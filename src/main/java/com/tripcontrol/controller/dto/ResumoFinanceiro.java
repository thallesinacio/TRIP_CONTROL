package com.tripcontrol.controller.dto;

import com.tripcontrol.model.SituacaoFinanceira;

import java.math.BigDecimal;

/**
 * Situação financeira consolidada de uma reserva, exibida no painel de detalhes
 * do UC07 (passo 4: "total pago e saldo pendente").
 *
 * <p>Os valores vêm dos pagamentos já registrados para a reserva. Enquanto o
 * UC05 não estiver implementado não existem pagamentos, então o total pago é
 * zero e o saldo é o valor integral da reserva — o que é a leitura correta do
 * estado atual do sistema. A situação {@code ATRASADA} depende das parcelas e
 * será calculada pelo UC05.</p>
 */
public record ResumoFinanceiro(BigDecimal valorTotal,
                               BigDecimal totalPago,
                               BigDecimal saldoPendente,
                               SituacaoFinanceira situacao) {
}
