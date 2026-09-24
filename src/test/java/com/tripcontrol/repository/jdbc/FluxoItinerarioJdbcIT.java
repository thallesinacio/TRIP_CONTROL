package com.tripcontrol.repository.jdbc;

import com.tripcontrol.controller.ItinerarioController;
import com.tripcontrol.controller.PacoteController;
import com.tripcontrol.controller.Resultado;
import com.tripcontrol.controller.StatusResultado;
import com.tripcontrol.controller.dto.DadosItemItinerario;
import com.tripcontrol.controller.dto.DadosRecurso;
import com.tripcontrol.controller.dto.ItemCronograma;
import com.tripcontrol.model.ItemItinerario;
import com.tripcontrol.model.Itinerario;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Recurso;
import com.tripcontrol.model.StatusItinerario;
import com.tripcontrol.model.TipoItemItinerario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UC06 de ponta a ponta sobre o PostgreSQL, pelo mesmo Controller da Etapa 5. */
class FluxoItinerarioJdbcIT extends RepositorioJdbcIT {

    private final JdbcPacoteRepository pacotes = new JdbcPacoteRepository();
    private final JdbcReservaRepository reservas = new JdbcReservaRepository();
    private final JdbcRecursoRepository recursos = new JdbcRecursoRepository();
    private final JdbcItinerarioRepository itinerarios = new JdbcItinerarioRepository();

    private ItinerarioController controller;
    private Pacote pacote;
    private Itinerario itinerario;
    private Recurso hotel;
    private Recurso voo;
    private Recurso passeio;

    @BeforeEach
    void prepararPacote() {
        controller = montarController();

        Pacote novo = new Pacote("Gramado " + System.nanoTime(),
                LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 12),
                "Roteiro cultural", new BigDecimal("3490.00"), 30, null);
        novo.setCodigo(pacotes.proximoCodigo());
        pacote = pacotes.salvar(novo);

        String sufixo = String.valueOf(System.nanoTime());
        hotel = cadastrar(TipoItemItinerario.HOSPEDAGEM, "Hotel Laghetto " + sufixo);
        voo = cadastrar(TipoItemItinerario.TRANSPORTE, "Voo G3 1402 " + sufixo);
        passeio = cadastrar(TipoItemItinerario.ATIVIDADE, "Tour Maria Fumaca " + sufixo);

