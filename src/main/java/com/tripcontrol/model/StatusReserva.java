package com.tripcontrol.model;

/**
 * Situacao operacional da reserva (UC03 e UC07).
 * Apenas reservas {@link #ATIVA} ocupam vagas do pacote.
 */
public enum StatusReserva {
    ATIVA("Ativa"),
    CANCELADA("Cancelada"),
    CONCLUIDA("Concluida");

    private final String descricao;

    StatusReserva(String descricao) {
        this.descricao = descricao;
    }

    public boolean ocupaVaga() {
        return this == ATIVA;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
