package com.tripcontrol.util;

import com.tripcontrol.controller.dto.Relatorio;
import com.tripcontrol.controller.dto.TotalRelatorio;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exportacao do relatorio em CSV (UC08, passo 9).
 *
 * <p>Dois detalhes fazem o arquivo abrir certo no Excel em portugues, e nenhum dos
 * dois e obvio:</p>
 *
 * <ul>
 *   <li><strong>Separador ponto e virgula.</strong> No Excel configurado para
 *       pt-BR, a virgula e separador decimal, entao um CSV separado por virgula
 *       joga tudo numa coluna so.</li>
 *   <li><strong>BOM no inicio do arquivo.</strong> Sem os tres bytes
 *       {@code EF BB BF}, o Excel no Windows le o arquivo como ANSI e os acentos
 *       aparecem trocados ("Ocupação" vira "OcupaÃ§Ã£o").</li>
 * </ul>
 */
public final class ExportadorCsv {

    private static final String SEPARADOR = ";";
    private static final String BOM = "﻿";

    private ExportadorCsv() {
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
        try (Writer escritor = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            escritor.write(BOM);
            escreverCabecalho(relatorio, escritor);
            escritor.write(System.lineSeparator());
            escreverTabela(relatorio, escritor);
        }
    }

    private static void escreverCabecalho(Relatorio relatorio, Writer escritor) throws IOException {
        linha(escritor, List.of("TripControl - Relatório de " + relatorio.tipo().getDescricao()));
        linha(escritor, List.of("Gerado em", Formatadores.formatarDataHora(relatorio.geradoEm())));
        linha(escritor, List.of("Filtros aplicados", relatorio.filtrosAplicados()));
        linha(escritor, List.of("Registros", String.valueOf(relatorio.quantidadeDeRegistros())));
        for (TotalRelatorio total : relatorio.totais()) {
            linha(escritor, List.of(total.rotulo(), total.valor()));
        }
    }

    private static void escreverTabela(Relatorio relatorio, Writer escritor) throws IOException {
        linha(escritor, relatorio.colunas());
        for (List<String> valores : relatorio.linhas()) {
            linha(escritor, valores);
        }
    }

    private static void linha(Writer escritor, List<String> campos) throws IOException {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < campos.size(); i++) {
            if (i > 0) {
                texto.append(SEPARADOR);
            }
            texto.append(escapar(campos.get(i)));
        }
        escritor.write(texto.toString());
        escritor.write(System.lineSeparator());
    }

    /** Envolve em aspas quando o valor tem separador, aspas ou quebra de linha. */
    private static String escapar(String valor) {
        String seguro = valor == null ? "" : valor;
        if (seguro.contains(SEPARADOR) || seguro.contains("\"")
                || seguro.contains("\n") || seguro.contains("\r")) {
            return '"' + seguro.replace("\"", "\"\"") + '"';
        }
        return seguro;
    }
}
