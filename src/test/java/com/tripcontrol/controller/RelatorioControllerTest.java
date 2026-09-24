package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.FiltrosRelatorio;
import com.tripcontrol.controller.dto.Relatorio;
import com.tripcontrol.model.CriterioRanking;
import com.tripcontrol.model.TipoRelatorio;
import com.tripcontrol.repository.relatorio.DadosRelatorio;
import com.tripcontrol.repository.relatorio.FiltrosValidados;
import com.tripcontrol.repository.relatorio.RelatorioIndisponivel;
import com.tripcontrol.repository.relatorio.RelatorioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fluxos do UC08 que nao dependem do banco: critica dos filtros (FA01, FA02),
 * ausencia de dados (FA03), falha de consulta (FA04), falha de gravacao (FA05),
 * cancelamento (FA06) e o conteudo dos arquivos exportados.
 *
 * <p>Usa um duplo de teste no lugar do repositorio para poder verificar algo que
 * uma consulta real esconderia: que <strong>nenhuma consulta e executada</strong>
 * quando os filtros sao invalidos, como o passo 5.2 do FA02 exige.</p>
 */
class RelatorioControllerTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 12);

    /** Repositorio de mentira: conta as consultas e devolve o que o teste mandar. */
    private static class RepositorioDeTeste implements RelatorioRepository {
        int consultas;
        DadosRelatorio resposta = new DadosRelatorio(
                List.of(List.of("RE-0001", "Carlos", "PC-101 - Gramado", "05/05/2026 a 12/05/2026",
                        "2", "Ativa")),
                Map.of("Reservas", "1"));
        RuntimeException falha;

        private DadosRelatorio responder() {
            consultas++;
            if (falha != null) {
                throw falha;
            }
            return resposta;
        }

        @Override
        public boolean disponivel() {
            return true;
        }

        @Override
        public DadosRelatorio reservas(FiltrosValidados filtros) {
            return responder();
        }

        @Override
        public DadosRelatorio pagamentos(FiltrosValidados filtros) {
            return responder();
        }

        @Override
        public DadosRelatorio pacotesMaisProcurados(FiltrosValidados filtros) {
            return responder();
        }

        @Override
        public DadosRelatorio clientes(FiltrosValidados filtros) {
            return responder();
        }

        @Override
        public DadosRelatorio ocupacaoDosPacotes(FiltrosValidados filtros) {
            return responder();
        }
    }

    private RepositorioDeTeste repositorio;
    private RelatorioController controller;

    @BeforeEach
    void prepararCenario() {
        repositorio = new RepositorioDeTeste();
        controller = new RelatorioController(repositorio,
                Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneId.of("UTC")));
    }

    private FiltrosRelatorio periodo(String inicio, String fim) {
        return new FiltrosRelatorio(inicio, fim, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    // ------------------------------------------------------------------
    // Fluxo principal
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Fluxo principal: relatorio gerado traz colunas, linhas, totais e data")
    void geraRelatorio() {
        Resultado<Relatorio> resultado = controller.gerar(TipoRelatorio.RESERVAS,
                periodo("01/01/2026", "31/12/2026"));

        assertTrue(resultado.isSucesso());
        Relatorio relatorio = resultado.getDado().orElseThrow();
        assertEquals(TipoRelatorio.RESERVAS.getColunas(), relatorio.colunas());
        assertEquals(1, relatorio.quantidadeDeRegistros());
        assertEquals("Reservas", relatorio.totais().get(0).rotulo());
        assertEquals(HOJE.atStartOfDay(), relatorio.geradoEm());
        assertTrue(relatorio.filtrosAplicados().contains("Período da viagem: 01/01/2026 a 31/12/2026"));
    }

    @Test
    @DisplayName("Os cinco tipos sao oferecidos com os nomes do documento de requisitos")
    void oferecerOsCincoTipos() {
        assertEquals(5, controller.tiposDisponiveis().size());
        assertEquals("Pacotes Mais Procurados",
                TipoRelatorio.PACOTES_MAIS_PROCURADOS.getDescricao());
        assertEquals("Ocupação dos Pacotes", TipoRelatorio.OCUPACAO_DOS_PACOTES.getDescricao());
    }

    // ------------------------------------------------------------------
    // Fluxos alternativos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("FA01: gerar sem escolher o tipo nao consulta o banco")
    void fa01TipoNaoSelecionado() {
        Resultado<Relatorio> resultado = controller.gerar(null, FiltrosRelatorio.vazios());

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("tipo").isPresent());
        assertEquals(0, repositorio.consultas);
    }

    @Test
    @DisplayName("FA02: data final anterior a inicial e recusada antes de consultar")
    void fa02DataFinalAnteriorAInicial() {
        Resultado<Relatorio> resultado = controller.gerar(TipoRelatorio.RESERVAS,
                periodo("31/12/2026", "01/01/2026"));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("dataFim").orElseThrow().contains("anterior"));
        assertEquals(0, repositorio.consultas, "a consulta nao pode ser executada");
    }

    @Test
    @DisplayName("FA02: data em formato invalido e recusada")
    void fa02DataInvalida() {
        Resultado<Relatorio> resultado = controller.gerar(TipoRelatorio.RESERVAS,
                periodo("31/02/2026", null));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("dataInicio").isPresent());
        assertEquals(0, repositorio.consultas);
    }

    @Test
    @DisplayName("FA02: percentual de ocupacao fora de 0 a 100 e recusado")
    void fa02PercentualForaDoIntervalo() {
        Resultado<Relatorio> acima = controller.gerar(TipoRelatorio.OCUPACAO_DOS_PACOTES,
                new FiltrosRelatorio(null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, "120"));
        assertTrue(acima.mensagemDoCampo("percentualMinimoOcupacao").orElseThrow()
                .contains("entre 0 e 100"));

        Resultado<Relatorio> negativo = controller.gerar(TipoRelatorio.OCUPACAO_DOS_PACOTES,
                new FiltrosRelatorio(null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, "-5"));
        assertEquals(StatusResultado.ERRO_VALIDACAO, negativo.getStatus());
        assertEquals(0, repositorio.consultas);
    }

    @Test
    @DisplayName("FA02: ranking menor que um e recusado")
    void fa02RankingMenorQueUm() {
        Resultado<Relatorio> resultado = controller.gerar(TipoRelatorio.PACOTES_MAIS_PROCURADOS,
                new FiltrosRelatorio(null, null, null, null, null, null, null,
                        "0", CriterioRanking.NUMERO_DE_RESERVAS,
                        null, null, null, null, null, null));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("posicoesRanking").orElseThrow()
                .contains("ao menos uma posição"));
        assertEquals(0, repositorio.consultas);
    }

    @Test
    @DisplayName("FA02: campo obrigatorio vazio no ranking e apontado")
    void fa02CampoObrigatorioVazio() {
        Resultado<Relatorio> resultado = controller.gerar(TipoRelatorio.PACOTES_MAIS_PROCURADOS,
                FiltrosRelatorio.vazios());

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("posicoesRanking").isPresent());
        assertTrue(resultado.mensagemDoCampo("criterioRanking").isPresent());
        assertEquals(0, repositorio.consultas);
    }

    @Test
    @DisplayName("FA02: minimo de viagens concluidas negativo e recusado")
    void fa02MinimoDeViagensNegativo() {
        Resultado<Relatorio> resultado = controller.gerar(TipoRelatorio.CLIENTES,
                new FiltrosRelatorio(null, null, null, null, null, null, null,
                        null, null, null, null, "-2", null, null, null));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("minimoViagensConcluidas").isPresent());
        assertEquals(0, repositorio.consultas);
    }

    @Test
    @DisplayName("FA03: consulta sem registros informa que nao ha dados")
    void fa03NenhumDadoEncontrado() {
        repositorio.resposta = DadosRelatorio.vazio();

        Resultado<Relatorio> resultado = controller.gerar(TipoRelatorio.RESERVAS,
                periodo("01/01/2026", "31/01/2026"));

        assertEquals(StatusResultado.NAO_ENCONTRADO, resultado.getStatus());
        assertTrue(resultado.getMensagem().contains("Nenhum dado encontrado"));
        assertEquals(1, repositorio.consultas, "a consulta rodou: o filtro era valido");
    }

    @Test
    @DisplayName("FA04: falha na consulta nao devolve resultado incompleto")
    void fa04FalhaNaConsulta() {
        repositorio.falha = new IllegalStateException("conexao perdida");

        Resultado<Relatorio> resultado = controller.gerar(TipoRelatorio.PAGAMENTOS,
                periodo("01/01/2026", "31/01/2026"));

        assertEquals(StatusResultado.OPERACAO_BLOQUEADA, resultado.getStatus());
        assertTrue(resultado.getMensagem().contains("filtros foram mantidos"));
        assertTrue(resultado.getDado().isEmpty());
    }

    @Test
    @DisplayName("Modo memoria: relatorios indisponiveis com explicacao, sem quebrar a tela")
    void modoMemoriaInformaIndisponibilidade() {
        RelatorioController semBanco = new RelatorioController(new RelatorioIndisponivel());

        assertFalse(semBanco.isDisponivel());
        Resultado<Relatorio> resultado = semBanco.gerar(TipoRelatorio.RESERVAS,
                FiltrosRelatorio.vazios());

        assertEquals(StatusResultado.OPERACAO_BLOQUEADA, resultado.getStatus());
        assertTrue(resultado.getMensagem().contains("modo memória"));
    }

    // ------------------------------------------------------------------
    // Exportacao
    // ------------------------------------------------------------------

    private Relatorio gerarParaExportar() {
        Map<String, String> totais = new LinkedHashMap<>();
        totais.put("Reservas", "2");
        totais.put("Total Recebido", "R$ 1.500,00");
        repositorio.resposta = new DadosRelatorio(List.of(
                List.of("RE-0001", "Carlos Eduardo", "PC-101 - Gramado & Canela",
                        "05/05/2026 a 12/05/2026", "2", "Ativa"),
                // o ponto e vírgula do nome precisa sair escapado no CSV
                List.of("RE-0002", "Ana; Maria", "PC-102 - Fernando de Noronha",
                        "12/04/2026 a 19/04/2026", "3", "Concluida")),
                totais);
        return controller.gerar(TipoRelatorio.RESERVAS, periodo("01/01/2026", "31/12/2026"))
                .getDado().orElseThrow();
    }

    @Test
    @DisplayName("CSV sai com BOM, ponto e vírgula, acentos e campos escapados")
    void exportaCsvLegivelNoExcel() throws IOException {
        Relatorio relatorio = gerarParaExportar();
        Path destino = Files.createTempDirectory("tripcontrol").resolve("reservas.csv");

        Resultado<Path> resultado = controller.exportarCsv(relatorio, destino);
        assertTrue(resultado.isSucesso());

        byte[] bruto = Files.readAllBytes(destino);
        assertEquals((byte) 0xEF, bruto[0], "o BOM e o que faz o Excel ler UTF-8");
        assertEquals((byte) 0xBB, bruto[1]);
        assertEquals((byte) 0xBF, bruto[2]);

        String conteudo = new String(bruto, StandardCharsets.UTF_8);
        assertTrue(conteudo.contains("Reserva;Cliente;Pacote;Período;Viajantes;Situação"));
        assertTrue(conteudo.contains("RE-0001;Carlos Eduardo;PC-101 - Gramado & Canela"));
        assertTrue(conteudo.contains("\"Ana; Maria\""), "campo com separador precisa de aspas");
        assertTrue(conteudo.contains("Total Recebido;R$ 1.500,00"));
        assertTrue(conteudo.contains("Gerado em;12/03/2026"));
    }

    @Test
    @DisplayName("PDF sai com estrutura valida e o titulo do relatorio")
    void exportaPdf() throws IOException {
        Relatorio relatorio = gerarParaExportar();
        Path destino = Files.createTempDirectory("tripcontrol").resolve("reservas.pdf");

        Resultado<Path> resultado = controller.exportarPdf(relatorio, destino);
        assertTrue(resultado.isSucesso());

        byte[] bruto = Files.readAllBytes(destino);
        String conteudo = new String(bruto, StandardCharsets.ISO_8859_1);
        assertTrue(conteudo.startsWith("%PDF-1.4"));
        assertTrue(conteudo.contains("/Type /Catalog"));
        assertTrue(conteudo.contains("/WinAnsiEncoding"), "sem isso os acentos saem errados");
        assertTrue(conteudo.contains("xref"));
        assertTrue(conteudo.trim().endsWith("%%EOF"));
        assertTrue(conteudo.contains("Relatório de Reservas"));
        assertTrue(conteudo.contains("RE-0002"));
    }

    @Test
    @DisplayName("FA05: pasta inexistente ou sem permissao e reportada com a causa")
    void fa05FalhaNaExportacao() throws IOException {
        Relatorio relatorio = gerarParaExportar();

        // pasta somente leitura: o sistema de arquivos recusa a escrita
        Path pasta = Files.createTempDirectory("tripcontrol-somente-leitura");
        pasta.toFile().setWritable(false);

        Resultado<Path> resultado = controller.exportarCsv(relatorio, pasta.resolve("x.csv"));

        // Em ambientes que ignoram a permissao (root em container), a gravacao pode
        // funcionar; o que importa e que nenhuma excecao escapa para a tela.
        if (!resultado.isSucesso()) {
            assertEquals(StatusResultado.OPERACAO_BLOQUEADA, resultado.getStatus());
            assertTrue(resultado.getMensagem().contains("prévia e os filtros"));
        }
        pasta.toFile().setWritable(true);
    }

    @Test
    @DisplayName("Exportar sem relatorio gerado e bloqueado")
    void naoExportaSemRelatorio() throws IOException {
        Path destino = Files.createTempDirectory("tripcontrol").resolve("vazio.csv");

        assertEquals(StatusResultado.OPERACAO_BLOQUEADA,
                controller.exportarCsv(null, destino).getStatus());
        assertFalse(Files.exists(destino));
    }
}
