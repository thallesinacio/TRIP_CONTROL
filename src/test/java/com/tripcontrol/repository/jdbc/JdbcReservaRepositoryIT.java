package com.tripcontrol.repository.jdbc;

import com.tripcontrol.controller.CalculadoraFinanceira;
import com.tripcontrol.controller.PacoteController;
import com.tripcontrol.controller.ReservaController;
import com.tripcontrol.controller.StatusResultado;
import com.tripcontrol.controller.dto.DadosReserva;
import com.tripcontrol.model.Cancelamento;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.MotivoCancelamento;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.model.StatusReserva;
import com.tripcontrol.repository.ConflitoDeConcorrenciaException;
import com.tripcontrol.repository.RepositorioException;
import com.tripcontrol.util.Conexoes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Persistencia de reservas, incluindo cancelamento e bloqueio otimista (UC03, UC04, UC07). */
class JdbcReservaRepositoryIT extends RepositorioJdbcIT {

    private final JdbcPacoteRepository pacotes = new JdbcPacoteRepository();
    private final JdbcClienteRepository clientes = new JdbcClienteRepository();
    private final JdbcReservaRepository reservas = new JdbcReservaRepository();

    private Pacote pacote;
    private Cliente cliente;

    @BeforeEach
    void prepararDados() {
        Pacote novoPacote = new Pacote("Noronha " + System.nanoTime(),
                LocalDate.of(2026, 4, 12), LocalDate.of(2026, 4, 19),
                "Mergulho", new BigDecimal("4800.00"), 10, "Dia 1: chegada");
        novoPacote.setCodigo(pacotes.proximoCodigo());
        pacote = pacotes.salvar(novoPacote);

        Cliente novoCliente = new Cliente("Mariana Silva", "52998224725", "87999990000");
        cliente = clientes.salvar(novoCliente);
    }

    private Reserva novaReserva(int viajantes) {
        Reserva reserva = new Reserva(cliente.getId(), pacote.getId(), viajantes,
                LocalDate.of(2026, 4, 12), LocalDate.of(2026, 4, 19), "Sem observacoes");
        reserva.setCodigo(reservas.proximoCodigo());
        reserva.setValorTotal(new BigDecimal("4800.00").multiply(BigDecimal.valueOf(viajantes)));
        return reservas.salvar(reserva);
    }

    @Test
    @DisplayName("Reserva gravada volta com cliente, periodo e situacao Ativa")
    void gravaERecupera() {
        Reserva gravada = novaReserva(2);
        assertNotNull(gravada.getId());
        assertEquals(0L, gravada.getVersao());

        Reserva lida = reservas.buscarPorId(gravada.getId()).orElseThrow();
        assertEquals(cliente.getId(), lida.getClienteId());
        assertEquals(pacote.getId(), lida.getPacoteId());
        assertEquals(LocalDate.of(2026, 4, 12), lida.getDataInicio());
        assertEquals(LocalDate.of(2026, 4, 19), lida.getDataFim());
        assertEquals(StatusReserva.ATIVA, lida.getStatus());
        assertNull(lida.getCancelamento());
    }

    @Test
    @DisplayName("UC03: a gravacao JDBC recusa pacote encerrado sem criar reserva")
    void recusaReservaDePacoteEncerrado() {
        Clock depoisDoPacote = Clock.fixed(LocalDate.of(2026, 4, 20)
                .atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        PacoteController controlePacotes = new PacoteController(pacotes, reservas, depoisDoPacote);
        ReservaController controleReservas = new ReservaController(reservas, pacotes, clientes,
                new CalculadoraFinanceira(new JdbcParcelaRepository(), new JdbcPagamentoRepository(),
                        depoisDoPacote), controlePacotes);

        var resultado = controleReservas.registrar(new DadosReserva(cliente.getId(), pacote.getId(),
                "2", "12/04/2026", "19/04/2026", null));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("pacote").orElseThrow().contains("encerrado"));
        assertTrue(reservas.buscarPorPacote(pacote.getId()).isEmpty());
    }

