package com.tripcontrol.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Item do cronograma de um pacote (UC06). Um mesmo formato atende aos tres
 * tipos: em hospedagem o intervalo representa check-in/check-out, em transporte
 * a saida/chegada e em atividade o inicio/termino.
 */
public class ItemItinerario {

    private Long id;
    private Long itinerarioId;
    private TipoItemItinerario tipo;
    private Long recursoId;
    private String nomeServico;
    private LocalDateTime inicio;
    private LocalDateTime fim;
    private String localOrigem;
    private String localDestino;
    private String instrucoesOperacionais;

    public ItemItinerario() {
    }

    public ItemItinerario(TipoItemItinerario tipo, String nomeServico, LocalDateTime inicio, LocalDateTime fim) {
        this.tipo = tipo;
        this.nomeServico = nomeServico;
        this.inicio = inicio;
        this.fim = fim;
    }

    /** @return {@code true} se este item se sobrepoe no tempo ao item informado (UC06, FA04). */
    public boolean conflitaCom(ItemItinerario outro) {
        if (outro == null || inicio == null || fim == null || outro.inicio == null || outro.fim == null) {
            return false;
        }
        return inicio.isBefore(outro.fim) && outro.inicio.isBefore(fim);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getItinerarioId() {
        return itinerarioId;
    }

    public void setItinerarioId(Long itinerarioId) {
        this.itinerarioId = itinerarioId;
    }

    public TipoItemItinerario getTipo() {
        return tipo;
    }

    public void setTipo(TipoItemItinerario tipo) {
        this.tipo = tipo;
    }

    public Long getRecursoId() {
        return recursoId;
    }

    public void setRecursoId(Long recursoId) {
        this.recursoId = recursoId;
    }

    public String getNomeServico() {
        return nomeServico;
    }

    public void setNomeServico(String nomeServico) {
        this.nomeServico = nomeServico;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public void setInicio(LocalDateTime inicio) {
        this.inicio = inicio;
    }

    public LocalDateTime getFim() {
        return fim;
    }

    public void setFim(LocalDateTime fim) {
        this.fim = fim;
    }

    public String getLocalOrigem() {
        return localOrigem;
    }

    public void setLocalOrigem(String localOrigem) {
        this.localOrigem = localOrigem;
    }

    public String getLocalDestino() {
        return localDestino;
    }

    public void setLocalDestino(String localDestino) {
        this.localDestino = localDestino;
    }

    public String getInstrucoesOperacionais() {
        return instrucoesOperacionais;
    }

    public void setInstrucoesOperacionais(String instrucoesOperacionais) {
        this.instrucoesOperacionais = instrucoesOperacionais;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemItinerario outro)) {
            return false;
        }
        return id != null && id.equals(outro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return tipo + " - " + nomeServico;
    }
}
