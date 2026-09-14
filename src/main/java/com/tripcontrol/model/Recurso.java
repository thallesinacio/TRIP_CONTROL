package com.tripcontrol.model;

import java.util.Objects;

/**
 * Hospedagem, transporte ou atividade previamente cadastrada, usada como
 * insumo na montagem do itinerario (UC06, pre-condicao e FA02).
 */
public class Recurso {

    private Long id;
    private TipoItemItinerario tipo;
    private String nome;
    private String descricao;
    private String local;
    private String contato;

    public Recurso() {
    }

    public Recurso(TipoItemItinerario tipo, String nome, String local) {
        this.tipo = tipo;
        this.nome = nome;
        this.local = local;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TipoItemItinerario getTipo() {
        return tipo;
    }

    public void setTipo(TipoItemItinerario tipo) {
        this.tipo = tipo;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getLocal() {
        return local;
    }

    public void setLocal(String local) {
        this.local = local;
    }

    public String getContato() {
        return contato;
    }

    public void setContato(String contato) {
        this.contato = contato;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Recurso outro)) {
            return false;
        }
        if (id != null && outro.id != null) {
            return id.equals(outro.id);
        }
        return tipo == outro.tipo && Objects.equals(nome, outro.nome);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(tipo, nome);
    }

    @Override
    public String toString() {
        return nome;
    }
}
