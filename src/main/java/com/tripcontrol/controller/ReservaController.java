package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.DadosReserva;
import com.tripcontrol.controller.dto.PacoteComVagas;
import com.tripcontrol.controller.dto.ResumoFinanceiro;
import com.tripcontrol.controller.dto.ResumoReserva;
import com.tripcontrol.model.Cancelamento;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.MotivoCancelamento;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.model.SituacaoPacote;
import com.tripcontrol.model.StatusReserva;
import com.tripcontrol.repository.ClienteRepository;
import com.tripcontrol.repository.PacoteRepository;
import com.tripcontrol.repository.ConflitoDeConcorrenciaException;
import com.tripcontrol.repository.ReservaRepository;
import com.tripcontrol.util.Conexoes;
import com.tripcontrol.util.Formatadores;
import com.tripcontrol.util.ValidadorCpf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Regras de aplicacao do UC03 (Registrar Reservas) e do UC07 (Cancelar Reservas).
 *
 * <p>A disponibilidade e sempre recalculada no momento da gravacao a partir das
 * reservas ativas (UC04), e nao de um contador guardado no pacote: assim duas
 * reservas concorrentes nunca ultrapassam a capacidade. Pela mesma razao, o
 * cancelamento devolve vagas ao pacote sem precisar escrever nada nele: basta a
 * reserva deixar de estar Ativa.</p>
 */
public class ReservaController {

    private final ReservaRepository reservaRepository;
    private final PacoteRepository pacoteRepository;
    private final ClienteRepository clienteRepository;
    private final CalculadoraFinanceira calculadora;
    private final PacoteController pacoteController;

    public ReservaController(ReservaRepository reservaRepository,
                             PacoteRepository pacoteRepository,
                             ClienteRepository clienteRepository,
                             CalculadoraFinanceira calculadora,
                             PacoteController pacoteController) {
        this.reservaRepository = reservaRepository;
        this.pacoteRepository = pacoteRepository;
        this.clienteRepository = clienteRepository;
        this.calculadora = calculadora;
        this.pacoteController = pacoteController;
    }

