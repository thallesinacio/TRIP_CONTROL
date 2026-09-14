package com.tripcontrol.model;

/** Tipos de item que compoem o cronograma de um pacote (UC06, passo 5). */
public enum TipoItemItinerario {
    HOSPEDAGEM("Hospedagem"),
    TRANSPORTE("Transporte"),
    ATIVIDADE("Atividade");

    private final String descricao;

    TipoItemItinerario(String descricao) {
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
