package com.tripcontrol.util;

import com.tripcontrol.controller.dto.Relatorio;
import com.tripcontrol.controller.dto.TotalRelatorio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exportacao do relatorio em PDF (UC08, passo 9), sem dependencia externa.
 *
 * <p><strong>Por que nao uma biblioteca.</strong> O roadmap sugere OpenPDF ou
 * PDFBox, ambas de licenca permissiva. Um relatorio deste sistema, porem, e uma
 * tabela de texto em fonte padrao — o subconjunto do formato PDF necessario para
 * isso e pequeno e estavel desde os anos 90. Escrever esse subconjunto aqui evita
 * acrescentar um jar de alguns megabytes ao projeto, elimina qualquer discussao de
 * licenca e mantem o RNF02 (execucao 100% local) trivialmente satisfeito.</p>
 *
 * <p>Se a equipe preferir a biblioteca depois, a troca e nesta classe so: o resto
 * do sistema conhece apenas {@code exportar(Relatorio, Path)}.</p>
 *
 * <p><strong>Como o arquivo e montado.</strong> Um PDF e uma lista de objetos
 * numerados seguida de uma tabela (a {@code xref}) que diz em que byte cada objeto
 * comeca. Aqui sao gerados: o catalogo, a lista de paginas, uma fonte normal e uma
 * negrito, e um par pagina + fluxo de conteudo para cada pagina de tabela. O texto
 * usa {@code WinAnsiEncoding}, que cobre os acentos do portugues.</p>
 */
public final class ExportadorPdf {

    /** A4 paisagem, em pontos: relatorios sao largos. */
    private static final float LARGURA = 842f;
    private static final float ALTURA = 595f;
    private static final float MARGEM = 32f;

    private static final float TAMANHO_TITULO = 15f;
    private static final float TAMANHO_TEXTO = 8.5f;
    private static final float ALTURA_DA_LINHA = 14f;

    /** Larguras medias de Helvetica, usadas para caber o texto na coluna. */
    private static final float LARGURA_MEDIA_DO_CARACTERE = 0.5f;

    /**
     * WinAnsiEncoding e o CP1252, nao o ISO-8859-1: os dois coincidem nos acentos do
     * portugues, mas so o CP1252 tem travessao, aspas curvas e reticencias — que
     * aparecem sempre que alguem cola texto de um editor num campo de observacao.
     */
    private static final Charset WINANSI = Charset.forName("windows-1252");

    private ExportadorPdf() {
    }

    /**
     * Grava o relatorio no caminho informado.
     *
     * @throws IOException quando a pasta nao existe, falta permissao de escrita ou
     *                     o disco esta cheio — as causas previstas no FA05
     */
    public static void exportar(Relatorio relatorio, Path destino) throws IOException {
        Path pasta = destino.toAbsolutePath().getParent();
        if (pasta != null) {
            Files.createDirectories(pasta);
        }
        Files.write(destino, montarDocumento(relatorio));
    }

    // ------------------------------------------------------------------
    // Montagem do documento
    // ------------------------------------------------------------------

