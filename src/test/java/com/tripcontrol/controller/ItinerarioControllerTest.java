package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.DadosItemItinerario;
import com.tripcontrol.controller.dto.DadosPacote;
import com.tripcontrol.controller.dto.DadosRecurso;
import com.tripcontrol.controller.dto.ItemCronograma;
import com.tripcontrol.controller.dto.PacoteComVagas;
import com.tripcontrol.model.ItemItinerario;
import com.tripcontrol.model.Itinerario;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Recurso;
import com.tripcontrol.model.StatusItinerario;
import com.tripcontrol.model.TipoItemItinerario;
import com.tripcontrol.repository.ItinerarioRepository;
import com.tripcontrol.repository.PacoteRepository;
import com.tripcontrol.repository.RecursoRepository;
import com.tripcontrol.repository.ReservaRepository;
import com.tripcontrol.repository.memory.InMemoryItinerarioRepository;
import com.tripcontrol.repository.memory.InMemoryPacoteRepository;
import com.tripcontrol.repository.memory.InMemoryRecursoRepository;
import com.tripcontrol.repository.memory.InMemoryReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre o fluxo principal e os fluxos alternativos do UC06 (Montar Itinerario),
 * incluindo o cadastro de recursos da decisao B da equipe.
 *
 * <p>O FA07 aparece aqui na forma que chega ao Controller: a confirmacao e da tela,
 * o descarte do rascunho e {@code descartarRascunho}.</p>
 */
class ItinerarioControllerTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 12);

    private ItinerarioRepository itinerarioRepository;
    private RecursoRepository recursoRepository;
    private PacoteRepository pacoteRepository;
    private ReservaRepository reservaRepository;
    private ItinerarioController controller;

    private Pacote pacote;
    private Itinerario itinerario;
    private Recurso hotel;
    private Recurso voo;
    private Recurso passeio;

    @BeforeEach
    void prepararCenario() {
        itinerarioRepository = new InMemoryItinerarioRepository();
        recursoRepository = new InMemoryRecursoRepository();
        pacoteRepository = new InMemoryPacoteRepository();
        reservaRepository = new InMemoryReservaRepository();

        Clock relogioFixo = Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneId.of("UTC"));
        PacoteController pacoteController =
                new PacoteController(pacoteRepository, reservaRepository, relogioFixo);
        controller = new ItinerarioController(itinerarioRepository, recursoRepository,
                pacoteRepository, pacoteController);

        pacote = pacoteController.cadastrar(new DadosPacote("Gramado & Canela, RS",
                "05/05/2026", "12/05/2026", "Roteiro cultural",
                "3490,00", "30", null)).getDado().orElseThrow();

        itinerario = controller.abrir(pacote.getId()).getDado().orElseThrow();

        hotel = cadastrarRecurso(TipoItemItinerario.HOSPEDAGEM, "Hotel Laghetto Premio Gramado",
                "Av. Borges de Medeiros, 1200");
        voo = cadastrarRecurso(TipoItemItinerario.TRANSPORTE, "Voo de Ida - G3 1402", "Guarulhos-GRU");
        passeio = cadastrarRecurso(TipoItemItinerario.ATIVIDADE, "Tour de Maria Fumaca e Vinicola",
                "Centro de Bento Goncalves");
    }

    private Recurso cadastrarRecurso(TipoItemItinerario tipo, String nome, String local) {
        return controller.cadastrarRecurso(new DadosRecurso(tipo, nome, local, null, null))
                .getDado().orElseThrow();
    }

    private DadosItemItinerario hospedagem(String checkin, String horaCheckin,
                                           String checkout, String horaCheckout) {
        return new DadosItemItinerario(TipoItemItinerario.HOSPEDAGEM, hotel.getId(),
                checkin, horaCheckin, checkout, horaCheckout, null, null, null);
    }

    private DadosItemItinerario transporte(String dataSaida, String horaSaida,
                                           String dataChegada, String horaChegada) {
        return new DadosItemItinerario(TipoItemItinerario.TRANSPORTE, voo.getId(),
                dataSaida, horaSaida, dataChegada, horaChegada,
                "Aeroporto GRU", "Aeroporto POA", "Bagagem despachada incluida");
    }

    private DadosItemItinerario atividade(String data, String horaInicio, String horaFim) {
        return new DadosItemItinerario(TipoItemItinerario.ATIVIDADE, passeio.getId(),
                data, horaInicio, null, horaFim, null, "Recepcao do Hotel", null);
    }

    // ------------------------------------------------------------------
    // Pesquisa do pacote (UC06, passos 2 e 3)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Busca por codigo e por destino localiza o pacote com as vagas restantes")
    void buscaPacotePorTermo() {
        Resultado<List<PacoteComVagas>> porCodigo = controller.buscarPacotes(pacote.getCodigo());
        assertTrue(porCodigo.isSucesso());
        assertEquals(30, porCodigo.getDado().orElseThrow().get(0).vagasDisponiveis());

        assertTrue(controller.buscarPacotes("Gramado").isSucesso());
    }

    @Test
    @DisplayName("FA01: nenhum pacote encontrado orienta a revisar ou cadastrar")
    void fa01PacoteNaoEncontrado() {
        Resultado<List<PacoteComVagas>> resultado = controller.buscarPacotes("PC-999");

        assertEquals(StatusResultado.NAO_ENCONTRADO, resultado.getStatus());
        assertTrue(resultado.getMensagem().contains("cadastre o pacote"));
        assertEquals(StatusResultado.ERRO_VALIDACAO, controller.buscarPacotes("  ").getStatus());
    }

    // ------------------------------------------------------------------
    // Cadastro de recursos (decisao B; FA02)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("FA02: o cadastro de recurso alimenta a lista do tipo escolhido")
    void fa02CadastroDeRecurso() {
        assertEquals(1, controller.listarRecursos(TipoItemItinerario.HOSPEDAGEM).size());

        Resultado<Recurso> novo = controller.cadastrarRecurso(new DadosRecurso(
                TipoItemItinerario.HOSPEDAGEM, "Pousada Vale dos Pinheiros", "Rua das Hortensias", null, null));

        assertTrue(novo.isSucesso());
        assertEquals(2, controller.listarRecursos(TipoItemItinerario.HOSPEDAGEM).size());
        assertEquals(1, controller.listarRecursos(TipoItemItinerario.TRANSPORTE).size());
    }

    @Test
    @DisplayName("Recurso sem nome e recurso repetido no mesmo tipo sao recusados")
    void recusaRecursoInvalidoOuRepetido() {
        Resultado<Recurso> semNome = controller.cadastrarRecurso(
                new DadosRecurso(TipoItemItinerario.ATIVIDADE, "   ", null, null, null));
        assertEquals(StatusResultado.ERRO_VALIDACAO, semNome.getStatus());
        assertTrue(semNome.mensagemDoCampo("nome").isPresent());

        Resultado<Recurso> repetido = controller.cadastrarRecurso(new DadosRecurso(
                TipoItemItinerario.HOSPEDAGEM, "hotel laghetto premio gramado", null, null, null));
        assertEquals(StatusResultado.DUPLICIDADE, repetido.getStatus());
        assertEquals(hotel.getId(), repetido.getDado().orElseThrow().getId());
    }

    // ------------------------------------------------------------------
    // Fluxo principal (UC06, passos 5 a 11)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Fluxo principal: hospedagem, transporte e atividade entram em ordem cronologica")
    void montaCronogramaCompleto() {
        assertTrue(controller.adicionarItem(itinerario, pacote,
                atividade("06/05/2026", "09:00", "17:00")).isSucesso());
        assertTrue(controller.adicionarItem(itinerario, pacote,
                transporte("05/05/2026", "08:30", "05/05/2026", "10:15")).isSucesso());
        assertTrue(controller.adicionarItem(itinerario, pacote,
                hospedagem("05/05/2026", "14:00", "12/05/2026", "12:00")).isSucesso());

        List<ItemCronograma> cronograma = controller.cronograma(itinerario);
        assertEquals(3, cronograma.size());
        assertEquals(TipoItemItinerario.TRANSPORTE, cronograma.get(0).item().getTipo());
        assertEquals(TipoItemItinerario.HOSPEDAGEM, cronograma.get(1).item().getTipo());
        assertEquals(TipoItemItinerario.ATIVIDADE, cronograma.get(2).item().getTipo());

        // enquanto nao finaliza, tudo e rascunho
        assertTrue(cronograma.stream().allMatch(ItemCronograma::rascunho));
    }

    @Test
    @DisplayName("Finalizar grava o itinerario e ele reaparece ao reabrir o pacote")
    void finalizaEReabreOItinerario() {
        controller.adicionarItem(itinerario, pacote,
                transporte("05/05/2026", "08:30", "05/05/2026", "10:15"));

        Resultado<Itinerario> resultado = controller.finalizar(itinerario);

        assertTrue(resultado.isSucesso());
        assertEquals(StatusItinerario.FINALIZADO, itinerario.getStatus());
        assertNotNull(itinerario.getDataFinalizacao());

        Itinerario reaberto = controller.abrir(pacote.getId()).getDado().orElseThrow();
        assertEquals(1, reaberto.getItens().size());
        assertFalse(controller.cronograma(reaberto).get(0).rascunho(),
                "depois de finalizado o item deixa de ser rascunho");
    }

    @Test
    @DisplayName("Os detalhes do cartao usam os rotulos proprios de cada tipo")
    void detalhesPorTipo() {
        controller.adicionarItem(itinerario, pacote, hospedagem("05/05/2026", "14:00", "12/05/2026", "12:00"));
        controller.adicionarItem(itinerario, pacote, transporte("05/05/2026", "08:30", "05/05/2026", "10:15"));
        controller.adicionarItem(itinerario, pacote, atividade("06/05/2026", "09:00", "17:00"));

        List<ItemCronograma> cronograma = controller.cronograma(itinerario);
        assertTrue(cronograma.get(0).detalhes().startsWith("Saída:"));
        assertTrue(cronograma.get(0).detalhes().contains("Aeroporto POA"));
        assertTrue(cronograma.get(1).detalhes().startsWith("Check-in:"));
        assertTrue(cronograma.get(1).detalhes().contains("Av. Borges de Medeiros"));
        assertTrue(cronograma.get(2).detalhes().contains("Local de Encontro: Recepcao do Hotel"));
        assertEquals("TRANSPORTE", cronograma.get(0).tag());
    }

    // ------------------------------------------------------------------
    // Fluxos alternativos do item
    // ------------------------------------------------------------------

    @Test
    @DisplayName("FA03: item fora do periodo do pacote e recusado informando o periodo valido")
    void fa03DataForaDoPeriodo() {
        Resultado<ItemItinerario> resultado = controller.adicionarItem(itinerario, pacote,
                atividade("20/05/2026", "09:00", "17:00"));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        String mensagem = resultado.mensagemDoCampo("inicio").orElseThrow();
        assertTrue(mensagem.contains("05/05/2026"));
        assertTrue(mensagem.contains("12/05/2026"));
        assertTrue(itinerario.isVazio());
    }

    @Test
    @DisplayName("FA04: termino nao posterior ao inicio e recusado")
    void fa04IntervaloInvalido() {
        Resultado<ItemItinerario> resultado = controller.adicionarItem(itinerario, pacote,
                atividade("06/05/2026", "17:00", "09:00"));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("fim").orElseThrow().contains("posterior"));
        assertTrue(itinerario.isVazio());
    }

    @Test
    @DisplayName("FA04: item sobreposto e recusado mostrando o item conflitante")
    void fa04ConflitoDeHorario() {
        controller.adicionarItem(itinerario, pacote, atividade("06/05/2026", "09:00", "17:00"));

        Resultado<ItemItinerario> resultado = controller.adicionarItem(itinerario, pacote,
                transporte("06/05/2026", "16:00", "06/05/2026", "18:00"));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("inicio").orElseThrow()
                .contains("Tour de Maria Fumaca e Vinicola"));
        assertEquals(1, itinerario.getItens().size());
    }

    @Test
    @DisplayName("Itens encostados, sem sobreposicao, sao aceitos")
    void aceitaItensConsecutivos() {
        controller.adicionarItem(itinerario, pacote,
                transporte("05/05/2026", "08:30", "05/05/2026", "10:15"));

        assertTrue(controller.adicionarItem(itinerario, pacote,
                atividade("05/05/2026", "10:15", "12:00")).isSucesso());
        assertEquals(2, itinerario.getItens().size());
    }

    @Test
    @DisplayName("FA05: recurso e campos obrigatorios do tipo sao apontados um a um")
    void fa05CamposObrigatorios() {
        Resultado<ItemItinerario> semRecurso = controller.adicionarItem(itinerario, pacote,
                new DadosItemItinerario(TipoItemItinerario.TRANSPORTE, null,
                        "05/05/2026", "08:30", "05/05/2026", "10:15", null, null, null));

        assertEquals(StatusResultado.ERRO_VALIDACAO, semRecurso.getStatus());
        assertTrue(semRecurso.mensagemDoCampo("recurso").orElseThrow().contains("Cadastrar novo"));
        assertTrue(semRecurso.mensagemDoCampo("localOrigem").isPresent());
        assertTrue(semRecurso.mensagemDoCampo("localDestino").isPresent());

        Resultado<ItemItinerario> semHorario = controller.adicionarItem(itinerario, pacote,
                new DadosItemItinerario(TipoItemItinerario.HOSPEDAGEM, hotel.getId(),
                        "05/05/2026", "", "12/05/2026", "12:00", null, null, null));
        assertTrue(semHorario.mensagemDoCampo("inicio").orElseThrow().contains("check-in"));

        assertTrue(itinerario.isVazio());
    }

    @Test
    @DisplayName("Atividade usa uma unica data: o termino cai no mesmo dia do inicio")
    void atividadeTerminaNoMesmoDia() {
        controller.adicionarItem(itinerario, pacote, atividade("06/05/2026", "09:00", "17:00"));

        ItemItinerario item = itinerario.getItens().get(0);
        assertEquals(item.getInicio().toLocalDate(), item.getFim().toLocalDate());
    }

    // ------------------------------------------------------------------
    // Finalizacao e cancelamento
    // ------------------------------------------------------------------

    @Test
    @DisplayName("FA06: itinerario sem itens nao pode ser finalizado")
    void fa06ItinerarioSemItens() {
        Resultado<Itinerario> resultado = controller.finalizar(itinerario);

        assertEquals(StatusResultado.OPERACAO_BLOQUEADA, resultado.getStatus());
        assertTrue(resultado.getMensagem().contains("ao menos um item"));
        assertEquals(StatusItinerario.RASCUNHO, itinerario.getStatus());
        assertEquals(0, itinerarioRepository.contar());
    }

    @Test
    @DisplayName("FA07: descartar a elaboracao devolve o ultimo itinerario gravado")
    void fa07DescartaRascunho() {
        controller.adicionarItem(itinerario, pacote,
                transporte("05/05/2026", "08:30", "05/05/2026", "10:15"));
        controller.finalizar(itinerario);

        // novos itens entram apenas no rascunho em memoria
        controller.adicionarItem(itinerario, pacote, atividade("06/05/2026", "09:00", "17:00"));
        assertEquals(2, itinerario.getItens().size());

        Itinerario aposDescarte = controller.descartarRascunho(pacote.getId());
        assertEquals(1, aposDescarte.getItens().size(),
                "o item nunca gravado desaparece com o descarte");
    }

    @Test
    @DisplayName("FA07: descartar sem nada gravado devolve um rascunho vazio")
    void fa07DescartaSemItinerarioGravado() {
        controller.adicionarItem(itinerario, pacote, atividade("06/05/2026", "09:00", "17:00"));

        Itinerario aposDescarte = controller.descartarRascunho(pacote.getId());

        assertTrue(aposDescarte.isVazio());
        assertEquals(0, itinerarioRepository.contar());
    }

    @Test
    @DisplayName("Falha ao gravar mantem o itinerario como rascunho")
    void desfazFinalizacaoQuandoPersistenciaFalha() {
        ItinerarioRepository repositorioComFalha = new InMemoryItinerarioRepository() {
            @Override
            public synchronized Itinerario salvar(Itinerario entidade) {
                throw new IllegalStateException("falha simulada de persistencia");
            }
        };
        ItinerarioController controllerComFalha = new ItinerarioController(repositorioComFalha,
                recursoRepository, pacoteRepository,
                new PacoteController(pacoteRepository, reservaRepository));
        controllerComFalha.adicionarItem(itinerario, pacote,
                transporte("05/05/2026", "08:30", "05/05/2026", "10:15"));

        Resultado<Itinerario> resultado = controllerComFalha.finalizar(itinerario);

        assertEquals(StatusResultado.OPERACAO_BLOQUEADA, resultado.getStatus());
        assertEquals(StatusItinerario.RASCUNHO, itinerario.getStatus());
        assertTrue(controllerComFalha.cronograma(itinerario).get(0).rascunho());
    }
}
