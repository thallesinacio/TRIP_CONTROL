package com.tripcontrol.model;

/**
 * Perfis de acesso previstos pelo RNF04. Nesta fase apenas o perfil
 * {@link #FUNCIONARIO} e utilizado pelos casos de uso UC01 a UC08.
 */
public enum PerfilAcesso {
    FUNCIONARIO("Funcionario"),
    ADMINISTRADOR("Administrador");

    private final String descricao;

    PerfilAcesso(String descricao) {
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
