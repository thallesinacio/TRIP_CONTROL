package com.tripcontrol.repository.jdbc;

import com.tripcontrol.controller.CalculadoraFinanceira;
import com.tripcontrol.controller.PacoteController;
import com.tripcontrol.controller.PagamentoController;
import com.tripcontrol.controller.ReservaController;
import com.tripcontrol.controller.Resultado;
import com.tripcontrol.controller.StatusResultado;
import com.tripcontrol.controller.dto.ComprovantePagamento;
import com.tripcontrol.controller.dto.DadosPagamento;
import com.tripcontrol.controller.dto.DadosParcelamento;
import com.tripcontrol.controller.dto.SituacaoPagamentos;
import com.tripcontrol.model.Cancelamento;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.FormaPagamento;
import com.tripcontrol.model.MotivoCancelamento;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.model.SituacaoFinanceira;
import com.tripcontrol.util.Formatadores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC05 de ponta a ponta sobre o PostgreSQL: o Controller nao mudou desde a
 * Etapa 4, so a implementacao de repositorio por baixo dele.
 *
 * <p>Vive no pacote dos repositorios JDBC porque reaproveita a infraestrutura de
 * {@link RepositorioJdbcIT} (banco de teste + transacao desfeita a cada teste).</p>
 */
class FluxoPagamentoJdbcIT extends RepositorioJdbcIT {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 12);
    private static final Clock RELOGIO =
            Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneId.of("UTC"));

    private final JdbcPacoteRepository pacotes = new JdbcPacoteRepository();
    private final JdbcClienteRepository clientes = new JdbcClienteRepository();
    private final JdbcReservaRepository reservas = new JdbcReservaRepository();
    private final JdbcParcelaRepository parcelas = new JdbcParcelaRepository();
    private final JdbcPagamentoRepository pagamentos = new JdbcPagamentoRepository();

    private PagamentoController controller;
    private Reserva reserva;

    @BeforeEach
    void prepararReserva() {
        controller = montarController();

        Pacote pacote = new Pacote("Gramado " + System.nanoTime(),
                LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 12),
                "Roteiro cultural", new BigDecimal("3490.00"), 10, null);
        pacote.setCodigo(pacotes.proximoCodigo());
        pacotes.salvar(pacote);

        Cliente cliente = clientes.salvar(new Cliente("Carlos Souza", "52998224725", "87999990000"));

        Reserva nova = new Reserva(cliente.getId(), pacote.getId(), 2,
                LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 12), null);
        nova.setCodigo(reservas.proximoCodigo());
        nova.setValorTotal(new BigDecimal("6980.00"));
        reserva = reservas.salvar(nova);
    }

    /** Monta a pilha inteira do UC05 sobre os repositorios JDBC. */
    private PagamentoController montarController() {
        CalculadoraFinanceira calculadora = new CalculadoraFinanceira(parcelas, pagamentos, RELOGIO);
        PacoteController pacoteController = new PacoteController(pacotes, reservas, RELOGIO);
        ReservaController reservaController = new ReservaController(reservas, pacotes, clientes,
                calculadora, pacoteController);
        return new PagamentoController(reservaController, reservas, parcelas, pagamentos,
                clientes, pacotes, calculadora);
    }

    private SituacaoPagamentos definirPlano() {
        return controller.definirParcelamento(reserva.getId(),
                new DadosParcelamento("2", "10/03/2026")).getDado().orElseThrow();
    }

    private Resultado<ComprovantePagamento> receber(String numeroParcela, String comprovante) {
        return controller.registrarRecebimento(reserva.getId(),
                new DadosPagamento("3490,00", "10/03/2026", FormaPagamento.PIX,
                        numeroParcela, comprovante, "Recebido no caixa"),
                controller.abrir(reserva.getId()).getDado().orElseThrow().versaoFinanceira());
    }

    @Test
    @DisplayName("Pendente vira Parcialmente Paga e depois Quitada, tudo gravado no banco")
    void percorreAsSituacoesFinanceiras() {
        SituacaoPagamentos plano = definirPlano();
        assertEquals(2, plano.parcelas().size());
        assertEquals(SituacaoFinanceira.ATRASADA, plano.financeiro().situacao(),
                "vencimento 10/03 com hoje em 12/03: a primeira parcela ja nasce atrasada");

        Resultado<ComprovantePagamento> primeiro = receber("1", "PIX-001");
        assertTrue(primeiro.isSucesso());
        assertEquals(SituacaoFinanceira.PARCIALMENTE_PAGA,
                primeiro.getDado().orElseThrow().situacao());
        assertTrue(primeiro.getDado().orElseThrow().numeroRecibo().startsWith("RC-"));

        Resultado<ComprovantePagamento> segundo = receber("2", "PIX-002");
        assertTrue(segundo.isSucesso());
        assertEquals(SituacaoFinanceira.QUITADA, segundo.getDado().orElseThrow().situacao());
        assertEquals(0, BigDecimal.ZERO.compareTo(segundo.getDado().orElseThrow().saldoPendente()));
    }

    @Test
    @DisplayName("Plano e pagamentos sobrevivem a uma releitura completa do banco")
    void dadosSobrevivemAReleitura() {
        definirPlano();
        receber("1", "PIX-003");

        // instancias novas de repositorio e controller: tudo vem do banco, nada de cache
        PagamentoController outraInstancia = montarController();
        SituacaoPagamentos situacao = outraInstancia.abrir(reserva.getId()).getDado().orElseThrow();

        assertEquals(2, situacao.parcelas().size());
        assertTrue(situacao.parcelas().get(0).isQuitada());
        assertFalse(situacao.parcelas().get(1).isQuitada());
        assertEquals(0, new BigDecimal("3490.00").compareTo(situacao.financeiro().totalPago()));
        assertEquals("PIX-003", situacao.parcelas().get(0).identificadorFormatado());
    }

    @Test
    @DisplayName("FA04: pagar a mesma parcela duas vezes e recusado")
    void fa04ParcelaDuplicada() {
        definirPlano();
        receber("1", "PIX-004");

        Resultado<ComprovantePagamento> repetido = receber("1", "PIX-005");

        assertEquals(StatusResultado.DUPLICIDADE, repetido.getStatus());
        assertEquals(1, pagamentos.buscarPorReserva(reserva.getId()).size());
    }

    @Test
    @DisplayName("FA04: comprovante repetido na mesma reserva e recusado")
    void fa04ComprovanteDuplicado() {
        definirPlano();
        receber("1", "PIX-006");

        Resultado<ComprovantePagamento> repetido = receber("2", "PIX-006");

        assertEquals(StatusResultado.DUPLICIDADE, repetido.getStatus());
        assertEquals(1, pagamentos.buscarPorReserva(reserva.getId()).size());
    }

    @Test
    @DisplayName("FA05: gravar sobre uma versao financeira antiga e recusado")
    void fa05SaldoAlteradoPorOutroProcesso() {
        definirPlano();
        long versaoNaAberturaDaTela = controller.abrir(reserva.getId())
                .getDado().orElseThrow().versaoFinanceira();

        receber("1", "PIX-007");

        Resultado<ComprovantePagamento> atrasado = controller.registrarRecebimento(reserva.getId(),
                new DadosPagamento("3490,00", "10/03/2026", FormaPagamento.PIX, "2", "PIX-008", null),
                versaoNaAberturaDaTela);

        assertEquals(StatusResultado.CONFLITO_CONCORRENCIA, atrasado.getStatus());
        assertEquals(1, pagamentos.buscarPorReserva(reserva.getId()).size());
    }

    @Test
    @DisplayName("FA02: reserva cancelada bloqueia pagamento e preserva o historico")
    void fa02ReservaCancelada() {
        definirPlano();
        receber("1", "PIX-009");

        reserva.cancelar(new Cancelamento(MotivoCancelamento.DESISTENCIA_CLIENTE,
                null, "Ana Silva", reserva.getQuantidadeViajantes()));
        reservas.atualizar(reserva);

        Resultado<ComprovantePagamento> bloqueado = receber("2", "PIX-010");

        assertEquals(StatusResultado.OPERACAO_BLOQUEADA, bloqueado.getStatus());
        SituacaoPagamentos situacao = controller.abrir(reserva.getId()).getDado().orElseThrow();
        assertTrue(situacao.isBloqueadaParaPagamento());
        assertEquals(0, new BigDecimal("3490.00").compareTo(situacao.financeiro().totalPago()),
                "o historico financeiro continua visivel");
    }

    @Test
    @DisplayName("A view SituacaoFinanceiraReserva do banco concorda com o calculo do Java")
    void bancoEJavaConcordamSobreASituacao() {
        // A view usa current_date e o Java usa o Clock injetado. Para comparar os dois
        // e preciso que compartilhem a mesma nocao de "hoje", entao este teste — e so
        // ele — monta o Controller com o relogio do sistema e datas relativas a hoje.
        CalculadoraFinanceira calculadora =
                new CalculadoraFinanceira(parcelas, pagamentos, Clock.systemDefaultZone());
        PacoteController pacoteController = new PacoteController(pacotes, reservas);
        ReservaController reservaController = new ReservaController(reservas, pacotes, clientes,
                calculadora, pacoteController);
        PagamentoController comRelogioReal = new PagamentoController(reservaController, reservas,
                parcelas, pagamentos, clientes, pacotes, calculadora);

        LocalDate hoje = LocalDate.now();
        comRelogioReal.definirParcelamento(reserva.getId(),
                new DadosParcelamento("2", Formatadores.formatarData(hoje)));

        conferirConcordancia(comRelogioReal, SituacaoFinanceira.PENDENTE);

        comRelogioReal.registrarRecebimento(reserva.getId(),
                new DadosPagamento("3490,00", Formatadores.formatarData(hoje), FormaPagamento.PIX,
                        "1", "PIX-011", null),
                comRelogioReal.abrir(reserva.getId()).getDado().orElseThrow().versaoFinanceira());

        conferirConcordancia(comRelogioReal, SituacaoFinanceira.PARCIALMENTE_PAGA);

        comRelogioReal.registrarRecebimento(reserva.getId(),
                new DadosPagamento("3490,00", Formatadores.formatarData(hoje), FormaPagamento.PIX,
                        "2", "PIX-012", null),
                comRelogioReal.abrir(reserva.getId()).getDado().orElseThrow().versaoFinanceira());

        conferirConcordancia(comRelogioReal, SituacaoFinanceira.QUITADA);
    }

    /** Compara a situacao calculada no Java com a que a view do banco devolve. */
    private void conferirConcordancia(PagamentoController controller, SituacaoFinanceira esperada) {
        SituacaoFinanceira noJava = controller.abrir(reserva.getId())
                .getDado().orElseThrow().financeiro().situacao();
        assertEquals(esperada, noJava);
        assertEquals(noJava.getDescricao(), situacaoNoBanco(),
                "a view SituacaoFinanceiraReserva precisa concordar com a CalculadoraFinanceira");
    }

    private String situacaoNoBanco() {
        return new JdbcPacoteRepository() {
            String consultar(Long reservaId) {
                return consultarEscalar(
                        "select situacaoFinanceira from SituacaoFinanceiraReserva where idReserva = ?",
                        resultado -> resultado.getString(1), reservaId);
            }
        }.consultar(reserva.getId());
    }
}
