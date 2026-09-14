package com.tripcontrol.model;

import java.util.Objects;

/**
 * Funcionario habilitado a operar o sistema (RNF04).
 * A senha nunca e mantida em texto puro: apenas o hash BCrypt e armazenado.
 */
public class Usuario {

    private Long id;
    private String nome;
    private String email;
    private String senhaHash;
    private PerfilAcesso perfil = PerfilAcesso.FUNCIONARIO;
    private boolean ativo = true;

    public Usuario() {
    }

    public Usuario(String nome, String email, String senhaHash, PerfilAcesso perfil) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public PerfilAcesso getPerfil() {
        return perfil;
    }

    public void setPerfil(PerfilAcesso perfil) {
        this.perfil = perfil;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Usuario outro)) {
            return false;
        }
        if (id != null && outro.id != null) {
            return id.equals(outro.id);
        }
        return Objects.equals(email, outro.email);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(email);
    }

    @Override
    public String toString() {
        return "Usuario{id=" + id + ", nome='" + nome + "', email='" + email + "', perfil=" + perfil + '}';
    }
}
