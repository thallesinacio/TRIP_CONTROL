package com.tripcontrol.repository.jdbc;

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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Itinerario do UC06 sobre Hospedagem, Transporte, Atividade e
 * {@code Pacote.itinerarioFinalizadoEm}.
 */
class JdbcItinerarioRepositoryIT extends RepositorioJdbcIT {

    private final JdbcPacoteRepository pacotes = new JdbcPacoteRepository();
    private final JdbcRecursoRepository recursos = new JdbcRecursoRepository();
    private final JdbcItinerarioRepository itinerarios = new JdbcItinerarioRepository();

    private Pacote pacote;
    private Recurso hotel;
    private Recurso voo;
    private Recurso passeio;

    @BeforeEach
    void prepararPacoteERecursos() {
        Pacote novo = new Pacote("Gramado " + System.nanoTime(),
                LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 12),
                "Roteiro cultural", new BigDecimal("3490.00"), 30, null);
        novo.setCodigo(pacotes.proximoCodigo());
        pacote = pacotes.salvar(novo);

        String sufixo = String.valueOf(System.nanoTime());
        hotel = recursos.salvar(new Recurso(TipoItemItinerario.HOSPEDAGEM,
                "Hotel Laghetto " + sufixo, "Av. Borges de Medeiros, 1200"));
        voo = recursos.salvar(new Recurso(TipoItemItinerario.TRANSPORTE,
                "Voo G3 1402 " + sufixo, "Guarulhos-GRU"));
        passeio = recursos.salvar(new Recurso(TipoItemItinerario.ATIVIDADE,
                "Tour Maria Fumaca " + sufixo, "Bento Goncalves"));
    }

    private Itinerario montarCronogramaCompleto() {
        Itinerario itinerario = new Itinerario(pacote.getId());

        ItemItinerario transporte = new ItemItinerario(TipoItemItinerario.TRANSPORTE,
                voo.getNome(), LocalDateTime.of(2026, 5, 5, 8, 30), LocalDateTime.of(2026, 5, 5, 10, 15));
        transporte.setRecursoId(voo.getId());
        transporte.setLocalOrigem("Aeroporto GRU");
        transporte.setLocalDestino("Aeroporto POA");
        transporte.setInstrucoesOperacionais("Bagagem despachada incluida");

        ItemItinerario hospedagem = new ItemItinerario(TipoItemItinerario.HOSPEDAGEM,
                hotel.getNome(), LocalDateTime.of(2026, 5, 5, 14, 0), LocalDateTime.of(2026, 5, 12, 12, 0));
        hospedagem.setRecursoId(hotel.getId());

        ItemItinerario atividade = new ItemItinerario(TipoItemItinerario.ATIVIDADE,
                passeio.getNome(), LocalDateTime.of(2026, 5, 6, 9, 0), LocalDateTime.of(2026, 5, 6, 17, 0));
        atividade.setRecursoId(passeio.getId());
        atividade.setLocalDestino("Recepcao do Hotel");

        itinerario.adicionarItem(transporte);
        itinerario.adicionarItem(hospedagem);
        itinerario.adicionarItem(atividade);
        itinerario.finalizar();
        return itinerarios.salvar(itinerario);
    }

    @Test
    @DisplayName("Itinerario finalizado reaparece ao reabrir o pacote, em ordem cronologica")
    void gravaEReabre() {
        montarCronogramaCompleto();

        Itinerario lido = itinerarios.buscarPorPacote(pacote.getId()).orElseThrow();
        assertEquals(StatusItinerario.FINALIZADO, lido.getStatus());
        assertNotNull(lido.getDataFinalizacao());
        assertEquals(3, lido.getItens().size());

        List<ItemItinerario> itens = lido.getItens();
        assertEquals(TipoItemItinerario.TRANSPORTE, itens.get(0).getTipo());
        assertEquals(TipoItemItinerario.HOSPEDAGEM, itens.get(1).getTipo());
        assertEquals(TipoItemItinerario.ATIVIDADE, itens.get(2).getTipo());
    }

    @Test
    @DisplayName("Cada tipo volta com os campos proprios e com o nome vindo do recurso")
    void camposPorTipo() {
        montarCronogramaCompleto();
        List<ItemItinerario> itens = itinerarios.buscarPorPacote(pacote.getId()).orElseThrow().getItens();

        ItemItinerario transporte = itens.get(0);
        assertEquals(voo.getNome(), transporte.getNomeServico());
        assertEquals("Aeroporto GRU", transporte.getLocalOrigem());
        assertEquals("Aeroporto POA", transporte.getLocalDestino());
        assertEquals("Bagagem despachada incluida", transporte.getInstrucoesOperacionais());
        assertEquals(LocalDateTime.of(2026, 5, 5, 8, 30), transporte.getInicio());

        ItemItinerario hospedagem = itens.get(1);
        assertEquals(hotel.getNome(), hospedagem.getNomeServico());
        assertEquals(hotel.getId(), hospedagem.getRecursoId());
        assertEquals(LocalDateTime.of(2026, 5, 12, 12, 0), hospedagem.getFim());

        // a atividade cabe num dia: a tabela guarda data + dois horarios
        ItemItinerario atividade = itens.get(2);
        assertEquals(LocalDateTime.of(2026, 5, 6, 9, 0), atividade.getInicio());
        assertEquals(LocalDateTime.of(2026, 5, 6, 17, 0), atividade.getFim());
        assertEquals("Recepcao do Hotel", atividade.getLocalDestino());
    }

    @Test
    @DisplayName("Pacote sem itinerario finalizado nao devolve itinerario")
    void pacoteSemItinerarioNaoDevolveNada() {
        assertTrue(itinerarios.buscarPorPacote(pacote.getId()).isEmpty());
    }

    @Test
    @DisplayName("Ids de itens de tabelas diferentes nao colidem")
    void idsDeItensSaoUnicos() {
        montarCronogramaCompleto();
        List<ItemItinerario> itens = itinerarios.buscarPorPacote(pacote.getId()).orElseThrow().getItens();

        long distintos = itens.stream().map(ItemItinerario::getId).distinct().count();
        assertEquals(itens.size(), distintos);
        assertTrue(itens.stream().allMatch(item -> item.getItinerarioId() != null));
    }

    @Test
    @DisplayName("Regravar substitui o cronograma inteiro, sem duplicar itens")
    void regravarSubstituiOCronograma() {
        Itinerario itinerario = montarCronogramaCompleto();

        ItemItinerario extra = new ItemItinerario(TipoItemItinerario.ATIVIDADE,
                passeio.getNome(), LocalDateTime.of(2026, 5, 7, 9, 0), LocalDateTime.of(2026, 5, 7, 12, 0));
        extra.setRecursoId(passeio.getId());
        extra.setLocalDestino("Praca central");
        itinerario.adicionarItem(extra);

        itinerarios.atualizar(itinerario);

        assertEquals(4, itinerarios.buscarPorPacote(pacote.getId()).orElseThrow().getItens().size());
    }

    @Test
    @DisplayName("Remover apaga os itens e devolve o pacote ao estado de rascunho")
    void removerLimpaOItinerario() {
        Itinerario itinerario = montarCronogramaCompleto();

        assertTrue(itinerarios.remover(itinerario.getId()));
        assertTrue(itinerarios.buscarPorPacote(pacote.getId()).isEmpty());
    }

    @Test
    @DisplayName("buscarTodos lista apenas pacotes com itinerario finalizado")
    void listaApenasFinalizados() {
        montarCronogramaCompleto();

        assertTrue(itinerarios.buscarTodos().stream()
                .anyMatch(i -> pacote.getId().equals(i.getPacoteId())));
        assertTrue(itinerarios.contar() >= 1);
    }
}
