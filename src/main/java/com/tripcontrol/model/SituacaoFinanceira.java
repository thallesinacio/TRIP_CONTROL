package com.tripcontrol.model;

import java.math.BigDecimal;

/**
 * Situacao financeira da reserva, recalculada a cada pagamento (UC05, passo 9).
 *
 * <p><strong>Esta situacao nunca e gravada.</strong> Ela e sempre derivada do valor
 * total da reserva, dos pagamentos registrados e do vencimento das parcelas. Guardar
 * o resultado em coluna faria a reserva aparecer como "Quitada" depois de qualquer
 * reinicio que perdesse os pagamentos, e e exatamente o que a view
 * {@code SituacaoFinanceiraReserva} do banco calcula no lado do SQL.</p>
 */
public enum SituacaoFinanceira {
    PENDENTE("Pendente"),
    PARCIALMENTE_PAGA("Parcialmente Paga"),
    QUITADA("Quitada"),
    ATRASADA("Atrasada");

    private final String descricao;

    SituacaoFinanceira(String descricao) {
        this.descricao = descricao;
    }

    /**
     * Aplica a ordem de precedencia decidida pela equipe para o passo 9 do UC05:
     *
     * <ol>
     *   <li><strong>Quitada</strong> — saldo zerado; vence qualquer outra condicao,
     *       inclusive a existencia de parcela vencida (se tudo foi pago, nao ha atraso);</li>
     *   <li><strong>Atrasada</strong> — existe parcela vencida e ainda nao quitada,
     *       tenha havido pagamento parcial ou nenhum;</li>
     *   <li><strong>Parcialmente Paga</strong> — ja houve pagamento, sem parcela vencida;</li>
     *   <li><strong>Pendente</strong> — nenhum pagamento registrado.</li>
     * </ol>
     *
     * <p>A ordem e a mesma da view {@code SituacaoFinanceiraReserva} do
     * {@code script.sql}, para que a Etapa 6 possa trocar este calculo pela consulta
     * a view sem mudar o comportamento percebido na tela.</p>
     *
     * @param valorTotal                    valor total da reserva
     * @param totalPago                     soma dos recebimentos ja registrados
     * @param existeParcelaVencidaEmAberto  ha parcela com vencimento passado e sem quitacao
     */
    public static SituacaoFinanceira calcular(BigDecimal valorTotal,
                                              BigDecimal totalPago,
                                              boolean existeParcelaVencidaEmAberto) {
        BigDecimal total = valorTotal == null ? BigDecimal.ZERO : valorTotal;
        BigDecimal pago = totalPago == null ? BigDecimal.ZERO : totalPago;

        if (pago.compareTo(total) >= 0) {
            return QUITADA;
        }
        if (existeParcelaVencidaEmAberto) {
            return ATRASADA;
        }
        return pago.compareTo(BigDecimal.ZERO) > 0 ? PARCIALMENTE_PAGA : PENDENTE;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
