package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.DadosCliente;
import com.tripcontrol.controller.dto.DadosPacote;
import com.tripcontrol.controller.dto.DadosReserva;
import com.tripcontrol.controller.dto.ResumoFinanceiro;
import com.tripcontrol.controller.dto.ResumoReserva;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.FormaPagamento;
import com.tripcontrol.model.MotivoCancelamento;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Pagamento;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.model.SituacaoFinanceira;
import com.tripcontrol.model.StatusReserva;
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
 * Cobre o fluxo principal e os fluxos alternativos do UC07 (Cancelar Reservas).
 *
 * <p>O FA04 (o funcionario desiste na mensagem de confirmacao) nao aparece aqui
 * porque nao chega ao Controller: a tela simplesmente nao chama {@code cancelar}
 * quando a confirmacao e recusada.</p>
 */
class ReservaCancelamentoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 12);

    private ReservaRepository reservaRepository;
    private PacoteRepository pacoteRepository;
    private ClienteRepository clienteRepository;
    private PagamentoRepository pagamentoRepository;
    private ParcelaRepository parcelaRepository;
    private PacoteController pacoteController;
    private ReservaController controller;

    private Pacote pacote;
    private Cliente cliente;
    private Reserva reserva;

    @BeforeEach
    void prepararCenario() {
        reservaRepository = new InMemoryReservaRepository();
        montarControllers(reservaRepository);
        pacote = criarPacote();
        cliente = criarCliente("Mariana Silva de Oliveira", "529.982.247-25");
        reserva = registrarReserva(cliente, 3);
    }

    private void montarControllers(ReservaRepository repositorioDeReservas) {
        this.reservaRepository = repositorioDeReservas;
        pacoteRepository = pacoteRepository == null ? new InMemoryPacoteRepository() : pacoteRepository;
        clienteRepository = clienteRepository == null ? new InMemoryClienteRepository() : clienteRepository;
        pagamentoRepository = pagamentoRepository == null
                ? new InMemoryPagamentoRepository() : pagamentoRepository;
        parcelaRepository = parcelaRepository == null
                ? new InMemoryParcelaRepository() : parcelaRepository;
        Clock relogioFixo = Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneId.of("UTC"));
        pacoteController = new PacoteController(pacoteRepository, reservaRepository, relogioFixo);
        controller = new ReservaController(reservaRepository, pacoteRepository, clienteRepository,
                new CalculadoraFinanceira(parcelaRepository, pagamentoRepository, relogioFixo),
                pacoteController);
    }

    private Pacote criarPacote() {
        return pacoteController.cadastrar(new DadosPacote("Fernando de Noronha, PE",
                "12/04/2026", "19/04/2026", "Mergulho e trilhas",
                "4800,00", "10", "Dia 1: chegada")).getDado().orElseThrow();
    }

    private Cliente criarCliente(String nome, String cpf) {
        ClienteController clienteController =
                new ClienteController(clienteRepository, reservaRepository, pacoteRepository);
        return clienteController.cadastrar(new DadosCliente(nome, cpf, "(87) 99999-0000",
                null, null, List.of())).getDado().orElseThrow();
    }

    private Reserva registrarReserva(Cliente titular, int viajantes) {
        Resultado<ResumoReserva> resultado = controller.registrar(new DadosReserva(
                titular.getId(), pacote.getId(), String.valueOf(viajantes),
                "12/04/2026", "19/04/2026", null));
        assertTrue(resultado.isSucesso(), "a reserva de apoio deveria ser criada");
        return resultado.getDado().orElseThrow().reserva();
    }

    // ------------------------------------------------------------------
    // Fluxo principal
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Fluxo principal: cancelamento devolve as vagas e registra motivo, data e responsavel")
    void cancelaReservaAtiva() {
        assertEquals(3, pacoteController.vagasOcupadas(pacote.getId()));

        Resultado<ResumoReserva> resultado = controller.cancelar(reserva.getId(),
                MotivoCancelamento.DESISTENCIA_CLIENTE, null, "Ana Silva", reserva.getVersao());

        assertTrue(resultado.isSucesso());
        assertEquals(StatusReserva.CANCELADA, reserva.getStatus());
        assertEquals(0, pacoteController.vagasOcupadas(pacote.getId()));
        assertEquals(10, pacoteController.comVagas(pacote).vagasDisponiveis());

        assertNotNull(reserva.getCancelamento());
        assertEquals(MotivoCancelamento.DESISTENCIA_CLIENTE, reserva.getCancelamento().getMotivo());
        assertEquals("Ana Silva", reserva.getCancelamento().getUsuarioResponsavel());
        assertEquals(3, reserva.getCancelamento().getVagasDevolvidas());
        assertNotNull(reserva.getCancelamento().getDataHora());
        assertTrue(resultado.getMensagem().contains("3 vaga(s)"));
    }

    @Test
    @DisplayName("O historico de pagamentos e preservado apos o cancelamento")
    void preservaHistoricoFinanceiro() {
        Pagamento pagamento = new Pagamento(reserva.getId(), 1, new BigDecimal("4800.00"),
                LocalDate.of(2026, 3, 10), FormaPagamento.PIX);
        pagamentoRepository.salvar(pagamento);

        controller.cancelar(reserva.getId(), MotivoCancelamento.CANCELAMENTO_AGENCIA,
                null, "Ana Silva", reserva.getVersao());

        assertEquals(1, pagamentoRepository.buscarPorReserva(reserva.getId()).size());
        ResumoFinanceiro financeiro = controller.resumoFinanceiro(reserva);
        // reserva de 3 viajantes a R$ 4.800,00 = R$ 14.400,00, com uma parcela ja paga
        assertEquals(0, new BigDecimal("14400.00").compareTo(financeiro.valorTotal()));
        assertEquals(0, new BigDecimal("4800.00").compareTo(financeiro.totalPago()));
        assertEquals(0, new BigDecimal("9600.00").compareTo(financeiro.saldoPendente()));
        assertEquals(SituacaoFinanceira.PARCIALMENTE_PAGA, financeiro.situacao());
    }

    @Test
    @DisplayName("Uma vaga liberada pelo cancelamento pode ser reservada de novo")
    void vagasVoltamParaONovoUso() {
        controller.cancelar(reserva.getId(), MotivoCancelamento.ALTERACAO_DE_DATA,
                null, "Ana Silva", reserva.getVersao());

        Cliente outro = criarCliente("Carlos Eduardo de Souza", "111.444.777-35");
        Resultado<ResumoReserva> nova = controller.registrar(new DadosReserva(
                outro.getId(), pacote.getId(), "10", "12/04/2026", "19/04/2026", null));

        assertTrue(nova.isSucesso(), "as 10 vagas deveriam estar livres apos o cancelamento");
    }

    // ------------------------------------------------------------------
    // Fluxos alternativos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("FA01: reserva inexistente nao e cancelada")
    void recusaReservaInexistente() {
        Resultado<ResumoReserva> resultado = controller.cancelar(999L,
                MotivoCancelamento.DESISTENCIA_CLIENTE, null, "Ana Silva", 0L);

        assertEquals(StatusResultado.NAO_ENCONTRADO, resultado.getStatus());
    }

    @Test
    @DisplayName("FA02: segunda tentativa e bloqueada e mostra o cancelamento anterior")
    void bloqueiaSegundoCancelamento() {
        controller.cancelar(reserva.getId(), MotivoCancelamento.DESISTENCIA_CLIENTE,
                null, "Ana Silva", reserva.getVersao());

        Resultado<ResumoReserva> segunda = controller.cancelar(reserva.getId(),
                MotivoCancelamento.CANCELAMENTO_AGENCIA, null, "Theo Pereira", reserva.getVersao());

        assertEquals(StatusResultado.OPERACAO_BLOQUEADA, segunda.getStatus());
        assertTrue(segunda.getMensagem().contains("ja foi cancelada"));
        assertTrue(segunda.getMensagem().contains("Ana Silva"));
        assertTrue(segunda.getMensagem().contains(MotivoCancelamento.DESISTENCIA_CLIENTE.getDescricao()));
        // o motivo original permanece intacto
        assertEquals(MotivoCancelamento.DESISTENCIA_CLIENTE, reserva.getCancelamento().getMotivo());
    }

    @Test
    @DisplayName("FA03: sem motivo selecionado o cancelamento nao acontece")
    void exigeMotivo() {
        Resultado<ResumoReserva> resultado = controller.cancelar(reserva.getId(),
                null, null, "Ana Silva", reserva.getVersao());

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("motivo").isPresent());
        assertTrue(reserva.isAtiva());
    }

    @Test
    @DisplayName("FA03: motivo \"Outro\" exige a descricao detalhada")
    void exigeDescricaoQuandoMotivoOutro() {
        Resultado<ResumoReserva> semDescricao = controller.cancelar(reserva.getId(),
                MotivoCancelamento.OUTRO, "   ", "Ana Silva", reserva.getVersao());

        assertEquals(StatusResultado.ERRO_VALIDACAO, semDescricao.getStatus());
        assertTrue(semDescricao.mensagemDoCampo("descricao").isPresent());
        assertTrue(reserva.isAtiva());

        Resultado<ResumoReserva> comDescricao = controller.cancelar(reserva.getId(),
                MotivoCancelamento.OUTRO, "Pacote remanejado para outra agencia",
                "Ana Silva", reserva.getVersao());

        assertTrue(comDescricao.isSucesso());
        assertEquals("Pacote remanejado para outra agencia", reserva.getCancelamento().getDescricao());
    }

    @Test
    @DisplayName("FA05: reserva alterada por outro processo interrompe o cancelamento")
    void detectaAlteracaoConcorrente() {
        long versaoDesatualizada = reserva.getVersao() - 1;

        Resultado<ResumoReserva> resultado = controller.cancelar(reserva.getId(),
                MotivoCancelamento.DESISTENCIA_CLIENTE, null, "Ana Silva", versaoDesatualizada);

        assertEquals(StatusResultado.CONFLITO_CONCORRENCIA, resultado.getStatus());
        assertTrue(reserva.isAtiva());
        assertEquals(3, pacoteController.vagasOcupadas(pacote.getId()));
    }

    @Test
    @DisplayName("FA06: falha ao persistir desfaz a alteracao parcial")
    void desfazAlteracaoQuandoPersistenciaFalha() {
        // repositorio que aceita a reserva inicial e falha na atualizacao do cancelamento
        ReservaRepository repositorioComFalha = new InMemoryReservaRepository() {
            @Override
            public synchronized Reserva atualizar(Reserva entidade) {
                throw new IllegalStateException("falha simulada de persistencia");
            }
        };
        montarControllers(repositorioComFalha);
        Reserva reservaLocal = registrarReserva(cliente, 2);

        Resultado<ResumoReserva> resultado = controller.cancelar(reservaLocal.getId(),
                MotivoCancelamento.DESISTENCIA_CLIENTE, null, "Ana Silva", reservaLocal.getVersao());

        assertEquals(StatusResultado.OPERACAO_BLOQUEADA, resultado.getStatus());
        assertTrue(reservaLocal.isAtiva(), "a reserva deve continuar ativa");
        assertNull(reservaLocal.getCancelamento(), "nenhum cancelamento parcial pode ficar registrado");
        assertEquals(2, pacoteController.vagasOcupadas(pacote.getId()),
                "a disponibilidade do pacote nao pode mudar");
    }

    // ------------------------------------------------------------------
    // Pesquisa de reservas (UC07, passos 2 e 3)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Pesquisa por codigo, CPF, nome e pacote localiza a reserva")
    void pesquisaPorCadaCriterio() {
        assertTrue(controller.buscar(reserva.getCodigo(), null, null, null).isSucesso());
        assertTrue(controller.buscar(null, "529.982.247-25", null, null).isSucesso());
        assertTrue(controller.buscar(null, null, "Mariana", null).isSucesso());
        assertTrue(controller.buscar(null, null, null, pacote.getCodigo()).isSucesso());

        List<ResumoReserva> encontradas =
                controller.buscar(null, null, "Mariana", null).getDado().orElseThrow();
        assertEquals(1, encontradas.size());
        assertEquals(reserva.getCodigo(), encontradas.get(0).codigo());
    }

    @Test
    @DisplayName("Pesquisa sem criterio nenhum e recusada")
    void exigeAoMenosUmCriterio() {
        Resultado<List<ResumoReserva>> resultado = controller.buscar("  ", null, "", null);

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("criterios").isPresent());
    }

    @Test
    @DisplayName("FA01 da pesquisa: criterios sem correspondencia devolvem NAO_ENCONTRADO")
    void avisaQuandoNadaCorresponde() {
        Resultado<List<ResumoReserva>> resultado = controller.buscar("RE-0000", null, null, null);

        assertEquals(StatusResultado.NAO_ENCONTRADO, resultado.getStatus());
        assertFalse(resultado.isSucesso());
    }
}
