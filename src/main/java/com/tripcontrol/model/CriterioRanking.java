package com.tripcontrol.model;

/** Critério de ordenação do relatório de Pacotes Mais Procurados (UC08, passo 4). */
public enum CriterioRanking {
    NUMERO_DE_RESERVAS("Número de Reservas"),
    NUMERO_DE_VIAJANTES("Número de Viajantes");

    private final String descricao;

    CriterioRanking(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
