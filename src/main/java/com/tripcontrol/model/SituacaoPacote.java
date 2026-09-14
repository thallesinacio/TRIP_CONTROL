package com.tripcontrol.model;

/**
 * Situacao operacional de um pacote, calculada em UC04 a partir da
 * capacidade total, das vagas ocupadas e da data final do pacote.
 */
public enum SituacaoPacote {
    DISPONIVEL("Disponivel"),
    LOTADO("Lotado"),
    ENCERRADO("Encerrado");

    private final String descricao;

    SituacaoPacote(String descricao) {
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
