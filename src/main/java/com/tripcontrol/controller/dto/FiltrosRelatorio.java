package com.tripcontrol.controller.dto;

import com.tripcontrol.model.CriterioRanking;
import com.tripcontrol.model.FormaPagamento;
import com.tripcontrol.model.SituacaoFinanceira;
import com.tripcontrol.model.SituacaoPacote;
import com.tripcontrol.model.StatusReserva;

/**
 * Filtros da tela de relatorios (UC08, passo 4).
 *
 * <p>Um unico registro atende aos cinco relatorios: a tela mostra apenas os
 * campos do tipo escolhido e os demais chegam nulos. Os campos livres vem como
 * texto, para que a critica do FA02 aconteca no Controller e possa ser testada
 * sem interface grafica; os campos de lista chegam como enum, porque vem de
 * combos de valores fixos.</p>
 *
 * @param dataInicio  inicio do periodo — viagem, recebimento, registro da reserva
 *                    ou inicio do pacote, conforme o relatorio
 * @param dataFim     fim do mesmo periodo
 */
public record FiltrosRelatorio(String dataInicio,
                               String dataFim,
                               StatusReserva situacaoReserva,
                               String codigoPacote,
                               String cpfCliente,
                               SituacaoFinanceira situacaoFinanceira,
                               FormaPagamento formaPagamento,
                               String posicoesRanking,
                               CriterioRanking criterioRanking,
                               String nomeCliente,
                               String preferencia,
                               String minimoViagensConcluidas,
                               String destino,
                               SituacaoPacote situacaoPacote,
                               String percentualMinimoOcupacao) {

    /** Filtros vazios: usado pelos testes e pela primeira abertura da tela. */
    public static FiltrosRelatorio vazios() {
        return new FiltrosRelatorio(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }
}
