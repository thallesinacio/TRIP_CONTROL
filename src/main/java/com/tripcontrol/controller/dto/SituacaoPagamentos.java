package com.tripcontrol.controller.dto;

import com.tripcontrol.model.Reserva;

import java.util.List;

/**
 * Tudo o que a tela de Gerenciamento Financeiro mostra para uma reserva
 * (UC05, passo 4): identificacao, consolidacao financeira e plano de parcelas.
 *
 * @param versaoFinanceira quantidade de pagamentos ja registrados na reserva no
 *                         momento em que a tela carregou. E o que permite detectar
 *                         o FA05 (saldo alterado por outro processo): se o numero
 *                         mudou entre a leitura e a gravacao, outro pagamento entrou
 *                         no meio do caminho. Na Etapa 6 este papel passa a ser da
 *                         coluna {@code versao} da reserva, como ja acontece no UC07.
 */
public record SituacaoPagamentos(ResumoReserva reserva,
                                 ResumoFinanceiro financeiro,
                                 List<LinhaParcela> parcelas,
                                 long versaoFinanceira) {

    public SituacaoPagamentos {
        parcelas = parcelas == null ? List.of() : List.copyOf(parcelas);
    }

    /** @return {@code true} quando a reserva ainda nao teve o parcelamento definido. */
    public boolean isSemPlanoDeParcelas() {
        return parcelas.isEmpty();
    }

    /** FA02: reserva cancelada mantem o historico visivel, mas bloqueia novo pagamento. */
    public boolean isBloqueadaParaPagamento() {
        Reserva dadosDaReserva = reserva.reserva();
        return dadosDaReserva.isCancelada();
    }

    /** Parcelas que ainda aceitam recebimento, usadas no combo "N. da Parcela". */
    public List<LinhaParcela> parcelasEmAberto() {
        return parcelas.stream().filter(linha -> !linha.isQuitada()).toList();
    }
}
