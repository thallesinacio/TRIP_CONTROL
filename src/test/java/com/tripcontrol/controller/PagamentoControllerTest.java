package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.ComprovantePagamento;
import com.tripcontrol.controller.dto.DadosCliente;
import com.tripcontrol.controller.dto.DadosPacote;
import com.tripcontrol.controller.dto.DadosPagamento;
import com.tripcontrol.controller.dto.DadosParcelamento;
import com.tripcontrol.controller.dto.DadosReserva;
import com.tripcontrol.controller.dto.LinhaParcela;
import com.tripcontrol.controller.dto.SituacaoPagamentos;
import com.tripcontrol.model.Cancelamento;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.FormaPagamento;
import com.tripcontrol.model.MotivoCancelamento;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Parcela;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.model.SituacaoFinanceira;
import com.tripcontrol.model.StatusParcela;
import com.tripcontrol.repository.ClienteRepository;
import com.tripcontrol.repository.PacoteRepository;
import com.tripcontrol.repository.PagamentoRepository;
import com.tripcontrol.repository.ParcelaRepository;
import com.tripcontrol.repository.ReservaRepository;
import com.tripcontrol.repository.memory.InMemoryClienteRepository;
import com.tripcontrol.repository.memory.InMemoryPacoteRepository;
import com.tripcontrol.repository.memory.InMemoryPagamentoRepository;
import com.tripcontrol.repository.memory.InMemoryParcelaRepository;
import com.tripcontrol.repository.memory.InMemoryReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre o fluxo principal e os fluxos alternativos do UC05 (Gerenciar Pagamentos),
 * mais a geracao do plano de parcelas (decisao A) e a precedencia da situacao
 * financeira (decisao C).
 *
 * <p>O FA06 aparece aqui na forma que chega ao Controller: o botao "Cancelar" nao
 * grava nada, ele apenas recarrega o painel, entao o teste verifica que recarregar
 * depois de uma tentativa descartada devolve exatamente o estado anterior.</p>
 */
class PagamentoControllerTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 12);
    private static final Clock RELOGIO =
            Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneId.of("UTC"));

    private ReservaRepository reservaRepository;
    private PacoteRepository pacoteRepository;
    private ClienteRepository clienteRepository;
    private ParcelaRepository parcelaRepository;
    private PagamentoRepository pagamentoRepository;
    private CalculadoraFinanceira calculadora;
    private ReservaController reservaController;
    private PagamentoController controller;

    private Pacote pacote;
    private Cliente cliente;
    private Reserva reserva;

    @BeforeEach
    void prepararCenario() {
        reservaRepository = new InMemoryReservaRepository();
        pacoteRepository = new InMemoryPacoteRepository();
        clienteRepository = new InMemoryClienteRepository();
        parcelaRepository = new InMemoryParcelaRepository();
        pagamentoRepository = new InMemoryPagamentoRepository();
        montarControllers(parcelaRepository);

        pacote = pacoteController().cadastrar(new DadosPacote("Gramado & Canela, RS",
                "05/05/2026", "12/05/2026", "Roteiro cultural",
                "3490,00", "10", null)).getDado().orElseThrow();

        cliente = new ClienteController(clienteRepository, reservaRepository, pacoteRepository)
                .cadastrar(new DadosCliente("Carlos Eduardo de Souza", "529.982.247-25",
                        "(87) 99999-0000", null, null, List.of())).getDado().orElseThrow();

        reserva = registrarReserva(2);
    }

    private PacoteController pacoteController() {
        return new PacoteController(pacoteRepository, reservaRepository, RELOGIO);
    }

    private void montarControllers(ParcelaRepository repositorioDeParcelas) {
        this.parcelaRepository = repositorioDeParcelas;
        calculadora = new CalculadoraFinanceira(repositorioDeParcelas, pagamentoRepository, RELOGIO);
        reservaController = new ReservaController(reservaRepository, pacoteRepository,
                clienteRepository, calculadora, pacoteController());
        controller = new PagamentoController(reservaController, reservaRepository,
                repositorioDeParcelas, pagamentoRepository, clienteRepository, pacoteRepository,
                calculadora);
    }

    private Reserva registrarReserva(int viajantes) {
        return reservaController.registrar(new DadosReserva(cliente.getId(), pacote.getId(),
                        String.valueOf(viajantes), "05/05/2026", "12/05/2026", null))
                .getDado().orElseThrow().reserva();
    }

    /** Plano padrao dos testes: 2 parcelas de R$ 3.490,00, a primeira vencida. */
    private SituacaoPagamentos definirPlanoDeDuasParcelas() {
        return controller.definirParcelamento(reserva.getId(),
                new DadosParcelamento("2", "10/03/2026")).getDado().orElseThrow();
    }

    private DadosPagamento recebimento(String valor, String numeroParcela, String identificador) {
        return new DadosPagamento(valor, "10/03/2026", FormaPagamento.PIX,
                numeroParcela, identificador, null);
    }

    private long versaoAtual() {
        return calculadora.versaoFinanceira(reserva.getId());
    }

    // ------------------------------------------------------------------
    // Decisao A - plano de parcelas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Definir Parcelamento gera parcelas iguais com vencimentos mensais")
    void geraPlanoDeParcelas() {
        SituacaoPagamentos situacao = definirPlanoDeDuasParcelas();

        assertEquals(2, situacao.parcelas().size());
        Parcela primeira = situacao.parcelas().get(0).parcela();
        Parcela segunda = situacao.parcelas().get(1).parcela();

        assertEquals(0, new BigDecimal("3490.00").compareTo(primeira.getValor()));
        assertEquals(0, new BigDecimal("3490.00").compareTo(segunda.getValor()));
        assertEquals(LocalDate.of(2026, 3, 10), primeira.getDataVencimento());
        assertEquals(LocalDate.of(2026, 4, 10), segunda.getDataVencimento());
        assertEquals(2, primeira.getTotalParcelas());
    }

    @Test
    @DisplayName("O residuo do arredondamento vai para a ultima parcela e a soma fecha o total")
    void residuoDoArredondamentoVaiParaUltimaParcela() {
        // R$ 6.980,00 em 3 parcelas nao divide exato: 2326,66 + 2326,66 + 2326,68
        List<Parcela> plano = controller.gerarPlano(reserva.getId(),
                new BigDecimal("6980.00"), 3, LocalDate.of(2026, 3, 10));

        assertEquals(0, new BigDecimal("2326.66").compareTo(plano.get(0).getValor()));
        assertEquals(0, new BigDecimal("2326.66").compareTo(plano.get(1).getValor()));
        assertEquals(0, new BigDecimal("2326.68").compareTo(plano.get(2).getValor()));

        BigDecimal soma = plano.stream().map(Parcela::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("6980.00").compareTo(soma),
                "a soma das parcelas deve ser exatamente o valor da reserva");
    }

    @Test
    @DisplayName("Quantidade de parcelas e data do primeiro vencimento sao obrigatorias")
    void recusaPlanoComDadosInvalidos() {
        Resultado<SituacaoPagamentos> resultado = controller.definirParcelamento(reserva.getId(),
                new DadosParcelamento("zero", "31/02/2026"));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("quantidadeParcelas").isPresent());
        assertTrue(resultado.mensagemDoCampo("dataPrimeiroVencimento").isPresent());
        assertTrue(parcelaRepository.buscarPorReserva(reserva.getId()).isEmpty());
    }

    @Test
    @DisplayName("Reserva que ja tem plano nao aceita um segundo parcelamento")
    void naoRedefineplanoExistente() {
        definirPlanoDeDuasParcelas();

        Resultado<SituacaoPagamentos> resultado = controller.definirParcelamento(reserva.getId(),
                new DadosParcelamento("4", "10/03/2026"));

        assertEquals(StatusResultado.OPERACAO_BLOQUEADA, resultado.getStatus());
        assertEquals(2, parcelaRepository.buscarPorReserva(reserva.getId()).size());
    }

    // ------------------------------------------------------------------
    // Fluxo principal e criterio de pronto
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Fluxo principal: Pendente vira Parcialmente Paga e depois Quitada")
    void percorreAsTresSituacoesFinanceiras() {
        SituacaoPagamentos inicial = definirPlanoDeDuasParcelas();
        // Vencimento 10/03 com "hoje" em 12/03: a primeira parcela ja nasce atrasada.
        assertEquals(SituacaoFinanceira.ATRASADA, inicial.financeiro().situacao());

        Resultado<ComprovantePagamento> primeiro = controller.registrarRecebimento(reserva.getId(),
                recebimento("3490,00", "1", "PIX-001"), versaoAtual());

        assertTrue(primeiro.isSucesso());
        ComprovantePagamento comprovante = primeiro.getDado().orElseThrow();
        assertNotNull(comprovante.numeroRecibo());
        assertEquals(SituacaoFinanceira.PARCIALMENTE_PAGA, comprovante.situacao());
        assertEquals(0, new BigDecimal("3490.00").compareTo(comprovante.saldoPendente()));

        Resultado<ComprovantePagamento> segundo = controller.registrarRecebimento(reserva.getId(),
                new DadosPagamento("3490,00", "10/03/2026", FormaPagamento.DINHEIRO,
                        "2", "DIN-002", null),
                versaoAtual());

        assertTrue(segundo.isSucesso());
        assertEquals(SituacaoFinanceira.QUITADA, segundo.getDado().orElseThrow().situacao());
        assertEquals(0, BigDecimal.ZERO.compareTo(segundo.getDado().orElseThrow().saldoPendente()));

        SituacaoPagamentos finalDaReserva = controller.abrir(reserva.getId()).getDado().orElseThrow();
        assertTrue(finalDaReserva.parcelas().stream().allMatch(LinhaParcela::isQuitada));
    }

    @Test
    @DisplayName("O recebimento grava recibo, forma, identificador e da baixa na parcela")
    void gravaPagamentoEBaixaDaParcelaJuntos() {
        definirPlanoDeDuasParcelas();

        controller.registrarRecebimento(reserva.getId(),
                recebimento("3490,00", "1", "PIX-001"), versaoAtual());

        LinhaParcela linha = controller.abrir(reserva.getId()).getDado().orElseThrow()
                .parcelas().get(0);
        assertEquals(StatusParcela.QUITADA, linha.status());
        assertEquals(LocalDate.of(2026, 3, 10), linha.parcela().getDataPagamento());
        assertNotNull(linha.parcela().getPagamentoId());
        assertEquals("PIX-001", linha.identificadorFormatado());
        assertEquals(FormaPagamento.PIX.getDescricao(), linha.formaFormatada());
        assertTrue(linha.reciboFormatado().startsWith("RC-"));
    }

    @Test
    @DisplayName("Numeros de recibo sao sequenciais e nao se repetem")
    void numeroDeReciboESequencial() {
        definirPlanoDeDuasParcelas();

        String primeiro = controller.registrarRecebimento(reserva.getId(),
                        recebimento("3490,00", "1", "PIX-001"), versaoAtual())
                .getDado().orElseThrow().numeroRecibo();
        String segundo = controller.registrarRecebimento(reserva.getId(),
                        recebimento("3490,00", "2", "PIX-002"), versaoAtual())
                .getDado().orElseThrow().numeroRecibo();

        assertEquals("RC-000001", primeiro);
        assertEquals("RC-000002", segundo);
    }

    // ------------------------------------------------------------------
    // Decisao C - precedencia da situacao financeira
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Precedencia: Quitada > Atrasada > Parcialmente Paga > Pendente")
    void precedenciaDaSituacaoFinanceira() {
        BigDecimal total = new BigDecimal("1000.00");

        // Quitada vence ate a existencia de parcela vencida.
        assertEquals(SituacaoFinanceira.QUITADA,
                SituacaoFinanceira.calcular(total, new BigDecimal("1000.00"), true));
        // Atrasada vence Parcialmente Paga quando ha pagamento parcial e parcela vencida.
        assertEquals(SituacaoFinanceira.ATRASADA,
                SituacaoFinanceira.calcular(total, new BigDecimal("400.00"), true));
        // Atrasada vence Pendente quando nao houve nenhum pagamento.
        assertEquals(SituacaoFinanceira.ATRASADA,
                SituacaoFinanceira.calcular(total, BigDecimal.ZERO, true));
        // Sem parcela vencida, o que decide e ter havido ou nao pagamento.
        assertEquals(SituacaoFinanceira.PARCIALMENTE_PAGA,
                SituacaoFinanceira.calcular(total, new BigDecimal("400.00"), false));
        assertEquals(SituacaoFinanceira.PENDENTE,
                SituacaoFinanceira.calcular(total, BigDecimal.ZERO, false));
    }

    @Test
    @DisplayName("Parcela a vencer mantem a reserva Pendente; apos o vencimento vira Atrasada")
    void situacaoAtrasadaDependeDoVencimento() {
        controller.definirParcelamento(reserva.getId(),
                new DadosParcelamento("2", "10/06/2026"));
        assertEquals(SituacaoFinanceira.PENDENTE,
                controller.abrir(reserva.getId()).getDado().orElseThrow().financeiro().situacao());

        // mesma reserva, plano vencido: o unico dado que muda e o vencimento
        parcelaRepository.removerPorReserva(reserva.getId());
        controller.definirParcelamento(reserva.getId(),
                new DadosParcelamento("2", "10/01/2026"));
        assertEquals(SituacaoFinanceira.ATRASADA,
                controller.abrir(reserva.getId()).getDado().orElseThrow().financeiro().situacao());
    }

    // ------------------------------------------------------------------
    // Fluxos alternativos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("FA01: reserva inexistente nao abre o painel nem aceita pagamento")
    void fa01ReservaNaoEncontrada() {
        assertEquals(StatusResultado.NAO_ENCONTRADO, controller.abrir(9999L).getStatus());
        assertEquals(StatusResultado.NAO_ENCONTRADO,
                controller.registrarRecebimento(9999L, recebimento("100,00", "1", null), 0L).getStatus());
        assertEquals(StatusResultado.NAO_ENCONTRADO,
                controller.buscar("RE-0000", null, null, null).getStatus());
    }

    @Test
    @DisplayName("FA02: reserva cancelada bloqueia pagamento e mantem o historico visivel")
    void fa02ReservaCancelada() {
        definirPlanoDeDuasParcelas();
        controller.registrarRecebimento(reserva.getId(),
                recebimento("3490,00", "1", "PIX-001"), versaoAtual());

        reserva.cancelar(new Cancelamento(MotivoCancelamento.DESISTENCIA_CLIENTE,
                null, "Ana Silva", reserva.getQuantidadeViajantes()));
        reservaRepository.atualizar(reserva);

        Resultado<ComprovantePagamento> resultado = controller.registrarRecebimento(reserva.getId(),
                recebimento("3490,00", "2", "PIX-002"), versaoAtual());

        assertEquals(StatusResultado.OPERACAO_BLOQUEADA, resultado.getStatus());
        assertEquals(StatusResultado.OPERACAO_BLOQUEADA, controller.definirParcelamento(
                reserva.getId(), new DadosParcelamento("3", "10/03/2026")).getStatus());

        // 4.2 - o historico continua disponivel para consulta
        SituacaoPagamentos situacao = controller.abrir(reserva.getId()).getDado().orElseThrow();
        assertTrue(situacao.isBloqueadaParaPagamento());
        assertEquals(1, pagamentoRepository.buscarPorReserva(reserva.getId()).size());
        assertEquals(0, new BigDecimal("3490.00").compareTo(situacao.financeiro().totalPago()));
    }

    @Test
    @DisplayName("FA03: cada dado invalido e apontado no seu proprio campo")
    void fa03DadosInvalidos() {
        definirPlanoDeDuasParcelas();

        Resultado<ComprovantePagamento> resultado = controller.registrarRecebimento(reserva.getId(),
                new DadosPagamento("abc", "20/03/2026", null, "0", null, null), versaoAtual());

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("valorRecebido").isPresent());
        assertTrue(resultado.mensagemDoCampo("dataRecebimento").orElseThrow().contains("futura"));
        assertTrue(resultado.mensagemDoCampo("numeroParcela").isPresent());
        assertTrue(resultado.mensagemDoCampo("formaPagamento").isPresent());
        assertTrue(pagamentoRepository.buscarPorReserva(reserva.getId()).isEmpty());
    }

    @Test
    @DisplayName("FA03: valor acima do saldo pendente e valor zero sao recusados")
    void fa03ValorForaDosLimites() {
        definirPlanoDeDuasParcelas();

        assertTrue(controller.registrarRecebimento(reserva.getId(),
                        recebimento("99999,00", "1", null), versaoAtual())
                .mensagemDoCampo("valorRecebido").orElseThrow().contains("saldo pendente"));

        assertTrue(controller.registrarRecebimento(reserva.getId(),
                        recebimento("0,00", "1", null), versaoAtual())
                .mensagemDoCampo("valorRecebido").orElseThrow().contains("maior que zero"));
    }

    @Test
    @DisplayName("FA03: parcela inexistente na reserva e recusada com as parcelas validas")
    void fa03ParcelaInexistente() {
        definirPlanoDeDuasParcelas();

        Resultado<ComprovantePagamento> resultado = controller.registrarRecebimento(reserva.getId(),
                recebimento("3490,00", "7", null), versaoAtual());

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("numeroParcela").orElseThrow().contains("1 a 2"));
    }

    @Test
    @DisplayName("Decisao A: o valor recebido tem de ser exatamente o valor da parcela")
    void recusaValorDiferenteDoValorDaParcela() {
        definirPlanoDeDuasParcelas();

        Resultado<ComprovantePagamento> resultado = controller.registrarRecebimento(reserva.getId(),
                recebimento("1000,00", "1", null), versaoAtual());

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("valorRecebido").orElseThrow().contains("igual ao da parcela"));
    }

    @Test
    @DisplayName("FA04: pagar a mesma parcela duas vezes e recusado")
    void fa04ParcelaDuplicada() {
        definirPlanoDeDuasParcelas();
        controller.registrarRecebimento(reserva.getId(),
                recebimento("3490,00", "1", "PIX-001"), versaoAtual());

        Resultado<ComprovantePagamento> resultado = controller.registrarRecebimento(reserva.getId(),
                recebimento("3490,00", "1", "PIX-999"), versaoAtual());

        assertEquals(StatusResultado.DUPLICIDADE, resultado.getStatus());
        assertTrue(resultado.getMensagem().contains("ja foi quitada"));
        assertEquals(1, pagamentoRepository.buscarPorReserva(reserva.getId()).size());
    }

    @Test
    @DisplayName("FA04: identificador de transacao repetido na reserva e recusado")
    void fa04IdentificadorDuplicado() {
        definirPlanoDeDuasParcelas();
        controller.registrarRecebimento(reserva.getId(),
                recebimento("3490,00", "1", "PIX-001"), versaoAtual());

        Resultado<ComprovantePagamento> resultado = controller.registrarRecebimento(reserva.getId(),
                recebimento("3490,00", "2", "PIX-001"), versaoAtual());

        assertEquals(StatusResultado.DUPLICIDADE, resultado.getStatus());
        assertTrue(resultado.getMensagem().contains("PIX-001"));
        assertEquals(1, pagamentoRepository.buscarPorReserva(reserva.getId()).size());
    }

    @Test
    @DisplayName("FA05: pagamento registrado por outro processo interrompe a gravacao")
    void fa05SaldoAlteradoPorOutroProcesso() {
        definirPlanoDeDuasParcelas();
        long versaoNaAberturaDaTela = versaoAtual();

        // outro processo registra um pagamento depois que a tela carregou
        controller.registrarRecebimento(reserva.getId(),
                recebimento("3490,00", "1", "PIX-001"), versaoNaAberturaDaTela);

        Resultado<ComprovantePagamento> resultado = controller.registrarRecebimento(reserva.getId(),
                recebimento("3490,00", "2", "PIX-002"), versaoNaAberturaDaTela);

        assertEquals(StatusResultado.CONFLITO_CONCORRENCIA, resultado.getStatus());
        assertEquals(1, pagamentoRepository.buscarPorReserva(reserva.getId()).size());
        assertFalse(controller.abrir(reserva.getId()).getDado().orElseThrow()
                .parcelas().get(1).isQuitada(), "a segunda parcela nao pode ter sido baixada");
    }

    @Test
    @DisplayName("FA06: descartar o registro recarrega o painel sem tocar no historico")
    void fa06CancelamentoDoRegistro() {
        definirPlanoDeDuasParcelas();
        controller.registrarRecebimento(reserva.getId(),
                recebimento("3490,00", "1", "PIX-001"), versaoAtual());
        SituacaoPagamentos antes = controller.abrir(reserva.getId()).getDado().orElseThrow();

        // O botao Cancelar nao chama o Controller: ele descarta o formulario e volta
        // ao passo 4, que e exatamente este recarregamento.
        SituacaoPagamentos depois = controller.abrir(reserva.getId()).getDado().orElseThrow();

        assertEquals(antes.versaoFinanceira(), depois.versaoFinanceira());
        assertEquals(0, antes.financeiro().totalPago().compareTo(depois.financeiro().totalPago()));
        assertEquals(antes.financeiro().situacao(), depois.financeiro().situacao());
        assertEquals(1, pagamentoRepository.buscarPorReserva(reserva.getId()).size());
    }

    @Test
    @DisplayName("Falha ao persistir a baixa desfaz a alteracao parcial da parcela")
    void desfazBaixaQuandoPersistenciaFalha() {
        // repositorio que aceita a criacao do plano e falha na baixa da parcela
        ParcelaRepository repositorioComFalha = new InMemoryParcelaRepository() {
            @Override
            public synchronized Parcela atualizar(Parcela entidade) {
                throw new IllegalStateException("falha simulada de persistencia");
            }
        };
        montarControllers(repositorioComFalha);
        definirPlanoDeDuasParcelas();

        Resultado<ComprovantePagamento> resultado = controller.registrarRecebimento(reserva.getId(),
                recebimento("3490,00", "1", "PIX-001"), versaoAtual());

        assertEquals(StatusResultado.OPERACAO_BLOQUEADA, resultado.getStatus());
        Parcela parcela = repositorioComFalha.buscarPorReservaENumero(reserva.getId(), 1).orElseThrow();
        assertNull(parcela.getDataPagamento(), "nenhuma baixa parcial pode ficar registrada");
        assertEquals(StatusParcela.PENDENTE, parcela.getStatus());
    }

    @Test
    @DisplayName("Sem plano de parcelas definido o recebimento e bloqueado")
    void exigePlanoDeParcelasAntesDoRecebimento() {
        Resultado<ComprovantePagamento> resultado = controller.registrarRecebimento(reserva.getId(),
                recebimento("3490,00", "1", null), 0L);

        assertEquals(StatusResultado.OPERACAO_BLOQUEADA, resultado.getStatus());
        assertTrue(resultado.getMensagem().contains("Definir Parcelamento"));
    }
}
