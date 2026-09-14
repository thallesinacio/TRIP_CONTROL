package com.tripcontrol.model;

/** Formas de pagamento aceitas pela agencia (UC05, passo 6). */
public enum FormaPagamento {
    DINHEIRO("Dinheiro"),
    PIX("PIX"),
    CARTAO_DEBITO("Cartao de Debito"),
    CARTAO_CREDITO("Cartao de Credito"),
    TRANSFERENCIA("Transferencia");

    private final String descricao;

    FormaPagamento(String descricao) {
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
