package com.tripcontrol.controller.dto;

import com.tripcontrol.model.TipoRelatorio;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Relatorio pronto para exibir, imprimir ou exportar (UC08, passos 7 e 8).
 *
 * <p>As linhas sao listas de texto ja formatado, e nao objetos de dominio. Isso e
 * deliberado: os cinco relatorios tem formatos completamente diferentes, e um
 * formato tabular unico faz a tabela da tela, o CSV e o PDF compartilharem o
 * mesmo caminho em vez de existirem em cinco versoes cada.</p>
 *
 * @param totais           indicadores do passo 8, exibidos como chips no cabecalho
 * @param filtrosAplicados resumo textual dos filtros, impresso no relatorio
 */
public record Relatorio(TipoRelatorio tipo,
                        List<String> colunas,
                        List<List<String>> linhas,
                        List<TotalRelatorio> totais,
                        LocalDateTime geradoEm,
                        String filtrosAplicados) {

    public Relatorio {
        colunas = colunas == null ? List.of() : List.copyOf(colunas);
        linhas = linhas == null ? List.of() : linhas.stream().map(List::copyOf).toList();
        totais = totais == null ? List.of() : List.copyOf(totais);
    }

    public int quantidadeDeRegistros() {
        return linhas.size();
    }

    public boolean isVazio() {
        return linhas.isEmpty();
    }

    /** Nome sugerido do arquivo na exportacao, sem extensao. */
    public String nomeSugerido() {
        return "relatorio-" + tipo.name().toLowerCase().replace('_', '-');
    }
}
