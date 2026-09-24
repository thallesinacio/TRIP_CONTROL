package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.Recurso;
import com.tripcontrol.model.TipoItemItinerario;
import com.tripcontrol.repository.RepositorioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Catalogo de hospedagens, transportes e atividades (UC06, pre-condicao). */
class JdbcRecursoRepositoryIT extends RepositorioJdbcIT {

    private final JdbcRecursoRepository recursos = new JdbcRecursoRepository();

    private Recurso criar(TipoItemItinerario tipo, String nome) {
        Recurso recurso = new Recurso(tipo, nome, "Rua de Teste, 100");
        recurso.setContato("(87) 99999-0000");
        recurso.setDescricao("Cadastro de teste");
        return recursos.salvar(recurso);
    }

    @Test
    @DisplayName("Recurso gravado volta do banco com todos os campos")
    void gravaERecupera() {
        Recurso gravado = criar(TipoItemItinerario.HOSPEDAGEM, "Hotel " + System.nanoTime());
        assertNotNull(gravado.getId());

        Recurso lido = recursos.buscarPorId(gravado.getId()).orElseThrow();
        assertEquals(TipoItemItinerario.HOSPEDAGEM, lido.getTipo());
        assertEquals(gravado.getNome(), lido.getNome());
        assertEquals("Rua de Teste, 100", lido.getLocal());
        assertEquals("(87) 99999-0000", lido.getContato());
    }

    @Test
    @DisplayName("A busca por tipo separa as tres listas do passo 5 do UC06")
    void separaPorTipo() {
        String sufixo = String.valueOf(System.nanoTime());
        criar(TipoItemItinerario.HOSPEDAGEM, "Pousada " + sufixo);
        criar(TipoItemItinerario.TRANSPORTE, "Voo " + sufixo);
        criar(TipoItemItinerario.ATIVIDADE, "Tour " + sufixo);

        List<Recurso> hospedagens = recursos.buscarPorTipo(TipoItemItinerario.HOSPEDAGEM);
        assertTrue(hospedagens.stream().allMatch(r -> r.getTipo() == TipoItemItinerario.HOSPEDAGEM));
        assertTrue(hospedagens.stream().anyMatch(r -> r.getNome().equals("Pousada " + sufixo)));
        assertTrue(recursos.buscarPorTipo(TipoItemItinerario.TRANSPORTE).stream()
                .noneMatch(r -> r.getNome().equals("Pousada " + sufixo)));
    }

    @Test
    @DisplayName("O banco recusa o mesmo nome repetido dentro do mesmo tipo")
    void recusaNomeRepetidoNoMesmoTipo() {
        String nome = "Hotel Duplicado " + System.nanoTime();
        criar(TipoItemItinerario.HOSPEDAGEM, nome);

        RepositorioException erro = assertThrows(RepositorioException.class,
                () -> criar(TipoItemItinerario.HOSPEDAGEM, nome));
        assertTrue(erro.isViolacaoDeUnicidade());
        // Nada mais e gravado depois daqui: o PostgreSQL aborta a transacao inteira
        // quando uma constraint e violada, e cada teste roda dentro de uma so.
    }

    @Test
    @DisplayName("O mesmo nome em tipos diferentes e permitido: sao catalogos distintos")
    void aceitaMesmoNomeEmTiposDiferentes() {
        String nome = "Recanto " + System.nanoTime();

        assertNotNull(criar(TipoItemItinerario.HOSPEDAGEM, nome).getId());
        assertNotNull(criar(TipoItemItinerario.ATIVIDADE, nome).getId());
    }

    @Test
    @DisplayName("Recurso sem tipo nao e gravado")
    void exigeTipo() {
        Recurso semTipo = new Recurso();
        semTipo.setNome("Sem tipo");
        assertThrows(RepositorioException.class, () -> recursos.salvar(semTipo));
    }
}
