package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.ComprovantePagamento;
import com.tripcontrol.controller.dto.DadosPagamento;
import com.tripcontrol.controller.dto.DadosParcelamento;
import com.tripcontrol.controller.dto.LinhaParcela;
import com.tripcontrol.controller.dto.ResumoFinanceiro;
import com.tripcontrol.controller.dto.ResumoReserva;
import com.tripcontrol.controller.dto.SituacaoPagamentos;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Pagamento;
import com.tripcontrol.model.Parcela;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.model.StatusParcela;
import com.tripcontrol.repository.ClienteRepository;
import com.tripcontrol.repository.ConflitoDeConcorrenciaException;
import com.tripcontrol.repository.PacoteRepository;
import com.tripcontrol.repository.PagamentoRepository;
import com.tripcontrol.repository.ParcelaRepository;
import com.tripcontrol.repository.ReservaRepository;
import com.tripcontrol.util.Conexoes;
import com.tripcontrol.util.Formatadores;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Regras de aplicacao do UC05 - Gerenciar Pagamentos.
 *
 * <p>Tres decisoes estruturais valem a leitura antes do codigo:</p>
 *
 * <ul>
 *   <li><strong>Nada financeiro e gravado como estado derivado.</strong> Total pago,
 *       saldo, status de parcela e situacao financeira saem sempre da
 *       {@link CalculadoraFinanceira}. Nesta fase a reserva mora no PostgreSQL e os
 *       pagamentos em memoria; se a situacao fosse gravada na reserva, um reinicio
 *       deixaria reservas "Quitadas" sem nenhum pagamento registrado.</li>
 *   <li><strong>Um pagamento quita exatamente uma parcela</strong> (decisao A da equipe).
 *       Por isso a tela nao pede data de vencimento: ela vem da parcela escolhida.</li>
 *   <li><strong>Pagamento e baixa da parcela sao uma operacao so.</strong> As duas
 *       gravacoes acontecem dentro de {@link Conexoes#emTransacao}, o mesmo envelope
 *       usado no cancelamento do UC07. Em memoria o bloco apenas executa; na Etapa 6,
 *       quando Pagamento e Parcela forem para o banco, ele ja vira transacao JDBC sem
 *       mudar uma linha daqui.</li>
 * </ul>
 */
public class PagamentoController {

    private final ReservaController reservaController;
    private final ReservaRepository reservaRepository;
    private final ParcelaRepository parcelaRepository;
    private final PagamentoRepository pagamentoRepository;
    private final ClienteRepository clienteRepository;
    private final PacoteRepository pacoteRepository;
    private final CalculadoraFinanceira calculadora;

    public PagamentoController(ReservaController reservaController,
                               ReservaRepository reservaRepository,
                               ParcelaRepository parcelaRepository,
                               PagamentoRepository pagamentoRepository,
                               ClienteRepository clienteRepository,
                               PacoteRepository pacoteRepository,
                               CalculadoraFinanceira calculadora) {
        this.reservaController = reservaController;
        this.reservaRepository = reservaRepository;
        this.parcelaRepository = parcelaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.clienteRepository = clienteRepository;
        this.pacoteRepository = pacoteRepository;
        this.calculadora = calculadora;
    }

    // ------------------------------------------------------------------
    // Pesquisa (UC05, passos 2 e 3)
    // ------------------------------------------------------------------

    /**
     * Pesquisa reservas por codigo, CPF, nome do cliente e/ou codigo do pacote.
     *
     * <p>Reaproveita a pesquisa ja escrita para o UC07: os criterios do passo 2 do
     * UC05 sao os mesmos, e duplicar a consulta so criaria duas versoes da mesma
     * regra para manter.</p>
     *
     * @return SUCESSO com as reservas encontradas; NAO_ENCONTRADO no FA01
     */
    public Resultado<List<ResumoReserva>> buscar(String codigoReserva,
                                                 String cpfCliente,
                                                 String nomeCliente,
                                                 String codigoPacote) {
        return reservaController.buscar(codigoReserva, cpfCliente, nomeCliente, codigoPacote);
    }

    /**
     * Monta o painel financeiro da reserva escolhida (UC05, passo 4).
     *
     * @return SUCESSO com a situacao completa; NAO_ENCONTRADO no FA01
     */
    public Resultado<SituacaoPagamentos> abrir(Long reservaId) {
        Optional<Reserva> encontrada = reservaRepository.buscarPorId(reservaId);
        if (encontrada.isEmpty()) {
            // FA01 - a reserva sumiu entre a pesquisa e a selecao.
            return Resultado.naoEncontrado("Reserva nao localizada. Refaca a pesquisa e tente novamente.");
        }
        return Resultado.sucesso(montarSituacao(encontrada.get()), null);
    }

    // ------------------------------------------------------------------
    // Definir parcelamento (decisao A da equipe)
    // ------------------------------------------------------------------

    /**
     * Gera o plano de parcelas de uma reserva que ainda nao possui nenhuma.
     *
     * <p>Parcelas de valor igual, com vencimentos mensais a partir do primeiro; a
     * diferenca de centavos do arredondamento vai para a ultima parcela, de modo que
     * a soma das parcelas seja exatamente o valor total da reserva.</p>
     *
     * @return SUCESSO com a situacao atualizada; NAO_ENCONTRADO no FA01;
     *         OPERACAO_BLOQUEADA quando a reserva esta cancelada (FA02) ou ja tem plano;
     *         ERRO_VALIDACAO quando quantidade ou vencimento sao invalidos
     */
    public Resultado<SituacaoPagamentos> definirParcelamento(Long reservaId, DadosParcelamento dados) {
        Optional<Reserva> encontrada = reservaRepository.buscarPorId(reservaId);
        if (encontrada.isEmpty()) {
            return Resultado.naoEncontrado("Reserva nao localizada. Refaca a pesquisa e tente novamente.");
        }
        Reserva reserva = encontrada.get();

        // FA02 - reserva cancelada nao recebe plano de parcelas nem pagamento.
        if (reserva.isCancelada()) {
            return Resultado.operacaoBloqueada("A reserva " + reserva.getCodigo()
                    + " esta cancelada. O historico financeiro fica disponivel apenas para consulta.");
        }

        // TODO: a equipe ainda nao definiu se um plano ja existente pode ser refeito
        //  (por exemplo, renegociar de 3 para 6 parcelas). Enquanto isso nao for
        //  decidido com o professor, redefinir o plano fica bloqueado, que e a opcao
        //  que nao destroi parcelas ja quitadas por engano.
        if (!parcelaRepository.buscarPorReserva(reservaId).isEmpty()) {
            return Resultado.operacaoBloqueada("A reserva " + reserva.getCodigo()
                    + " ja possui plano de parcelas definido.");
        }

        List<ErroValidacao> erros = new ArrayList<>();

        Optional<Integer> quantidade = Formatadores.lerInteiro(dados.quantidadeParcelas());
        if (quantidade.isEmpty()) {
            erros.add(new ErroValidacao("quantidadeParcelas",
                    "Informe a quantidade de parcelas como numero inteiro."));
        } else if (quantidade.get() < 1) {
            erros.add(new ErroValidacao("quantidadeParcelas",
                    "A quantidade de parcelas deve ser maior ou igual a 1."));
        }

        Optional<LocalDate> primeiroVencimento = Formatadores.lerData(dados.dataPrimeiroVencimento());
        if (primeiroVencimento.isEmpty()) {
            erros.add(new ErroValidacao("dataPrimeiroVencimento",
                    "Informe a data do primeiro vencimento no formato DD/MM/AAAA."));
        }

        BigDecimal valorTotal = reserva.getValorTotal() == null ? BigDecimal.ZERO : reserva.getValorTotal();
        if (quantidade.isPresent() && quantidade.get() >= 1
                && valorPorParcela(valorTotal, quantidade.get()).compareTo(new BigDecimal("0.01")) < 0) {
            erros.add(new ErroValidacao("quantidadeParcelas",
                    "Parcelas demais para o valor da reserva: cada parcela ficaria abaixo de um centavo."));
        }

        if (!erros.isEmpty()) {
            return Resultado.erroValidacao(erros);
        }

        List<Parcela> plano = gerarPlano(reservaId, valorTotal, quantidade.get(), primeiroVencimento.get());

        // Todas as parcelas entram juntas: um plano pela metade seria pior que plano nenhum.
        Conexoes.emTransacao(() -> {
            plano.forEach(parcelaRepository::salvar);
            return plano;
        });

        return Resultado.sucesso(montarSituacao(reserva),
                plano.size() + " parcela(s) geradas para a reserva " + reserva.getCodigo()
                        + ", a primeira vencendo em "
                        + Formatadores.formatarData(primeiroVencimento.get()) + ".");
    }

    /**
     * Monta o plano em memoria, sem gravar nada: parcelas iguais e vencimentos
     * mensais, com o residuo do arredondamento na ultima.
     */
    public List<Parcela> gerarPlano(Long reservaId, BigDecimal valorTotal, int quantidade,
                                    LocalDate primeiroVencimento) {
        BigDecimal valorBase = valorPorParcela(valorTotal, quantidade);
        List<Parcela> plano = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;

        for (int numero = 1; numero <= quantidade; numero++) {
            // A ultima parcela recebe o que sobrou, garantindo soma exata.
            BigDecimal valor = numero == quantidade ? valorTotal.subtract(acumulado) : valorBase;
            acumulado = acumulado.add(valor);
            plano.add(new Parcela(reservaId, numero, quantidade, valor,
                    primeiroVencimento.plusMonths(numero - 1L)));
        }
        return plano;
    }

    /** Valor das parcelas iguais: arredondado para baixo, para que a ultima absorva o residuo. */
    private static BigDecimal valorPorParcela(BigDecimal valorTotal, int quantidade) {
        return valorTotal.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.DOWN);
    }

    // ------------------------------------------------------------------
    // Registrar recebimento (UC05, passos 5 a 10)
    // ------------------------------------------------------------------

    /**
     * Registra um recebimento e da baixa na parcela correspondente.
     *
     * @param versaoFinanceiraConhecida valor de
     *        {@link SituacaoPagamentos#versaoFinanceira()} lido quando a tela carregou
     * @return SUCESSO com o comprovante do passo 10; NAO_ENCONTRADO no FA01;
     *         OPERACAO_BLOQUEADA no FA02 e quando a gravacao falha;
     *         ERRO_VALIDACAO no FA03; DUPLICIDADE no FA04;
     *         CONFLITO_CONCORRENCIA no FA05
     */
    public Resultado<ComprovantePagamento> registrarRecebimento(Long reservaId,
                                                                DadosPagamento dados,
                                                                long versaoFinanceiraConhecida) {
        // FA01 - reserva nao encontrada.
        Optional<Reserva> encontrada = reservaRepository.buscarPorId(reservaId);
        if (encontrada.isEmpty()) {
            return Resultado.naoEncontrado("Reserva nao localizada. Refaca a pesquisa e tente novamente.");
        }
        Reserva reserva = encontrada.get();

        // FA02 - reserva cancelada: historico visivel, pagamento bloqueado.
        if (reserva.isCancelada()) {
            return Resultado.operacaoBloqueada("A reserva " + reserva.getCodigo()
                    + " esta cancelada. O historico financeiro fica disponivel apenas para consulta.");
        }

        List<LinhaParcela> parcelas = calculadora.parcelasDe(reservaId);
        if (parcelas.isEmpty()) {
            return Resultado.operacaoBloqueada("A reserva " + reserva.getCodigo()
                    + " ainda nao tem plano de parcelas. Use \"Definir Parcelamento\" antes de "
                    + "registrar o recebimento.");
        }

        ResumoFinanceiro antes = calculadora.resumoDe(reserva);

        // FA03 - criticas de campo, todas reunidas para a tela destacar de uma vez.
        List<ErroValidacao> erros = validarCampos(dados, antes.saldoPendente(), parcelas);
        if (!erros.isEmpty()) {
            return Resultado.erroValidacao(erros);
        }

        int numeroParcela = Formatadores.lerInteiro(dados.numeroParcela()).orElseThrow();
        LinhaParcela linha = parcelas.stream()
                .filter(candidata -> candidata.parcela().getNumero() == numeroParcela)
                .findFirst()
                .orElseThrow();
        BigDecimal valorRecebido = Formatadores.lerValorMonetario(dados.valorRecebido()).orElseThrow();
        LocalDate dataRecebimento = Formatadores.lerData(dados.dataRecebimento()).orElseThrow();
        String identificador = Formatadores.textoOuNulo(dados.identificadorTransacao());

        // FA04 - parcela ja quitada ou identificador de transacao repetido na reserva.
        Optional<Resultado<ComprovantePagamento>> duplicidade =
                verificarDuplicidade(reserva, linha, identificador);
        if (duplicidade.isPresent()) {
            return duplicidade.get();
        }

        // Decisao A: o recebimento quita a parcela inteira, nunca um pedaco dela.
        if (valorRecebido.compareTo(linha.parcela().getValor()) != 0) {
            return Resultado.erroValidacao("valorRecebido",
                    "O valor deve ser igual ao da parcela " + linha.numeroFormatado() + ": "
                            + Formatadores.formatarMoeda(linha.parcela().getValor()) + ".");
        }

        // FA05 - outro pagamento entrou na reserva depois que a tela carregou.
        long versaoAtual = calculadora.versaoFinanceira(reservaId);
        if (versaoAtual != versaoFinanceiraConhecida) {
            return Resultado.conflitoConcorrencia(null,
                    "Outro pagamento foi registrado nesta reserva enquanto a tela estava aberta. "
                            + "Os valores foram recarregados: revise o saldo antes de confirmar.");
        }

        Pagamento pagamento = new Pagamento(reservaId, numeroParcela, valorRecebido,
                dataRecebimento, dados.formaPagamento());
        pagamento.setIdentificadorTransacao(identificador);
        pagamento.setObservacao(Formatadores.textoOuNulo(dados.observacao()));

        Parcela parcela = linha.parcela();
        StatusParcela statusAnterior = parcela.getStatus();

        try {
            // Gravacao unica: o pagamento so existe se a parcela tiver sido quitada,
            // e vice-versa. E o mesmo envelope transacional do UC07.
            Conexoes.emTransacao(() -> {
                pagamento.setNumeroRecibo(pagamentoRepository.proximoNumeroRecibo());
                pagamentoRepository.salvar(pagamento);
                parcela.quitar(dataRecebimento, pagamento.getId());
                return parcelaRepository.atualizar(parcela);
            });
        } catch (ConflitoDeConcorrenciaException conflito) {
            // FA05 detectado pelo proprio banco: o UPDATE exige que a parcela ainda
            // esteja em aberto, entao outra instancia quitou esta parcela entre a
            // leitura da tela e a gravacao.
            desfazerBaixaEmMemoria(parcela, statusAnterior);
            return Resultado.conflitoConcorrencia(null,
                    "Esta parcela foi quitada por outro processo enquanto a tela estava aberta. "
                            + "Os valores foram recarregados: revise o saldo antes de confirmar.");
        } catch (RuntimeException falha) {
            desfazerBaixaEmMemoria(parcela, statusAnterior);
            return Resultado.operacaoBloqueada("Nao foi possivel registrar o recebimento da reserva "
                    + reserva.getCodigo() + ". Nenhuma alteracao foi aplicada. Tente novamente.");
        }

        SituacaoPagamentos atualizada = montarSituacao(reserva);
        ComprovantePagamento comprovante = new ComprovantePagamento(pagamento,
                atualizada.financeiro().saldoPendente(),
                atualizada.financeiro().situacao(),
                atualizada);

        // Passo 10: recibo, valor registrado e saldo atualizado.
        return Resultado.sucesso(comprovante,
                "Recibo " + pagamento.getNumeroRecibo() + " - "
                        + Formatadores.formatarMoeda(valorRecebido) + " recebido na parcela "
                        + linha.numeroFormatado() + ". Saldo pendente: "
                        + Formatadores.formatarMoeda(atualizada.financeiro().saldoPendente())
                        + " (" + atualizada.financeiro().situacao().getDescricao() + ").");
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /** FA03 - cada inconsistencia do passo 7.1 vira um erro do campo correspondente. */
    private List<ErroValidacao> validarCampos(DadosPagamento dados,
                                              BigDecimal saldoPendente,
                                              List<LinhaParcela> parcelas) {
        List<ErroValidacao> erros = new ArrayList<>();

        Optional<BigDecimal> valor = Formatadores.lerValorMonetario(dados.valorRecebido());
        if (valor.isEmpty()) {
            erros.add(new ErroValidacao("valorRecebido", "Informe o valor recebido como numero."));
        } else if (valor.get().compareTo(BigDecimal.ZERO) <= 0) {
            erros.add(new ErroValidacao("valorRecebido", "O valor recebido deve ser maior que zero."));
        } else if (valor.get().compareTo(saldoPendente) > 0) {
            erros.add(new ErroValidacao("valorRecebido",
                    "Valor acima do saldo pendente (" + Formatadores.formatarMoeda(saldoPendente) + ")."));
        }

        Optional<LocalDate> dataRecebimento = Formatadores.lerData(dados.dataRecebimento());
        if (dataRecebimento.isEmpty()) {
            erros.add(new ErroValidacao("dataRecebimento",
                    "Informe a data do recebimento no formato DD/MM/AAAA."));
        } else if (dataRecebimento.get().isAfter(calculadora.hoje())) {
            erros.add(new ErroValidacao("dataRecebimento",
                    "A data do recebimento nao pode ser futura."));
        }

        Optional<Integer> numeroParcela = Formatadores.lerInteiro(dados.numeroParcela());
        if (numeroParcela.isEmpty()) {
            erros.add(new ErroValidacao("numeroParcela", "Informe o numero da parcela."));
        } else if (numeroParcela.get() < 1) {
            erros.add(new ErroValidacao("numeroParcela", "O numero da parcela deve ser maior ou igual a 1."));
        } else if (parcelas.stream().noneMatch(linha -> linha.parcela().getNumero() == numeroParcela.get())) {
            erros.add(new ErroValidacao("numeroParcela",
                    "A reserva nao possui a parcela " + numeroParcela.get()
                            + ". Parcelas disponiveis: 1 a " + parcelas.size() + "."));
        }

        if (dados.formaPagamento() == null) {
            erros.add(new ErroValidacao("formaPagamento", "Selecione a forma de pagamento."));
        }

        return erros;
    }

    /**
     * FA04 - duplicidade dentro da mesma reserva: parcela ja quitada ou identificador
     * de transacao ja usado. O texto diz qual registro causou a duplicidade, como pede
     * o passo 7.2.
     */
    private Optional<Resultado<ComprovantePagamento>> verificarDuplicidade(Reserva reserva,
                                                                          LinhaParcela linha,
                                                                          String identificador) {
        if (linha.isQuitada()) {
            Pagamento anterior = linha.pagamento();
            String detalhe = anterior == null ? ""
                    : " pelo recibo " + anterior.getNumeroRecibo() + " em "
                            + Formatadores.formatarData(anterior.getDataRecebimento());
            return Optional.of(Resultado.duplicidade(null,
                    "A parcela " + linha.numeroFormatado() + " da reserva " + reserva.getCodigo()
                            + " ja foi quitada" + detalhe + ". Escolha outra parcela."));
        }

        if (identificador != null) {
            Optional<Pagamento> repetido = pagamentoRepository.buscarPorReserva(reserva.getId()).stream()
                    .filter(pagamento -> identificador.equalsIgnoreCase(pagamento.getIdentificadorTransacao()))
                    .findFirst();
            if (repetido.isPresent()) {
                return Optional.of(Resultado.duplicidade(null,
                        "O identificador \"" + identificador + "\" ja foi usado no recibo "
                                + repetido.get().getNumeroRecibo() + " desta reserva. "
                                + "Corrija o identificador da transacao."));
            }
        }
        return Optional.empty();
    }

    /** Devolve a parcela em memoria ao estado anterior a tentativa de baixa. */
    private void desfazerBaixaEmMemoria(Parcela parcela, StatusParcela statusAnterior) {
        parcela.setStatus(statusAnterior);
        parcela.setDataPagamento(null);
        parcela.setPagamentoId(null);
    }

    private SituacaoPagamentos montarSituacao(Reserva reserva) {
        Cliente cliente = clienteRepository.buscarPorId(reserva.getClienteId()).orElse(null);
        Pacote pacote = pacoteRepository.buscarPorId(reserva.getPacoteId()).orElse(null);
        return new SituacaoPagamentos(
                new ResumoReserva(reserva, cliente, pacote),
                calculadora.resumoDe(reserva),
                calculadora.parcelasDe(reserva.getId()),
                calculadora.versaoFinanceira(reserva.getId()));
    }
}
