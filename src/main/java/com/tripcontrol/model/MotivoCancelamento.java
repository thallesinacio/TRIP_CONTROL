package com.tripcontrol.model;

/** Motivos previstos para o cancelamento de uma reserva (UC07, passo 6). */
public enum MotivoCancelamento {
    DESISTENCIA_CLIENTE("Desistencia do Cliente", false),
    CANCELAMENTO_AGENCIA("Cancelamento pela Agencia", false),
    ALTERACAO_DE_DATA("Alteracao de Data", false),
    OUTRO("Outro", true);

    private final String descricao;
    private final boolean exigeDescricao;

    MotivoCancelamento(String descricao, boolean exigeDescricao) {
        this.descricao = descricao;
        this.exigeDescricao = exigeDescricao;
    }

    /** @return {@code true} quando a descricao detalhada e obrigatoria (FA03 do UC07). */
    public boolean isExigeDescricao() {
        return exigeDescricao;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