    @Test
    @DisplayName("Cancelamento grava motivo, responsavel e faz a situacao virar Cancelada")
    void persisteCancelamento() {
        Reserva reserva = novaReserva(3);

        reserva.cancelar(new Cancelamento(MotivoCancelamento.DESISTENCIA_CLIENTE,
                null, "Ana Silva", 3));
        reservas.atualizar(reserva);

        Reserva lida = reservas.buscarPorId(reserva.getId()).orElseThrow();
        assertEquals(StatusReserva.CANCELADA, lida.getStatus());
        assertNotNull(lida.getCancelamento());
        assertEquals(MotivoCancelamento.DESISTENCIA_CLIENTE, lida.getCancelamento().getMotivo());
        assertEquals("Ana Silva", lida.getCancelamento().getUsuarioResponsavel());
        assertEquals(3, lida.getCancelamento().getVagasDevolvidas());
    }

    @Test
    @DisplayName("Reserva cancelada deixa de ocupar vaga do pacote")
    void cancelamentoDevolveVagas() {
        Reserva reserva = novaReserva(4);
        assertEquals(1, reservas.buscarAtivasPorPacote(pacote.getId()).size());

        reserva.cancelar(new Cancelamento(MotivoCancelamento.CANCELAMENTO_AGENCIA, null, "Ana Silva", 4));
        reservas.atualizar(reserva);

        assertTrue(reservas.buscarAtivasPorPacote(pacote.getId()).isEmpty());
        assertEquals(1, reservas.buscarPorPacote(pacote.getId()).size());
    }

    @Test
    @DisplayName("Cada gravacao avanca a versao da reserva")
    void versaoAvancaACadaGravacao() {
        Reserva reserva = novaReserva(1);

        reserva.setObservacoes("Alterado");
        reservas.atualizar(reserva);

        assertEquals(1L, reserva.getVersao());
        assertEquals(1L, reservas.buscarPorId(reserva.getId()).orElseThrow().getVersao());
    }

    @Test
    @DisplayName("Bloqueio otimista: gravar sobre versao antiga e recusado")
    void detectaAlteracaoConcorrente() {
        Reserva reserva = novaReserva(1);

        // simula outra instancia gravando antes: a versao no banco avanca
        avancarVersaoPorFora(reserva.getId());

        reserva.cancelar(new Cancelamento(MotivoCancelamento.ALTERACAO_DE_DATA, null, "Theo", 1));
        assertThrows(ConflitoDeConcorrenciaException.class, () -> reservas.atualizar(reserva));

        // nada foi gravado: a reserva continua ativa no banco
        assertEquals(StatusReserva.ATIVA, reservas.buscarPorId(reserva.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("A trigger do banco impede ultrapassar a capacidade do pacote")
    void triggerBarraExcessoDeVagas() {
        novaReserva(8);

        RepositorioException erro = assertThrows(RepositorioException.class, () -> novaReserva(5));
        assertTrue(erro.getMessage().toLowerCase().contains("vagas"));
    }

    @Test
    @DisplayName("Busca por cliente e por periodo de viagem localizam a reserva")
    void buscasPorClienteEPeriodo() {
        Reserva reserva = novaReserva(2);

        List<Reserva> porCliente = reservas.buscarPorCliente(cliente.getId());
        assertTrue(porCliente.stream().anyMatch(item -> reserva.getId().equals(item.getId())));

        List<Reserva> porPeriodo = reservas.buscarPorPeriodoDeViagem(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        assertTrue(porPeriodo.stream().anyMatch(item -> reserva.getId().equals(item.getId())));
    }

    /** Avanca a versao direto no banco, simulando outra instancia da aplicacao. */
    private void avancarVersaoPorFora(Long reservaId) {
        try (Connection conexao = Conexoes.atual();
             Statement comando = conexao.createStatement()) {
            comando.executeUpdate("update Reserva set versao = versao + 1 where idReserva = " + reservaId);
        } catch (SQLException excecao) {
            throw new IllegalStateException(excecao);
        }
    }
}
