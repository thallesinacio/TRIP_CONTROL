package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.DadosCliente;
import com.tripcontrol.controller.dto.DadosPacote;
import com.tripcontrol.controller.dto.DadosReserva;
import com.tripcontrol.controller.dto.ResumoReserva;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.repository.ClienteRepository;
import com.tripcontrol.repository.PacoteRepository;
import com.tripcontrol.repository.ReservaRepository;
import com.tripcontrol.repository.memory.InMemoryClienteRepository;
import com.tripcontrol.repository.memory.InMemoryPacoteRepository;
import com.tripcontrol.repository.memory.InMemoryPagamentoRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cobre o fluxo principal e os fluxos alternativos FA01 e FA02 do UC03. */
class ReservaControllerTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 12);

    private ReservaRepository reservaRepository;
    private PacoteRepository pacoteRepository;
    private ClienteRepository clienteRepository;
    private PacoteController pacoteController;
    private ReservaController controller;
    private Pacote pacote;
    private Cliente cliente;

    @BeforeEach
    void prepararCenario() {
        reservaRepository = new InMemoryReservaRepository();
        pacoteRepository = new InMemoryPacoteRepository();
        clienteRepository = new InMemoryClienteRepository();
        Clock relogioFixo = Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneId.of("UTC"));
        pacoteController = new PacoteController(pacoteRepository, reservaRepository, relogioFixo);
        controller = new ReservaController(reservaRepository, pacoteRepository, clienteRepository,
                new InMemoryPagamentoRepository(), pacoteController);

        pacote = pacoteController.cadastrar(new DadosPacote("Gramado & Canela, RS",
                "05/05/2026", "12/05/2026", null, "3490,00", "10", null)).getDado().orElseThrow();

        ClienteController clienteController =
                new ClienteController(clienteRepository, reservaRepository, pacoteRepository);
        cliente = clienteController.cadastrar(new DadosCliente("Carlos Eduardo de Souza",
                "529.982.247-25", "(87) 99999-0000", null, null, List.of())).getDado().orElseThrow();
    }

    private DadosReserva dados(String quantidade, String inicio, String fim) {
        return new DadosReserva(cliente.getId(), pacote.getId(), quantidade, inicio, fim, null);
    }

    @Test
    @DisplayName("Fluxo principal: reserva confirmada calcula o valor total e ocupa vagas")
    void registraReservaValida() {
        Resultado<ResumoReserva> resultado = controller.registrar(dados("2", "05/05/2026", "12/05/2026"));

        assertTrue(resultado.isSucesso());
        ResumoReserva resumo = resultado.getDado().orElseThrow();
        assertEquals("RE-8901", resumo.codigo());
        assertEquals(0, new BigDecimal("6980.00").compareTo(resumo.valorTotal()));
        assertEquals(2, pacoteController.vagasOcupadas(pacote.getId()));
    }

    @Test
    @DisplayName("FA01: quantidade acima das vagas disponiveis e recusada com o saldo real")
    void recusaVagasInsuficientes() {
        controller.registrar(dados("8", "05/05/2026", "12/05/2026"));

        Resultado<ResumoReserva> resultado = controller.registrar(dados("5", "05/05/2026", "12/05/2026"));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("quantidadeViajantes").orElseThrow()
                .contains("Vagas disponiveis: 2"));
        assertEquals(8, pacoteController.vagasOcupadas(pacote.getId()));
    }

    @Test
    @DisplayName("FA02: periodo fora da vigencia do pacote e recusado")
    void recusaPeriodoIncompativel() {
        Resultado<ResumoReserva> resultado = controller.registrar(dados("2", "01/05/2026", "20/05/2026"));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("dataInicio").orElseThrow().contains("05/05/2026"));
        assertEquals(0L, reservaRepository.contar());
    }

    @Test
    @DisplayName("Quantidade de viajantes invalida e recusada antes de consultar vagas")
    void recusaQuantidadeInvalida() {
        Resultado<ResumoReserva> resultado = controller.registrar(dados("0", "05/05/2026", "12/05/2026"));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("quantidadeViajantes").isPresent());
    }

    @Test
    @DisplayName("Cliente inexistente interrompe o registro da reserva")
    void recusaClienteInexistente() {
        Resultado<ResumoReserva> resultado = controller.registrar(
                new DadosReserva(999L, pacote.getId(), "2", "05/05/2026", "12/05/2026", null));

        assertEquals(StatusResultado.NAO_ENCONTRADO, resultado.getStatus());
    }
}
