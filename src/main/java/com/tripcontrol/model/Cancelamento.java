package com.tripcontrol.model;

import java.time.LocalDateTime;

/**
 * Dados registrados no cancelamento de uma reserva (UC07, passo 9).
 * Fica embutido na reserva para preservar o historico mesmo apos o cancelamento.
 */
public class Cancelamento {

    private MotivoCancelamento motivo;
    private String descricao;
    private String usuarioResponsavel;
    private LocalDateTime dataHora = LocalDateTime.now();
    private int vagasDevolvidas;

    public Cancelamento() {
    }

    public Cancelamento(MotivoCancelamento motivo, String descricao, String usuarioResponsavel, int vagasDevolvidas) {
        this.motivo = motivo;
        this.descricao = descricao;
        this.usuarioResponsavel = usuarioResponsavel;
        this.vagasDevolvidas = vagasDevolvidas;
    }

    public MotivoCancelamento getMotivo() {
        return motivo;
    }

    public void setMotivo(MotivoCancelamento motivo) {
        this.motivo = motivo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
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

    public int getVagasDevolvidas() {
        return vagasDevolvidas;
    }

    public void setVagasDevolvidas(int vagasDevolvidas) {
        this.vagasDevolvidas = vagasDevolvidas;
    }

    @Override
    public String toString() {
        return "Cancelamento{motivo=" + motivo + ", em=" + dataHora + '}';
    }
}
