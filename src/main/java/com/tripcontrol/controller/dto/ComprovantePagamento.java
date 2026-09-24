package com.tripcontrol.controller.dto;

import com.tripcontrol.model.Pagamento;
import com.tripcontrol.model.SituacaoFinanceira;

import java.math.BigDecimal;

/**
 * Confirmacao exibida ao final do UC05 (passo 10): numero do recibo, valor
 * registrado e saldo pendente atualizado.
 */
public record ComprovantePagamento(Pagamento pagamento,
                                   BigDecimal saldoPendente,
                                   SituacaoFinanceira situacao,
                                   SituacaoPagamentos situacaoAtualizada) {

    public String numeroRecibo() {
        return pagamento.getNumeroRecibo();
    }

    public BigDecimal valorRegistrado() {
        return pagamento.getValor();
    }
}
