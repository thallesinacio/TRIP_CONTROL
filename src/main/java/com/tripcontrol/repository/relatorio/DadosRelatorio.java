package com.tripcontrol.repository.relatorio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado de uma consulta de relatorio: as linhas e os indicadores.
 *
 * <p>As linhas vem como texto ja formatado. A escolha e deliberada: os cinco
 * relatorios do UC08 nao tem nenhuma coluna em comum, e devolver objetos de
 * dominio exigiria cinco tipos e cinco formatadores para chegar ao mesmo lugar.
 * Com um formato tabular unico, a tabela da tela, o CSV e o PDF compartilham o
 * mesmo caminho — e a formatacao continua centralizada em
 * {@code util.Formatadores}, so chamada daqui.</p>
 *
 * @param totais indicadores na ordem de exibicao (LinkedHashMap preserva a ordem)
 */
public record DadosRelatorio(List<List<String>> linhas, Map<String, String> totais) {

    public DadosRelatorio {
        linhas = linhas == null ? List.of() : linhas.stream().map(List::copyOf).toList();
        totais = totais == null ? Map.of() : new LinkedHashMap<>(totais);
    }

    public static DadosRelatorio vazio() {
        return new DadosRelatorio(List.of(), Map.of());
    }
}
