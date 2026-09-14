package com.tripcontrol.controller.dto;

import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.SituacaoPacote;

/**
 * Linha da tela de controle de vagas (UC04, passo 2): o pacote acompanhado
 * dos valores calculados de ocupacao. As vagas nunca sao persistidas, sempre
 * derivadas das reservas ativas.
 */
public record PacoteComVagas(Pacote pacote, int vagasOcupadas, int vagasDisponiveis, SituacaoPacote situacao) {

    public String codigo() {
        return pacote.getCodigo();
    }

    public String destino() {
        return pacote.getDestino();
    }

    public int capacidadeTotal() {
        return pacote.getCapacidadeTotal();
    }

    /** @return percentual de ocupacao, usado no relatorio de ocupacao (UC08). */
    public double percentualOcupacao() {
        int capacidade = pacote.getCapacidadeTotal();
        return capacidade <= 0 ? 0d : (vagasOcupadas * 100d) / capacidade;
    }
}
