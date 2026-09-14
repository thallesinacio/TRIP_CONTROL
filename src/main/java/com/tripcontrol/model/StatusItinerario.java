package com.tripcontrol.model;

/** Estado do itinerario: rascunho ate o funcionario finalizar (UC06, passo 10). */
public enum StatusItinerario {
    RASCUNHO("Rascunho"),
    FINALIZADO("Finalizado");

    private final String descricao;

    StatusItinerario(String descricao) {
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
