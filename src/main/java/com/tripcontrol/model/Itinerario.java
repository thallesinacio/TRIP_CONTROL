package com.tripcontrol.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Cronograma de um pacote (UC06). Permanece em {@link StatusItinerario#RASCUNHO}
 * ate que o funcionario acione "Finalizar Itinerario".
 */
public class Itinerario {

    private Long id;
    private Long pacoteId;
    private StatusItinerario status = StatusItinerario.RASCUNHO;
    private final List<ItemItinerario> itens = new ArrayList<>();
    private LocalDateTime dataFinalizacao;

    public Itinerario() {
    }

    public Itinerario(Long pacoteId) {
        this.pacoteId = pacoteId;
    }

    /** Adiciona o item mantendo a ordem cronologica exigida no passo 8 do UC06. */
    public void adicionarItem(ItemItinerario item) {
        if (item == null) {
            return;
        }
        itens.add(item);
        itens.sort(Comparator.comparing(ItemItinerario::getInicio,
                Comparator.nullsLast(Comparator.naturalOrder())));
    }

    public void removerItem(ItemItinerario item) {
        itens.remove(item);
    }

    /** @return o primeiro item que conflita no tempo com o informado, ou {@code null}. */
    public ItemItinerario buscarConflito(ItemItinerario candidato) {
        return itens.stream()
                .filter(existente -> !existente.equals(candidato))
                .filter(existente -> existente.conflitaCom(candidato))
                .findFirst()
                .orElse(null);
    }

    public void finalizar() {
        this.status = StatusItinerario.FINALIZADO;
        this.dataFinalizacao = LocalDateTime.now();
    }

    public boolean isVazio() {
        return itens.isEmpty();
    }

    public List<ItemItinerario> getItens() {
        return Collections.unmodifiableList(itens);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPacoteId() {
        return pacoteId;
    }

    public void setPacoteId(Long pacoteId) {
        this.pacoteId = pacoteId;
    }

    public StatusItinerario getStatus() {
        return status;
    }

    public void setStatus(StatusItinerario status) {
        this.status = status;
    }

    public LocalDateTime getDataFinalizacao() {
        return dataFinalizacao;
    }

    public void setDataFinalizacao(LocalDateTime dataFinalizacao) {
        this.dataFinalizacao = dataFinalizacao;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Itinerario outro)) {
            return false;
        }
        if (id != null && outro.id != null) {
            return id.equals(outro.id);
        }
        return Objects.equals(pacoteId, outro.pacoteId);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(pacoteId);
    }

    @Override
    public String toString() {
        return "Itinerario{pacoteId=" + pacoteId + ", itens=" + itens.size() + ", status=" + status + '}';
    }
}