    /**
     * Registra a reserva (UC03, passos 4 a 6).
     *
     * @return SUCESSO com o resumo usado no comprovante; ERRO_VALIDACAO nas
     *         situacoes FA01 (vagas insuficientes) e FA02 (periodo incompativel);
     *         NAO_ENCONTRADO quando cliente ou pacote nao existem mais
     */
    public Resultado<ResumoReserva> registrar(DadosReserva dados) {
        List<ErroValidacao> erros = new ArrayList<>();

        if (dados.clienteId() == null) {
            erros.add(new ErroValidacao("cliente", "Selecione o cliente da reserva."));
        }
        if (dados.pacoteId() == null) {
            erros.add(new ErroValidacao("pacote", "Selecione o pacote de viagem."));
        }

        Optional<Integer> quantidade = Formatadores.lerInteiro(dados.quantidadeViajantes());
        if (quantidade.isEmpty()) {
            erros.add(new ErroValidacao("quantidadeViajantes",
                    "Informe a quantidade de viajantes como numero inteiro."));
        } else if (quantidade.get() <= 0) {
            erros.add(new ErroValidacao("quantidadeViajantes", "A quantidade de viajantes deve ser maior que zero."));
        }

        Optional<LocalDate> inicio = Formatadores.lerData(dados.dataInicio());
        Optional<LocalDate> fim = Formatadores.lerData(dados.dataFim());
        if (inicio.isEmpty()) {
            erros.add(new ErroValidacao("dataInicio", "Informe a data de inicio da viagem."));
        }
        if (fim.isEmpty()) {
            erros.add(new ErroValidacao("dataFim", "Informe a data de fim da viagem."));
        }
        if (inicio.isPresent() && fim.isPresent() && fim.get().isBefore(inicio.get())) {
            erros.add(new ErroValidacao("dataFim", "A data de fim deve ser posterior a data de inicio."));
        }

        if (!erros.isEmpty()) {
            return Resultado.erroValidacao(erros);
        }

        Optional<Cliente> cliente = clienteRepository.buscarPorId(dados.clienteId());
        if (cliente.isEmpty()) {
            return Resultado.naoEncontrado("Cliente nao localizado. Atualize a lista e tente novamente.");
        }
        Optional<Pacote> pacoteEncontrado = pacoteRepository.buscarPorId(dados.pacoteId());
        if (pacoteEncontrado.isEmpty()) {
            return Resultado.naoEncontrado("Pacote nao localizado. Atualize a lista e tente novamente.");
        }
        Pacote pacote = pacoteEncontrado.get();

        // FA02 - periodo fora da vigencia do pacote.
        if (!pacote.contemPeriodo(inicio.get(), fim.get())) {
            return Resultado.erroValidacao("dataInicio",
                    "Periodo incompativel com o pacote. Datas validas: "
                            + Formatadores.formatarData(pacote.getDataInicio()) + " a "
                            + Formatadores.formatarData(pacote.getDataFim()) + ".");
        }

        // Conferencia de vagas e gravacao acontecem na mesma transacao, com a linha
        // do pacote bloqueada: sem isso, duas reservas simultaneas poderiam ler as
        // mesmas vagas livres e juntas ultrapassar a capacidade.
        return Conexoes.emTransacao(() -> {
            pacoteRepository.bloquearParaAtualizacao(pacote.getId());
            Optional<Pacote> pacoteAtual = pacoteRepository.buscarPorId(pacote.getId());
            if (pacoteAtual.isEmpty()) {
                return Resultado.naoEncontrado("Pacote nao localizado. Atualize a lista e tente novamente.");
            }
            Pacote pacoteParaReserva = pacoteAtual.get();
            PacoteComVagas vagasAtuais = pacoteController.comVagas(pacoteParaReserva);
            if (vagasAtuais.situacao() == SituacaoPacote.ENCERRADO) {
                return Resultado.erroValidacao("pacote",
                        "Este pacote ja foi encerrado. Selecione outro pacote para a reserva.");
            }
            if (!pacoteParaReserva.contemPeriodo(inicio.get(), fim.get())) {
                return Resultado.erroValidacao("dataInicio",
                        "Periodo incompativel com o pacote. Datas validas: "
                                + Formatadores.formatarData(pacoteParaReserva.getDataInicio()) + " a "
                                + Formatadores.formatarData(pacoteParaReserva.getDataFim()) + ".");
            }

            // FA01 - vagas insuficientes (recalculadas agora, nao no carregamento da tela).
            int disponiveis = vagasAtuais.vagasDisponiveis();
            if (quantidade.get() > disponiveis) {
                return Resultado.erroValidacao("quantidadeViajantes",
                        "Vagas insuficientes para o pacote selecionado (Vagas disponiveis: "
                                + disponiveis + ").");
            }

            Reserva reserva = new Reserva(cliente.get().getId(), pacote.getId(), quantidade.get(),
                    inicio.get(), fim.get(), Formatadores.textoOuNulo(dados.observacoes()));
            reserva.setCodigo(reservaRepository.proximoCodigo());
            reserva.setValorTotal(calcularValorTotal(pacoteParaReserva, quantidade.get()));
            reservaRepository.salvar(reserva);

            ResumoReserva resumo = new ResumoReserva(reserva, cliente.get(), pacoteParaReserva);
            return Resultado.sucesso(resumo,
                    "Reserva confirmada.\n\nCódigo: " + reserva.getCodigo()
                            + "\nCliente: " + cliente.get().getNome()
                            + "\nPacote: " + pacoteParaReserva.getCodigo() + " - " + pacoteParaReserva.getDestino()
                            + "\nPeríodo: " + Formatadores.formatarData(inicio.get()) + " a "
                            + Formatadores.formatarData(fim.get())
                            + "\nViajantes: " + quantidade.get()
                            + "\nTotal: " + Formatadores.formatarMoeda(reserva.getValorTotal()));
        });
    }

