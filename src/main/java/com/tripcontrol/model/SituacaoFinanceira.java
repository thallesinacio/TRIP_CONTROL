package com.tripcontrol.model;

/**
 * Situacao financeira da reserva, recalculada a cada pagamento (UC05, passo 9).
 */
public enum SituacaoFinanceira {
    PENDENTE("Pendente"),
    PARCIALMENTE_PAGA("Parcialmente Paga"),
    QUITADA("Quitada"),
    ATRASADA("Atrasada");

    private final String descricao;

    SituacaoFinanceira(String descricao) {
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