        itinerario = controller.abrir(pacote.getId()).getDado().orElseThrow();
    }

    private ItinerarioController montarController() {
        return new ItinerarioController(itinerarios, recursos, pacotes,
                new PacoteController(pacotes, reservas));
    }

    private Recurso cadastrar(TipoItemItinerario tipo, String nome) {
        return controller.cadastrarRecurso(new DadosRecurso(tipo, nome, "Centro", null, null))
                .getDado().orElseThrow();
    }

    private Resultado<ItemItinerario> adicionarTransporte() {
        return controller.adicionarItem(itinerario, pacote, new DadosItemItinerario(
                TipoItemItinerario.TRANSPORTE, voo.getId(),
                "05/05/2026", "08:30", "05/05/2026", "10:15",
                "Aeroporto GRU", "Aeroporto POA", "Bagagem despachada incluida"));
    }

    private Resultado<ItemItinerario> adicionarHospedagem() {
        return controller.adicionarItem(itinerario, pacote, new DadosItemItinerario(
                TipoItemItinerario.HOSPEDAGEM, hotel.getId(),
                "05/05/2026", "14:00", "12/05/2026", "12:00", null, null, null));
    }

    private Resultado<ItemItinerario> adicionarAtividade() {
        return controller.adicionarItem(itinerario, pacote, new DadosItemItinerario(
                TipoItemItinerario.ATIVIDADE, passeio.getId(),
                "06/05/2026", "09:00", null, "17:00", null, "Recepcao do Hotel", null));
    }

    @Test
    @DisplayName("Cronograma com os tres tipos e gravado e reabre em ordem cronologica")
    void montaEReabreOCronograma() {
        assertTrue(adicionarAtividade().isSucesso());
        assertTrue(adicionarTransporte().isSucesso());
        assertTrue(adicionarHospedagem().isSucesso());

        // antes de finalizar, tudo e rascunho e nada foi ao banco
        assertTrue(controller.cronograma(itinerario).stream().allMatch(ItemCronograma::rascunho));
        assertTrue(itinerarios.buscarPorPacote(pacote.getId()).isEmpty());

        assertTrue(controller.finalizar(itinerario).isSucesso());

        // instancia nova: tudo vem do banco
        Itinerario reaberto = montarController().abrir(pacote.getId()).getDado().orElseThrow();
        assertEquals(StatusItinerario.FINALIZADO, reaberto.getStatus());
        List<ItemCronograma> cronograma = controller.cronograma(reaberto);
        assertEquals(3, cronograma.size());
        assertEquals(TipoItemItinerario.TRANSPORTE, cronograma.get(0).item().getTipo());
        assertEquals(TipoItemItinerario.HOSPEDAGEM, cronograma.get(1).item().getTipo());
        assertEquals(TipoItemItinerario.ATIVIDADE, cronograma.get(2).item().getTipo());
        assertTrue(cronograma.stream().noneMatch(ItemCronograma::rascunho));
    }

    @Test
    @DisplayName("Os detalhes do cartao sobrevivem a ida e volta ao banco")
    void detalhesSobrevivemAoBanco() {
        adicionarTransporte();
        adicionarHospedagem();
        controller.finalizar(itinerario);

        List<ItemCronograma> cronograma = controller.cronograma(
                montarController().abrir(pacote.getId()).getDado().orElseThrow());

        assertTrue(cronograma.get(0).detalhes().contains("Aeroporto POA"));
        assertEquals(voo.getNome(), cronograma.get(0).titulo());
        assertEquals("Bagagem despachada incluida",
                cronograma.get(0).item().getInstrucoesOperacionais());
        assertTrue(cronograma.get(1).detalhes().contains("Centro"),
                "o local da hospedagem vem do recurso cadastrado");
    }

    @Test
    @DisplayName("FA03: item fora do periodo do pacote nao chega ao banco")
    void fa03ForaDoPeriodo() {
        Resultado<ItemItinerario> fora = controller.adicionarItem(itinerario, pacote,
                new DadosItemItinerario(TipoItemItinerario.ATIVIDADE, passeio.getId(),
                        "20/05/2026", "09:00", null, "17:00", null, "Recepcao", null));

        assertEquals(StatusResultado.ERRO_VALIDACAO, fora.getStatus());
        assertTrue(fora.mensagemDoCampo("inicio").orElseThrow().contains("12/05/2026"));
        assertTrue(itinerarios.buscarPorPacote(pacote.getId()).isEmpty());
    }

    @Test
    @DisplayName("FA04: item sobreposto nao chega ao banco")
    void fa04Conflito() {
        adicionarAtividade();

        Resultado<ItemItinerario> conflitante = controller.adicionarItem(itinerario, pacote,
                new DadosItemItinerario(TipoItemItinerario.TRANSPORTE, voo.getId(),
                        "06/05/2026", "16:00", "06/05/2026", "18:00",
                        "Hotel", "Aeroporto", null));

        assertEquals(StatusResultado.ERRO_VALIDACAO, conflitante.getStatus());
        assertEquals(1, itinerario.getItens().size());
    }

    @Test
    @DisplayName("FA06: itinerario sem itens nao e gravado")
    void fa06SemItens() {
        Resultado<Itinerario> vazio = controller.finalizar(itinerario);

        assertEquals(StatusResultado.OPERACAO_BLOQUEADA, vazio.getStatus());
        assertTrue(itinerarios.buscarPorPacote(pacote.getId()).isEmpty());
    }

    @Test
    @DisplayName("FA07: descartar o rascunho devolve o que estava gravado")
    void fa07DescartaRascunho() {
        adicionarTransporte();
        controller.finalizar(itinerario);

        adicionarAtividade();
        assertEquals(2, itinerario.getItens().size());

        Itinerario aposDescarte = controller.descartarRascunho(pacote.getId());
        assertEquals(1, aposDescarte.getItens().size());
        assertFalse(controller.cronograma(aposDescarte).get(0).rascunho());
    }

    @Test
    @DisplayName("Regravar o cronograma nao duplica itens no banco")
    void regravarNaoDuplica() {
        adicionarTransporte();
        controller.finalizar(itinerario);

        adicionarAtividade();
        controller.finalizar(itinerario);

        assertEquals(2, itinerarios.buscarPorPacote(pacote.getId()).orElseThrow().getItens().size());
    }

    @Test
    @DisplayName("O banco recusa um recurso de tipo incompativel com a tabela do item")
    void bancoGaranteOTipoDoRecurso() {
        // a integridade e garantida pela coluna gerada + chave estrangeira composta:
        // uma linha de Hospedagem so aceita recurso do tipo HOSPEDAGEM
        Itinerario forcado = new Itinerario(pacote.getId());
        ItemItinerario item = new ItemItinerario(TipoItemItinerario.HOSPEDAGEM, voo.getNome(),
                java.time.LocalDateTime.of(2026, 5, 5, 14, 0),
                java.time.LocalDateTime.of(2026, 5, 12, 12, 0));
        item.setRecursoId(voo.getId());
        forcado.adicionarItem(item);
        forcado.finalizar();

        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> itinerarios.salvar(forcado)).getMessage().toLowerCase().contains("itinerario"));
    }
}