    private static byte[] montarDocumento(Relatorio relatorio) {
        float[] larguras = calcularLarguras(relatorio);
        List<List<List<String>>> paginas = dividirEmPaginas(relatorio);

        // Objetos: 1 catalogo, 2 lista de paginas, 3 fonte normal, 4 fonte negrito,
        // e depois um par (pagina, conteudo) por pagina.
        int primeiroObjetoDePagina = 5;
        int totalDePaginas = paginas.size();

        List<byte[]> objetos = new ArrayList<>();
        objetos.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));

        StringBuilder referencias = new StringBuilder();
        for (int i = 0; i < totalDePaginas; i++) {
            referencias.append(primeiroObjetoDePagina + i * 2).append(" 0 R ");
        }
        objetos.add(bytes("<< /Type /Pages /Count " + totalDePaginas
                + " /Kids [" + referencias.toString().trim() + "] >>"));
        objetos.add(bytes(fonte("Helvetica")));
        objetos.add(bytes(fonte("Helvetica-Bold")));

        for (int i = 0; i < totalDePaginas; i++) {
            int objetoDoConteudo = primeiroObjetoDePagina + i * 2 + 1;
            objetos.add(bytes("<< /Type /Page /Parent 2 0 R"
                    + " /MediaBox [0 0 " + inteiro(LARGURA) + " " + inteiro(ALTURA) + "]"
                    + " /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >>"
                    + " /Contents " + objetoDoConteudo + " 0 R >>"));

            byte[] conteudo = bytes(montarConteudoDaPagina(
                    relatorio, paginas.get(i), larguras, i + 1, totalDePaginas));
            ByteArrayOutputStream fluxo = new ByteArrayOutputStream();
            escrever(fluxo, "<< /Length " + conteudo.length + " >>\nstream\n");
            escrever(fluxo, conteudo);
            escrever(fluxo, "\nendstream");
            objetos.add(fluxo.toByteArray());
        }

        return serializar(objetos);
    }

    /** Escreve os objetos, a tabela xref e o trailer, na ordem que o formato exige. */
    private static byte[] serializar(List<byte[]> objetos) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        escrever(saida, "%PDF-1.4\n");
        // O comentario binario avisa leitores e ferramentas de que o arquivo nao e texto puro.
        saida.write(new byte[] {'%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'}, 0, 6);

        int[] posicoes = new int[objetos.size()];
        for (int i = 0; i < objetos.size(); i++) {
            posicoes[i] = saida.size();
            escrever(saida, (i + 1) + " 0 obj\n");
            escrever(saida, objetos.get(i));
            escrever(saida, "\nendobj\n");
        }

        int inicioDaXref = saida.size();
        escrever(saida, "xref\n0 " + (objetos.size() + 1) + "\n");
        escrever(saida, "0000000000 65535 f \n");
        for (int posicao : posicoes) {
            escrever(saida, String.format("%010d 00000 n %n", posicao).replace(
                    System.lineSeparator(), "\n"));
        }
        escrever(saida, "trailer\n<< /Size " + (objetos.size() + 1) + " /Root 1 0 R >>\n");
        escrever(saida, "startxref\n" + inicioDaXref + "\n%%EOF\n");
        return saida.toByteArray();
    }

    private static String montarConteudoDaPagina(Relatorio relatorio,
                                                 List<List<String>> linhas,
                                                 float[] larguras,
                                                 int pagina,
                                                 int totalDePaginas) {
        StringBuilder conteudo = new StringBuilder();
        float y = ALTURA - MARGEM;

        if (pagina == 1) {
            y = escreverTitulo(relatorio, conteudo, y);
        } else {
            texto(conteudo, "F2", TAMANHO_TITULO, MARGEM, y,
                    "Relatório de " + relatorio.tipo().getDescricao() + " (continuação)");
            y -= ALTURA_DA_LINHA * 2;
        }

        y = escreverCabecalhoDaTabela(relatorio, conteudo, larguras, y);
        for (List<String> valores : linhas) {
            escreverLinha(conteudo, larguras, y, valores, "F1");
            y -= ALTURA_DA_LINHA;
        }

        texto(conteudo, "F1", 7.5f, MARGEM, MARGEM - 8f,
                "TripControl · página " + pagina + " de " + totalDePaginas);
        return conteudo.toString();
    }

    private static float escreverTitulo(Relatorio relatorio, StringBuilder conteudo, float y) {
        texto(conteudo, "F2", TAMANHO_TITULO, MARGEM, y,
                "Relatório de " + relatorio.tipo().getDescricao());
        y -= ALTURA_DA_LINHA * 1.4f;

        texto(conteudo, "F1", TAMANHO_TEXTO, MARGEM, y,
                "Gerado em " + Formatadores.formatarDataHora(relatorio.geradoEm())
                        + "  ·  " + relatorio.quantidadeDeRegistros() + " registro(s)");
        y -= ALTURA_DA_LINHA;

        texto(conteudo, "F1", TAMANHO_TEXTO, MARGEM, y,
                "Filtros: " + relatorio.filtrosAplicados());
        y -= ALTURA_DA_LINHA;

        if (!relatorio.totais().isEmpty()) {
            String indicadores = relatorio.totais().stream()
                    .map(TotalRelatorio::toString)
                    .reduce((um, outro) -> um + "  ·  " + outro)
                    .orElse("");
            texto(conteudo, "F2", TAMANHO_TEXTO, MARGEM, y, indicadores);
            y -= ALTURA_DA_LINHA;
        }
        return y - ALTURA_DA_LINHA * 0.5f;
    }

    private static float escreverCabecalhoDaTabela(Relatorio relatorio, StringBuilder conteudo,
                                                   float[] larguras, float y) {
        escreverLinha(conteudo, larguras, y, relatorio.colunas(), "F2");
        y -= 4f;
        // Linha horizontal separando o cabecalho dos dados.
        conteudo.append("0.6 w ").append(formatar(MARGEM)).append(' ').append(formatar(y))
                .append(" m ").append(formatar(LARGURA - MARGEM)).append(' ')
                .append(formatar(y)).append(" l S\n");
        return y - ALTURA_DA_LINHA;
    }

    private static void escreverLinha(StringBuilder conteudo, float[] larguras, float y,
                                      List<String> valores, String fonte) {
        float x = MARGEM;
        for (int i = 0; i < valores.size() && i < larguras.length; i++) {
            texto(conteudo, fonte, TAMANHO_TEXTO, x, y, cortar(valores.get(i), larguras[i]));
            x += larguras[i];
        }
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /** Distribui a largura util entre as colunas, proporcional ao conteudo de cada uma. */
    private static float[] calcularLarguras(Relatorio relatorio) {
        int colunas = relatorio.colunas().size();
        float[] pesos = new float[colunas];
        for (int i = 0; i < colunas; i++) {
            pesos[i] = relatorio.colunas().get(i).length();
        }
        for (List<String> linha : relatorio.linhas()) {
            for (int i = 0; i < colunas && i < linha.size(); i++) {
                pesos[i] = Math.max(pesos[i], Math.min(linha.get(i).length(), 40));
            }
        }

        float soma = 0f;
        for (float peso : pesos) {
            soma += peso;
        }
        float disponivel = LARGURA - MARGEM * 2;
        float[] larguras = new float[colunas];
        for (int i = 0; i < colunas; i++) {
            larguras[i] = soma == 0f ? disponivel / colunas : disponivel * pesos[i] / soma;
        }
        return larguras;
    }

    /** Quantas linhas cabem por pagina, considerando o cabecalho maior da primeira. */
    private static List<List<List<String>>> dividirEmPaginas(Relatorio relatorio) {
        float alturaUtil = ALTURA - MARGEM * 2 - ALTURA_DA_LINHA * 7;
        int porPagina = Math.max(1, (int) (alturaUtil / ALTURA_DA_LINHA));

        List<List<List<String>>> paginas = new ArrayList<>();
        List<List<String>> linhas = relatorio.linhas();
        if (linhas.isEmpty()) {
            paginas.add(List.of());
            return paginas;
        }
        for (int i = 0; i < linhas.size(); i += porPagina) {
            paginas.add(linhas.subList(i, Math.min(linhas.size(), i + porPagina)));
        }
        return paginas;
    }

    /** Corta o texto que nao cabe na coluna, deixando claro que houve corte. */
    private static String cortar(String valor, float larguraDisponivel) {
        String seguro = valor == null ? "" : valor;
        int limite = (int) ((larguraDisponivel - 4f) / (TAMANHO_TEXTO * LARGURA_MEDIA_DO_CARACTERE));
        if (limite <= 3 || seguro.length() <= limite) {
            return seguro;
        }
        return seguro.substring(0, limite - 3) + "...";
    }

    // ------------------------------------------------------------------
    // Primitivas do formato
    // ------------------------------------------------------------------

    private static String fonte(String nome) {
        return "<< /Type /Font /Subtype /Type1 /BaseFont /" + nome
                + " /Encoding /WinAnsiEncoding >>";
    }

    private static void texto(StringBuilder conteudo, String fonte, float tamanho,
                              float x, float y, String valor) {
        conteudo.append("BT /").append(fonte).append(' ').append(formatar(tamanho))
                .append(" Tf ").append(formatar(x)).append(' ').append(formatar(y))
                .append(" Td (").append(escapar(valor)).append(") Tj ET\n");
    }

    /** Em PDF, parenteses e barra invertida delimitam e escapam strings. */
    private static String escapar(String valor) {
        return valor.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                .replace("\r", " ").replace("\n", " ");
    }

    private static String formatar(float valor) {
        return String.format(java.util.Locale.US, "%.2f", valor);
    }

    private static String inteiro(float valor) {
        return String.valueOf((int) valor);
    }

    private static byte[] bytes(String texto) {
        return texto.getBytes(WINANSI);
    }

    /**
     * @return {@code true} se o texto tem algum caractere que o CP1252 nao
     *         representa (por exemplo um alfabeto nao latino). Esses caracteres saem
     *         como "?" no PDF; os acentos do portugues estao todos cobertos.
     */
    static boolean temCaractereForaDoCp1252(String texto) {
        return !WINANSI.newEncoder().canEncode(texto);
    }

    private static void escrever(ByteArrayOutputStream saida, String texto) {
        escrever(saida, bytes(texto));
    }

    private static void escrever(ByteArrayOutputStream saida, byte[] dados) {
        saida.write(dados, 0, dados.length);
    }
}
