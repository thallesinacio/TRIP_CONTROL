package com.tripcontrol.model;

/** Situacao individual de uma parcela do plano de pagamento (UC05). */
public enum StatusParcela {
    PENDENTE("Pendente"),
    QUITADA("Quitada"),
    ATRASADA("Atrasada");

    private final String descricao;

    StatusParcela(String descricao) {
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
