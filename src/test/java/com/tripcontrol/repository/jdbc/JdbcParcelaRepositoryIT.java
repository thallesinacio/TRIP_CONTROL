package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Parcela;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.model.StatusParcela;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plano de parcelas do UC05 sobre a tabela {@code Parcela}. */
class JdbcParcelaRepositoryIT extends RepositorioJdbcIT {

    private final JdbcPacoteRepository pacotes = new JdbcPacoteRepository();
    private final JdbcClienteRepository clientes = new JdbcClienteRepository();
    private final JdbcReservaRepository reservas = new JdbcReservaRepository();
    private final JdbcParcelaRepository parcelas = new JdbcParcelaRepository();

    private Reserva reserva;

    @BeforeEach
    void prepararReserva() {
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

    private List<Parcela> gerarPlanoDeTresParcelas() {
        // 6980,00 em 3: 2326,66 + 2326,66 + 2326,68
        parcelas.salvar(new Parcela(reserva.getId(), 1, 3,
                new BigDecimal("2326.66"), LocalDate.of(2026, 3, 10)));
        parcelas.salvar(new Parcela(reserva.getId(), 2, 3,
                new BigDecimal("2326.66"), LocalDate.of(2026, 4, 10)));
        parcelas.salvar(new Parcela(reserva.getId(), 3, 3,
                new BigDecimal("2326.68"), LocalDate.of(2026, 5, 10)));
        return parcelas.buscarPorReserva(reserva.getId());
    }

    @Test
    @DisplayName("Plano gravado volta em ordem, com valores e vencimentos intactos")
    void gravaERecuperaOPlano() {
        List<Parcela> plano = gerarPlanoDeTresParcelas();

        assertEquals(3, plano.size());
        assertEquals(1, plano.get(0).getNumero());
        assertEquals(0, new BigDecimal("2326.66").compareTo(plano.get(0).getValor()));
        assertEquals(0, new BigDecimal("2326.68").compareTo(plano.get(2).getValor()));
        assertEquals(LocalDate.of(2026, 4, 10), plano.get(1).getDataVencimento());

        BigDecimal soma = plano.stream().map(Parcela::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("6980.00").compareTo(soma));
    }

    @Test
    @DisplayName("totalParcelas e contado pela consulta, nao guardado em coluna")
    void totalParcelasEDerivado() {
        gerarPlanoDeTresParcelas();

        // mesmo buscando uma parcela isolada, o total reflete o plano inteiro
        Parcela isolada = parcelas.buscarPorReservaENumero(reserva.getId(), 2).orElseThrow();
        assertEquals(3, isolada.getTotalParcelas());
        assertEquals(3, parcelas.buscarPorId(isolada.getId()).orElseThrow().getTotalParcelas());
    }

    @Test
    @DisplayName("Parcela recem-planejada nasce pendente e sem forma de pagamento")
    void parcelaNasceEmAberto() {
        Parcela primeira = gerarPlanoDeTresParcelas().get(0);

        assertEquals(StatusParcela.PENDENTE, primeira.getStatus());
        assertNull(primeira.getDataPagamento());
        assertNull(primeira.getPagamentoId());
    }

    @Test
    @DisplayName("Remover por reserva limpa o plano inteiro")
    void removePorReserva() {
        gerarPlanoDeTresParcelas();
        parcelas.removerPorReserva(reserva.getId());
        assertTrue(parcelas.buscarPorReserva(reserva.getId()).isEmpty());
    }

    @Test
    @DisplayName("O id sintetico volta a mesma parcela")
    void idSinteticoEReversivel() {
        Parcela segunda = gerarPlanoDeTresParcelas().get(1);
        Parcela pelaChave = parcelas.buscarPorId(segunda.getId()).orElseThrow();

        assertEquals(segunda.getNumero(), pelaChave.getNumero());
        assertEquals(segunda.getReservaId(), pelaChave.getReservaId());
        assertNotNull(segunda.getId());
    }
}
