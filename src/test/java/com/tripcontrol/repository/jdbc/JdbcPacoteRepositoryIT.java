package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.AlteracaoCapacidade;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.repository.RepositorioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Persistencia de pacotes (UC01 e UC04). */
class JdbcPacoteRepositoryIT extends RepositorioJdbcIT {

    private final JdbcPacoteRepository repositorio = new JdbcPacoteRepository();

    private Pacote novoPacote(String destino) {
        Pacote pacote = new Pacote(destino, LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 12),
                "Roteiro cultural", new BigDecimal("3490.00"), 30, "Dia 1: chegada");
        pacote.setCodigo(repositorio.proximoCodigo());
        return pacote;
    }

    @Test
    @DisplayName("Pacote gravado volta do banco com todos os campos")
    void gravaERecuperaTodosOsCampos() {
        Pacote gravado = repositorio.salvar(novoPacote("Gramado & Canela, RS"));
        assertNotNull(gravado.getId());

        Pacote lido = repositorio.buscarPorId(gravado.getId()).orElseThrow();
        assertEquals(gravado.getCodigo(), lido.getCodigo());
        assertEquals("Gramado & Canela, RS", lido.getDestino());
        assertEquals(LocalDate.of(2026, 5, 5), lido.getDataInicio());
        assertEquals(LocalDate.of(2026, 5, 12), lido.getDataFim());
        assertEquals(0, new BigDecimal("3490.00").compareTo(lido.getPreco()));
        assertEquals(30, lido.getCapacidadeTotal());
        assertEquals("Dia 1: chegada", lido.getRoteiroPrevisto());
        assertNotNull(lido.getDataCadastro());
    }

    @Test
    @DisplayName("O codigo de negocio segue o formato PC-000 e nao se repete")
    void geraCodigosUnicos() {
        String primeiro = repositorio.proximoCodigo();
        String segundo = repositorio.proximoCodigo();

        assertTrue(primeiro.matches("PC-\\d{3,}"), "formato inesperado: " + primeiro);
        assertTrue(!primeiro.equals(segundo));
    }

    @Test
    @DisplayName("Pacote sem descricao e sem roteiro e aceito (campos opcionais no UC01)")
    void aceitaCamposOpcionaisVazios() {
        Pacote pacote = new Pacote("Recife, PE", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 8),
                null, new BigDecimal("1800.00"), 12, null);
        pacote.setCodigo(repositorio.proximoCodigo());

        Pacote gravado = repositorio.salvar(pacote);

        assertNotNull(repositorio.buscarPorId(gravado.getId()).orElseThrow().getId());
    }

    @Test
    @DisplayName("Busca por destino e periodo sustenta o FA03 do UC01")
    void buscaPorDestinoEPeriodo() {
        Pacote gravado = repositorio.salvar(novoPacote("Santiago, Chile"));

        assertTrue(repositorio.buscarPorDestinoEPeriodo("  santiago, chile  ",
                LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 12)).isPresent());
        assertEquals(gravado.getId(), repositorio.buscarPorDestinoEPeriodo("Santiago, Chile",
                LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 12)).orElseThrow().getId());
    }

    @Test
    @DisplayName("O banco tambem barra destino e periodo repetidos")
    void recusaDuplicidadeNoBanco() {
        repositorio.salvar(novoPacote("Fernando de Noronha, PE"));
        Pacote repetido = novoPacote("Fernando de Noronha, PE");

        RepositorioException erro = assertThrows(RepositorioException.class, () -> repositorio.salvar(repetido));
        assertTrue(erro.isViolacaoDeUnicidade());
    }

    @Test
    @DisplayName("Busca livre encontra por codigo, destino e data")
    void buscaPorTermo() {
        Pacote gravado = repositorio.salvar(novoPacote("Buenos Aires, Argentina"));

        assertTrue(contemId(repositorio.buscarPorTermo(gravado.getCodigo()), gravado.getId()));
        assertTrue(contemId(repositorio.buscarPorTermo("buenos"), gravado.getId()));
        assertTrue(contemId(repositorio.buscarPorTermo("05/05/2026"), gravado.getId()));
    }

    @Test
    @DisplayName("Alteracao de capacidade grava o historico com anterior, nova e responsavel")
    void gravaHistoricoDeCapacidade() {
        Pacote pacote = repositorio.salvar(novoPacote("Lisboa, Portugal"));

        pacote.registrarAlteracaoCapacidade(
                new AlteracaoCapacidade(pacote.getId(), 30, 35, "Onibus extra", "Ana Silva"));
        pacote.setCapacidadeTotal(35);
        repositorio.atualizar(pacote);

        Pacote lido = repositorio.buscarPorId(pacote.getId()).orElseThrow();
        assertEquals(35, lido.getCapacidadeTotal());
        assertEquals(1, lido.getHistoricoCapacidade().size());
        AlteracaoCapacidade alteracao = lido.getHistoricoCapacidade().get(0);
        assertEquals(30, alteracao.getCapacidadeAnterior());
        assertEquals(35, alteracao.getCapacidadeNova());
        assertEquals("Ana Silva", alteracao.getUsuarioResponsavel());
        assertEquals("Onibus extra", alteracao.getJustificativa());
    }

    private static boolean contemId(List<Pacote> pacotes, Long id) {
        return pacotes.stream().anyMatch(pacote -> id.equals(pacote.getId()));
    }
}
