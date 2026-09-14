package com.tripcontrol.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Registro historico de uma alteracao de capacidade de pacote (UC04, passo 7):
 * guarda data, hora, funcionario responsavel e justificativa da mudanca.
 */
public class AlteracaoCapacidade {

    private Long id;
    private Long pacoteId;
    private int capacidadeAnterior;
    private int capacidadeNova;
    private String justificativa;
    private String usuarioResponsavel;
    private LocalDateTime dataHora = LocalDateTime.now();

    public AlteracaoCapacidade() {
    }

    public AlteracaoCapacidade(Long pacoteId, int capacidadeAnterior, int capacidadeNova,
                               String justificativa, String usuarioResponsavel) {
        this.pacoteId = pacoteId;
        this.capacidadeAnterior = capacidadeAnterior;
        this.capacidadeNova = capacidadeNova;
        this.justificativa = justificativa;
        this.usuarioResponsavel = usuarioResponsavel;
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

    public int getCapacidadeAnterior() {
        return capacidadeAnterior;
    }

    public void setCapacidadeAnterior(int capacidadeAnterior) {
        this.capacidadeAnterior = capacidadeAnterior;
    }

    public int getCapacidadeNova() {
        return capacidadeNova;
    }

    public void setCapacidadeNova(int capacidadeNova) {
        this.capacidadeNova = capacidadeNova;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public void setJustificativa(String justificativa) {
        this.justificativa = justificativa;
    }

    public String getUsuarioResponsavel() {
        return usuarioResponsavel;
    }

    public void setUsuarioResponsavel(String usuarioResponsavel) {
        this.usuarioResponsavel = usuarioResponsavel;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AlteracaoCapacidade outra)) {
            return false;
        }
        return id != null && id.equals(outra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AlteracaoCapacidade{pacoteId=" + pacoteId + ", de=" + capacidadeAnterior
                + ", para=" + capacidadeNova + ", em=" + dataHora + '}';
    }
}
