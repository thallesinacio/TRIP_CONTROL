package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.FormaPagamento;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Pagamento;
import com.tripcontrol.model.Parcela;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.model.StatusParcela;
import com.tripcontrol.repository.ConflitoDeConcorrenciaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recebimentos do UC05 gravados nas colunas de pagamento da tabela {@code Parcela}.
 *
 * <p>O teste de duplicidade e o mais importante daqui: e ele que prova que o
 * {@code and valorPago is null} do UPDATE realmente fecha a corrida do FA05.</p>
 */
class JdbcPagamentoRepositoryIT extends RepositorioJdbcIT {

    private final JdbcPacoteRepository pacotes = new JdbcPacoteRepository();
    private final JdbcClienteRepository clientes = new JdbcClienteRepository();
    private final JdbcReservaRepository reservas = new JdbcReservaRepository();
    private final JdbcParcelaRepository parcelas = new JdbcParcelaRepository();
    private final JdbcPagamentoRepository pagamentos = new JdbcPagamentoRepository();

    private Reserva reserva;

    @BeforeEach
    void prepararPlano() {
        Pacote pacote = new Pacote("Noronha " + System.nanoTime(),
                LocalDate.of(2026, 4, 12), LocalDate.of(2026, 4, 19),
                "Mergulho", new BigDecimal("4800.00"), 10, null);
        pacote.setCodigo(pacotes.proximoCodigo());
        pacotes.salvar(pacote);

        Cliente cliente = clientes.salvar(new Cliente("Mariana Silva", "11144477735", "81988881234"));

        Reserva nova = new Reserva(cliente.getId(), pacote.getId(), 2,
                LocalDate.of(2026, 4, 12), LocalDate.of(2026, 4, 19), null);
        nova.setCodigo(reservas.proximoCodigo());
        nova.setValorTotal(new BigDecimal("9600.00"));
        reserva = reservas.salvar(nova);

        parcelas.salvar(new Parcela(reserva.getId(), 1, 2,
                new BigDecimal("4800.00"), LocalDate.of(2026, 3, 10)));
        parcelas.salvar(new Parcela(reserva.getId(), 2, 2,
                new BigDecimal("4800.00"), LocalDate.of(2026, 4, 10)));
    }

    private Pagamento registrar(int numeroParcela, String comprovante) {
        Pagamento pagamento = new Pagamento(reserva.getId(), numeroParcela,
                new BigDecimal("4800.00"), LocalDate.of(2026, 3, 12), FormaPagamento.PIX);
        pagamento.setIdentificadorTransacao(comprovante);
        pagamento.setObservacao("Recebido no caixa");
        pagamento.setNumeroRecibo(pagamentos.proximoNumeroRecibo());
        return pagamentos.salvar(pagamento);
    }

    @Test
    @DisplayName("Recebimento gravado volta com recibo, forma, comprovante e observacao")
    void gravaERecupera() {
        Pagamento gravado = registrar(1, "PIX-ABC-001");
        assertNotNull(gravado.getId());

        Pagamento lido = pagamentos.buscarPorReservaEParcela(reserva.getId(), 1).orElseThrow();
        assertEquals(0, new BigDecimal("4800.00").compareTo(lido.getValor()));
        assertEquals(LocalDate.of(2026, 3, 12), lido.getDataRecebimento());
        assertEquals(FormaPagamento.PIX, lido.getFormaPagamento());
        assertEquals("PIX-ABC-001", lido.getIdentificadorTransacao());
        assertEquals("Recebido no caixa", lido.getObservacao());
        assertTrue(lido.getNumeroRecibo().startsWith("RC-"));
    }

    @Test
    @DisplayName("O recebimento da baixa na parcela: ela passa a Quitada")
    void recebimentoQuitaAParcela() {
        registrar(1, "PIX-ABC-002");

        Parcela parcela = parcelas.buscarPorReservaENumero(reserva.getId(), 1).orElseThrow();
        assertEquals(StatusParcela.QUITADA, parcela.getStatus());
        assertEquals(LocalDate.of(2026, 3, 12), parcela.getDataPagamento());

        // a segunda parcela continua em aberto
        assertEquals(StatusParcela.PENDENTE,
                parcelas.buscarPorReservaENumero(reserva.getId(), 2).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Pagar a mesma parcela duas vezes e recusado pelo proprio UPDATE (FA05)")
    void recusaSegundoPagamentoNaMesmaParcela() {
        registrar(1, "PIX-ABC-003");

        assertThrows(ConflitoDeConcorrenciaException.class, () -> registrar(1, "PIX-ABC-004"));

        // o pagamento original segue intacto
        assertEquals("PIX-ABC-003",
                pagamentos.buscarPorReservaEParcela(reserva.getId(), 1).orElseThrow()
                        .getIdentificadorTransacao());
    }

    @Test
    @DisplayName("Numeros de recibo vem de sequence: unicos e sem repeticao")
    void reciboVemDeSequence() {
        String primeiro = registrar(1, "PIX-ABC-005").getNumeroRecibo();
        String segundo = registrar(2, "PIX-ABC-006").getNumeroRecibo();

        assertTrue(primeiro.matches("RC-\\d{6}"));
        assertTrue(segundo.matches("RC-\\d{6}"));
        assertTrue(Long.parseLong(segundo.substring(3)) > Long.parseLong(primeiro.substring(3)));
    }

    @Test
    @DisplayName("So parcelas com recebimento contam como pagamento da reserva")
    void listaApenasParcelasRecebidas() {
        assertTrue(pagamentos.buscarPorReserva(reserva.getId()).isEmpty());

        registrar(1, "PIX-ABC-007");
        List<Pagamento> encontrados = pagamentos.buscarPorReserva(reserva.getId());

        assertEquals(1, encontrados.size());
        assertEquals(1, encontrados.get(0).getNumeroParcela());
    }

    @Test
    @DisplayName("Busca pelo comprovante sustenta o FA04 do UC05")
    void buscaPeloComprovante() {
        registrar(1, "PIX-ABC-008");

        assertTrue(pagamentos.buscarPorIdentificadorTransacao("pix-abc-008").isPresent());
        assertTrue(pagamentos.buscarPorIdentificadorTransacao("PIX-INEXISTENTE").isEmpty());
    }

    @Test
    @DisplayName("Busca por periodo de recebimento alimenta o relatorio de Pagamentos")
    void buscaPorPeriodo() {
        registrar(1, "PIX-ABC-009");

        assertTrue(pagamentos.buscarPorPeriodoDeRecebimento(
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).stream()
                .anyMatch(p -> reserva.getId().equals(p.getReservaId())));
        assertTrue(pagamentos.buscarPorPeriodoDeRecebimento(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)).stream()
                .noneMatch(p -> reserva.getId().equals(p.getReservaId())));
    }

    @Test
    @DisplayName("Remover o pagamento devolve a parcela ao estado pendente")
    void removerDesfazORecebimento() {
        Pagamento pagamento = registrar(1, "PIX-ABC-010");

        assertTrue(pagamentos.remover(pagamento.getId()));

        Parcela parcela = parcelas.buscarPorReservaENumero(reserva.getId(), 1).orElseThrow();
        assertEquals(StatusParcela.PENDENTE, parcela.getStatus());
        assertNull(parcela.getDataPagamento());
        assertTrue(pagamentos.buscarPorReserva(reserva.getId()).isEmpty());
    }
}
