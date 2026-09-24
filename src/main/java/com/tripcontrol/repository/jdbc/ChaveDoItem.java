package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.TipoItemItinerario;

/**
 * Identificador unico para itens de itinerario espalhados por tres tabelas.
 *
 * <p>{@code Hospedagem}, {@code Transporte} e {@code Atividade} tem sequences
 * independentes, entao a hospedagem 1 e o transporte 1 existem ao mesmo tempo.
 * Como {@code ItemItinerario.equals} compara por id, dois itens diferentes
 * ficariam "iguais" — e remover um do cronograma removeria o outro.</p>
 *
 * <p>A faixa por tipo resolve isso sem coluna nova: cada tipo ocupa um bloco de
 * um bilhao de ids.</p>
 */
final class ChaveDoItem {

    private static final long FAIXA_POR_TIPO = 1_000_000_000L;

    private ChaveDoItem() {
    }

    static Long id(TipoItemItinerario tipo, long idNaTabela) {
        return tipo.ordinal() * FAIXA_POR_TIPO + idNaTabela;
    }
}
