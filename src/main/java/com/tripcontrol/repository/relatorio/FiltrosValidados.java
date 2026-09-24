package com.tripcontrol.repository.relatorio;

import com.tripcontrol.model.CriterioRanking;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Filtros do UC08 ja convertidos e criticados pelo Controller.
 *
 * <p>Existe para que a camada de consulta receba tipos, e nao texto: quando um
 * valor chega aqui, o FA02 ja passou. Campos nulos significam "sem filtro", e e
 * assim que as consultas os tratam.</p>
 */
public record FiltrosValidados(LocalDate dataInicio,
                               LocalDate dataFim,
                               String situacaoReserva,
                               String codigoPacote,
                               String cpfCliente,
                               String situacaoFinanceira,
                               String formaPagamento,
                               Integer posicoesRanking,
                               CriterioRanking criterioRanking,
                               String nomeCliente,
                               String preferencia,
                               Integer minimoViagensConcluidas,
                               String destino,
                               String situacaoPacote,
                               BigDecimal percentualMinimoOcupacao) {
}
