package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.DadosPacote;
import com.tripcontrol.controller.dto.PacoteComVagas;
import com.tripcontrol.controller.dto.DadosReserva;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.repository.memory.InMemoryClienteRepository;
import com.tripcontrol.repository.memory.InMemoryPacoteRepository;
import com.tripcontrol.repository.memory.InMemoryPagamentoRepository;
import com.tripcontrol.repository.memory.InMemoryReservaRepository;
import com.tripcontrol.repository.ClienteRepository;
import com.tripcontrol.repository.PacoteRepository;
import com.tripcontrol.repository.ReservaRepository;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.util.Formatadores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cobre o fluxo principal e os fluxos alternativos do UC01 e do UC04. */
class PacoteControllerTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 12);

    private PacoteRepository pacoteRepository;
    private ReservaRepository reservaRepository;
    private ClienteRepository clienteRepository;
    private PacoteController controller;

    @BeforeEach
    void prepararCenario() {
        pacoteRepository = new InMemoryPacoteRepository();
        reservaRepository = new InMemoryReservaRepository();
        clienteRepository = new InMemoryClienteRepository();
        Clock relogioFixo = Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneId.of("UTC"));
        controller = new PacoteController(pacoteRepository, reservaRepository, relogioFixo);
    }

    private DadosPacote dadosValidos() {
        return new DadosPacote("Gramado & Canela, RS", "05/05/2026", "12/05/2026",
                "Roteiro cultural", "3490,00", "30", "Dia 1: chegada");
    }

    @Test
    @DisplayName("Fluxo principal: pacote valido e gravado com codigo gerado")
    void cadastraPacoteValido() {
        Resultado<Pacote> resultado = controller.cadastrar(dadosValidos());

        assertTrue(resultado.isSucesso());
        Pacote pacote = resultado.getDado().orElseThrow();
        assertEquals("PC-101", pacote.getCodigo());
        assertEquals(30, pacote.getCapacidadeTotal());
        assertEquals(1L, pacoteRepository.contar());
    }

    @Test
    @DisplayName("FA01: campos obrigatorios vazios sao apontados um a um")
    void recusaCamposObrigatorios() {
        Resultado<Pacote> resultado = controller.cadastrar(
                new DadosPacote("", "", "", null, "", "", null));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("destino").isPresent());
        assertTrue(resultado.mensagemDoCampo("preco").isPresent());
        assertTrue(resultado.mensagemDoCampo("capacidadeTotal").isPresent());
        assertEquals(0L, pacoteRepository.contar());
    }

    @Test
    @DisplayName("FA01: preco e capacidade devem ser maiores que zero")
    void recusaValoresNaoPositivos() {
        Resultado<Pacote> resultado = controller.cadastrar(
                new DadosPacote("Recife, PE", "05/05/2026", "12/05/2026", null, "0,00", "0", null));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("preco").orElseThrow().contains("maior que zero"));
        assertTrue(resultado.mensagemDoCampo("capacidadeTotal").orElseThrow().contains("maior que zero"));
    }

    @Test
    @DisplayName("FA02: data de fim anterior a de inicio e recusada")
    void recusaPeriodoInvertido() {
        Resultado<Pacote> resultado = controller.cadastrar(
                new DadosPacote("Recife, PE", "12/05/2026", "05/05/2026", null, "1000,00", "10", null));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("dataFim").isPresent());
    }

    @Test
    @DisplayName("FA02: data de inicio no passado e recusada")
    void recusaInicioNoPassado() {
        Resultado<Pacote> resultado = controller.cadastrar(
                new DadosPacote("Recife, PE", "01/01/2026", "10/01/2026", null, "1000,00", "10", null));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("dataInicio").isPresent());
    }

    @Test
    @DisplayName("FA03: mesmo destino e mesmo periodo devolve o pacote existente")
    void detectaDuplicidade() {
        controller.cadastrar(dadosValidos());
        Resultado<Pacote> segundo = controller.cadastrar(dadosValidos());

        assertEquals(StatusResultado.DUPLICIDADE, segundo.getStatus());
        assertEquals("PC-101", segundo.getDado().orElseThrow().getCodigo());
        assertEquals(1L, pacoteRepository.contar());
    }

    @Test
    @DisplayName("UC04: vagas disponiveis sao derivadas das reservas ativas")
    void calculaVagasAPartirDasReservas() {
        Pacote pacote = controller.cadastrar(dadosValidos()).getDado().orElseThrow();
        registrarReserva(pacote, 4);

        PacoteComVagas linha = controller.comVagas(pacote);

        assertEquals(4, linha.vagasOcupadas());
        assertEquals(26, linha.vagasDisponiveis());
    }

    @Test
    @DisplayName("UC04 FA03: nova capacidade menor que as vagas ocupadas e recusada")
    void recusaCapacidadeAbaixoDoOcupado() {
        Pacote pacote = controller.cadastrar(dadosValidos()).getDado().orElseThrow();
        registrarReserva(pacote, 6);

        Resultado<PacoteComVagas> resultado =
                controller.alterarCapacidade(pacote.getId(), "4", "Reducao de onibus", "Ana", 6);

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("novaCapacidade").orElseThrow().contains("6"));
        assertEquals(30, pacote.getCapacidadeTotal());
    }

    @Test
    @DisplayName("UC04 FA04: alteracao concorrente interrompe o salvamento")
    void detectaAlteracaoConcorrente() {
        Pacote pacote = controller.cadastrar(dadosValidos()).getDado().orElseThrow();
        registrarReserva(pacote, 3);

        Resultado<PacoteComVagas> resultado =
                controller.alterarCapacidade(pacote.getId(), "40", "Alta demanda", "Ana", 0);

        assertEquals(StatusResultado.CONFLITO_CONCORRENCIA, resultado.getStatus());
        assertEquals(30, pacote.getCapacidadeTotal());
    }

    @Test
    @DisplayName("UC04: alteracao valida registra justificativa no historico")
    void alteraCapacidadeComJustificativa() {
        Pacote pacote = controller.cadastrar(dadosValidos()).getDado().orElseThrow();

        Resultado<PacoteComVagas> resultado =
                controller.alterarCapacidade(pacote.getId(), "35", "Contratacao de onibus extra", "Ana", 0);

        assertTrue(resultado.isSucesso());
        assertEquals(35, pacote.getCapacidadeTotal());
        assertEquals(1, pacote.getHistoricoCapacidade().size());
        assertEquals(30, pacote.getHistoricoCapacidade().get(0).getCapacidadeAnterior());
        assertFalse(pacote.getHistoricoCapacidade().get(0).getJustificativa().isBlank());
    }

    private void registrarReserva(Pacote pacote, int viajantes) {
        Cliente cliente = new Cliente("Cliente Teste", "52998224725", "87999990000");
        clienteRepository.salvar(cliente);
        ReservaController reservaController =
                new ReservaController(reservaRepository, pacoteRepository, clienteRepository,
                        new InMemoryPagamentoRepository(), controller);
        Resultado<?> resultado = reservaController.registrar(new DadosReserva(
                cliente.getId(), pacote.getId(), String.valueOf(viajantes),
                Formatadores.formatarData(pacote.getDataInicio()),
                Formatadores.formatarData(pacote.getDataFim()), null));
        assertTrue(resultado.isSucesso(), "reserva de apoio deveria ser criada");
    }
}