    /** @return valor total da reserva: preco unitario do pacote x quantidade de viajantes. */
    public BigDecimal calcularValorTotal(Pacote pacote, int quantidadeViajantes) {
        BigDecimal preco = pacote.getPreco() == null ? BigDecimal.ZERO : pacote.getPreco();
        return preco.multiply(BigDecimal.valueOf(quantidadeViajantes));
    }

    public List<ResumoReserva> listar() {
        return reservaRepository.buscarTodos().stream()
                .sorted(Comparator.comparing(Reserva::getDataRegistro,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::montarResumo)
                .toList();
    }

    public Optional<ResumoReserva> buscarPorCodigo(String codigo) {
        return reservaRepository.buscarPorCodigo(codigo).map(this::montarResumo);
    }

    public List<ResumoReserva> buscarPorPacote(Long pacoteId) {
        return reservaRepository.buscarPorPacote(pacoteId).stream().map(this::montarResumo).toList();
    }

    private ResumoReserva montarResumo(Reserva reserva) {
        Cliente cliente = clienteRepository.buscarPorId(reserva.getClienteId()).orElse(null);
        Pacote pacote = pacoteRepository.buscarPorId(reserva.getPacoteId()).orElse(null);
        return new ResumoReserva(reserva, cliente, pacote);
    }

    // ------------------------------------------------------------------
    // UC07 - Cancelar Reservas
    // ------------------------------------------------------------------

    /**
     * Pesquisa reservas por codigo, CPF do cliente, nome do cliente e/ou codigo
     * do pacote (UC07, passos 2 e 3). Os criterios informados sao combinados.
     *
     * @return SUCESSO com as reservas encontradas; ERRO_VALIDACAO quando nenhum
     *         criterio foi informado; NAO_ENCONTRADO na situacao FA01
     */
    public Resultado<List<ResumoReserva>> buscar(String codigoReserva,
                                                 String cpfCliente,
                                                 String nomeCliente,
                                                 String codigoPacote) {
        String codigo = Formatadores.textoOuNulo(codigoReserva);
        String cpf = Formatadores.textoOuNulo(cpfCliente);
        String nome = Formatadores.textoOuNulo(nomeCliente);
        String pacote = Formatadores.textoOuNulo(codigoPacote);

        if (codigo == null && cpf == null && nome == null && pacote == null) {
            return Resultado.erroValidacao("criterios",
                    "Informe ao menos um criterio de pesquisa.");
        }

        List<Reserva> encontradas = new ArrayList<>(reservaRepository.buscarTodos());

        if (codigo != null) {
            encontradas.removeIf(reserva -> reserva.getCodigo() == null
                    || !reserva.getCodigo().equalsIgnoreCase(codigo));
        }
        if (cpf != null) {
            Long clienteId = clienteRepository.buscarPorCpf(ValidadorCpf.apenasDigitos(cpf))
                    .map(Cliente::getId)
                    .orElse(null);
            encontradas.removeIf(reserva -> clienteId == null
                    || !Objects.equals(reserva.getClienteId(), clienteId));
        }
        if (nome != null) {
            Set<Long> idsPorNome = clienteRepository.buscarPorNome(nome).stream()
                    .map(Cliente::getId)
                    .collect(Collectors.toSet());
            encontradas.removeIf(reserva -> !idsPorNome.contains(reserva.getClienteId()));
        }
        if (pacote != null) {
            Long pacoteId = pacoteRepository.buscarPorCodigo(pacote)
                    .map(Pacote::getId)
                    .orElse(null);
            encontradas.removeIf(reserva -> pacoteId == null
                    || !Objects.equals(reserva.getPacoteId(), pacoteId));
        }

        // FA01 - nenhuma reserva corresponde aos criterios.
        if (encontradas.isEmpty()) {
            return Resultado.naoEncontrado("Nenhuma reserva corresponde aos criterios informados.");
        }

        List<ResumoReserva> resumos = encontradas.stream()
                .sorted(Comparator.comparing(Reserva::getDataRegistro,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::montarResumo)
                .toList();

        return Resultado.sucesso(resumos, resumos.size() + " reserva(s) encontrada(s).");
    }

    /**
     * Total pago, saldo pendente e situacao financeira da reserva (UC07, passo 4).
     *
     * <p>Delega a {@link CalculadoraFinanceira}, a mesma usada pelo UC05, para que a
     * tela de cancelamento e a de pagamentos nunca mostrem numeros diferentes para a
     * mesma reserva. Com o UC05 implementado, a situacao {@code ATRASADA} tambem passa
     * a aparecer aqui, porque o calculo agora enxerga o vencimento das parcelas.</p>
     */
    public ResumoFinanceiro resumoFinanceiro(Reserva reserva) {
        return calculadora.resumoDe(reserva);
    }

    /** @return quantidade de vagas que voltam ao pacote se a reserva for cancelada (UC07, passo 7). */
    public int vagasQueSeraoDevolvidas(Reserva reserva) {
        return reserva.isAtiva() ? reserva.getQuantidadeViajantes() : 0;
    }

    /** Validacao compartilhada pelo formulario e pela gravacao do cancelamento (UC07, FA03). */
    public List<ErroValidacao> validarMotivoCancelamento(MotivoCancelamento motivo, String descricao) {
        if (motivo == null) {
            return List.of(new ErroValidacao("motivo", "Selecione o motivo do cancelamento."));
        }
        if (motivo.isExigeDescricao() && Formatadores.textoOuNulo(descricao) == null) {
            return List.of(new ErroValidacao("descricao",
                    "Descreva o motivo informado pelo passageiro quando a opcao for \"Outro\"."));
        }
        return List.of();
    }

    /**
     * Efetiva o cancelamento de uma reserva ativa (UC07, passos 8 a 10).
     *
     * <p>A operacao e atomica do ponto de vista do dominio: ou a reserva passa a
     * Cancelada com motivo, data, hora e responsavel registrados, ou nada muda.
     * As vagas voltam ao pacote automaticamente, porque a ocupacao e derivada das
     * reservas ativas, e o historico de pagamentos e preservado.</p>
     *
     * @param versaoConhecida versao da reserva exibida quando a tela a carregou
     * @return SUCESSO com o resumo da reserva cancelada; NAO_ENCONTRADO no FA01;
     *         OPERACAO_BLOQUEADA no FA02 (ja cancelada) e no FA06 (falha ao persistir);
     *         ERRO_VALIDACAO no FA03; CONFLITO_CONCORRENCIA no FA05
     */
    public Resultado<ResumoReserva> cancelar(Long reservaId,
                                             MotivoCancelamento motivo,
                                             String descricao,
                                             String usuarioResponsavel,
                                             long versaoConhecida) {
        // FA01 - reserva nao encontrada.
        Optional<Reserva> encontrada = reservaRepository.buscarPorId(reservaId);
        if (encontrada.isEmpty()) {
            return Resultado.naoEncontrado("Reserva nao localizada. Refaca a pesquisa e tente novamente.");
        }
        Reserva reserva = encontrada.get();

        // FA02 - reserva ja cancelada: bloqueia e mostra o cancelamento anterior.
        if (reserva.isCancelada()) {
            return Resultado.operacaoBloqueada(descreverCancelamentoExistente(reserva));
        }

        // FA03 - motivo ausente ou "Outro" sem a descricao obrigatoria.
        List<ErroValidacao> errosMotivo = validarMotivoCancelamento(motivo, descricao);
        if (!errosMotivo.isEmpty()) {
            return Resultado.erroValidacao(errosMotivo);
        }
        String descricaoLimpa = Formatadores.textoOuNulo(descricao);

        // FA05 - a reserva mudou depois que a tela a carregou.
        if (reserva.getVersao() != versaoConhecida) {
            return Resultado.conflitoConcorrencia(montarResumo(reserva),
                    "Esta reserva foi alterada por outro processo enquanto a tela estava aberta. "
                            + "Os dados foram recarregados: revise antes de cancelar.");
        }

        StatusReserva statusAnterior = reserva.getStatus();
        long versaoAnterior = reserva.getVersao();
        int vagasDevolvidas = reserva.getQuantidadeViajantes();

        try {
            // Transacao unica: situacao, motivo, data, hora e responsavel entram
            // juntos, e as vagas voltam ao pacote por consequencia (a ocupacao e
            // derivada das reservas ativas). O historico financeiro nao e tocado.
            Conexoes.emTransacao(() -> {
                reserva.cancelar(new Cancelamento(motivo, descricaoLimpa, usuarioResponsavel, vagasDevolvidas));
                return reservaRepository.atualizar(reserva);
            });
        } catch (ConflitoDeConcorrenciaException conflito) {
            // FA05 detectado pelo banco: outra instancia alterou a reserva entre a
            // leitura e a gravacao.
            desfazerCancelamentoEmMemoria(reserva, statusAnterior, versaoAnterior);
            Reserva atual = reservaRepository.buscarPorId(reservaId).orElse(reserva);
            return Resultado.conflitoConcorrencia(montarResumo(atual),
                    "Esta reserva foi alterada por outro processo. Os dados foram recarregados: "
                            + "revise antes de cancelar.");
        } catch (RuntimeException falha) {
            // FA06 - desfaz a alteracao parcial: a reserva volta ao estado anterior
            // e a disponibilidade do pacote permanece como estava.
            desfazerCancelamentoEmMemoria(reserva, statusAnterior, versaoAnterior);
            return Resultado.operacaoBloqueada(
                    "Nao foi possivel concluir o cancelamento da reserva " + reserva.getCodigo()
                            + ". Nenhuma alteracao foi aplicada. Tente novamente.");
        }

        Pacote pacote = pacoteRepository.buscarPorId(reserva.getPacoteId()).orElse(null);
        String identificacaoPacote = pacote == null ? "pacote" : "pacote " + pacote.getCodigo();

        return Resultado.sucesso(montarResumo(reserva),
                "Reserva " + reserva.getCodigo() + " cancelada. " + vagasDevolvidas
                        + " vaga(s) devolvida(s) ao " + identificacaoPacote + ".");
    }

    /** Devolve a reserva em memoria ao estado anterior a tentativa de cancelamento. */
    private void desfazerCancelamentoEmMemoria(Reserva reserva, StatusReserva statusAnterior, long versaoAnterior) {
        reserva.setStatus(statusAnterior);
        reserva.setCancelamento(null);
        reserva.setVersao(versaoAnterior);
    }

    /** Texto do FA02 com data, hora, motivo e responsavel do cancelamento ja registrado. */
    private String descreverCancelamentoExistente(Reserva reserva) {
        Cancelamento cancelamento = reserva.getCancelamento();
        if (cancelamento == null) {
            return "A reserva " + reserva.getCodigo() + " ja esta cancelada.";
        }
        StringBuilder texto = new StringBuilder("A reserva ")
                .append(reserva.getCodigo())
                .append(" ja foi cancelada em ")
                .append(Formatadores.formatarDataHora(cancelamento.getDataHora()))
                .append(" por ")
                .append(cancelamento.getUsuarioResponsavel() == null
                        ? "usuario nao identificado" : cancelamento.getUsuarioResponsavel())
                .append(". Motivo: ")
                .append(cancelamento.getMotivo() == null
                        ? "nao informado" : cancelamento.getMotivo().getDescricao());
        if (cancelamento.getDescricao() != null) {
            texto.append(" - ").append(cancelamento.getDescricao());
        }
        texto.append(".");
        return texto.toString();
    }
}
