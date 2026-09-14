package com.tripcontrol.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Cliente da agencia (UC02). O CPF e guardado somente com digitos, o que
 * garante que a checagem de duplicidade independa da mascara digitada.
 */
public class Cliente {

    private Long id;
    private String nome;
    private String cpf;
    private String telefone;
    private String email;
    private String endereco;
    private final List<String> preferencias = new ArrayList<>();
    private LocalDateTime dataCadastro = LocalDateTime.now();

    public Cliente() {
    }

    public Cliente(String nome, String cpf, String telefone) {
        this.nome = nome;
        this.cpf = cpf;
        this.telefone = telefone;
    }

    public void adicionarPreferencia(String preferencia) {
        if (preferencia != null && !preferencia.isBlank() && !preferencias.contains(preferencia.trim())) {
            preferencias.add(preferencia.trim());
        }
    }

    public void removerPreferencia(String preferencia) {
        preferencias.remove(preferencia);
    }

    public void definirPreferencias(List<String> novasPreferencias) {
        preferencias.clear();
        if (novasPreferencias != null) {
            novasPreferencias.forEach(this::adicionarPreferencia);
        }
    }

    public List<String> getPreferencias() {
        return Collections.unmodifiableList(preferencias);
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

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public LocalDateTime getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDateTime dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Cliente outro)) {
            return false;
        }
        if (id != null && outro.id != null) {
            return id.equals(outro.id);
        }
        return Objects.equals(cpf, outro.cpf);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(cpf);
    }

    @Override
    public String toString() {
        return nome + (cpf == null ? "" : " (CPF: " + cpf + ")");
    }
}
