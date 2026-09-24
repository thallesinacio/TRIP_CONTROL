package com.tripcontrol.controller.dto;

import com.tripcontrol.model.Pagamento;
import com.tripcontrol.model.Parcela;
import com.tripcontrol.model.StatusParcela;
import com.tripcontrol.util.Formatadores;

import java.math.BigDecimal;

/**
 * Linha da tabela de parcelas do UC05 (passo 4): a parcela, o pagamento que a
 * quitou (quando existe) e o status ja calculado.
 *
 * <p>O status nao vem gravado na parcela: e derivado da data de pagamento e do
 * vencimento no momento da consulta, pela mesma razao que a situacao financeira
 * da reserva tambem e sempre calculada.</p>
 */
public record LinhaParcela(Parcela parcela, Pagamento pagamento, StatusParcela status) {

    /** "1/3", como o prototipo apresenta a coluna Parcela. */
    public String numeroFormatado() {
        return parcela.getNumero() + "/" + parcela.getTotalParcelas();
    }

    public String vencimentoFormatado() {
        return Formatadores.formatarData(parcela.getDataVencimento());
    }

    public String valorFormatado() {
        return Formatadores.formatarMoeda(parcela.getValor());
    }

    public String recebimentoFormatado() {
        return parcela.getDataPagamento() == null
                ? "-" : Formatadores.formatarData(parcela.getDataPagamento());
    }

    public String formaFormatada() {
        return pagamento == null || pagamento.getFormaPagamento() == null
                ? "-" : pagamento.getFormaPagamento().getDescricao();
    }

    /** Referencia da transacao exigida pelo passo 4 do UC05. */
    public String identificadorFormatado() {
        return pagamento == null || pagamento.getIdentificadorTransacao() == null
                ? "-" : pagamento.getIdentificadorTransacao();
    }

    public String reciboFormatado() {
        return pagamento == null || pagamento.getNumeroRecibo() == null
                ? "-" : pagamento.getNumeroRecibo();
    }

    public boolean isQuitada() {
        return status == StatusParcela.QUITADA;
    }

    /** @return valor ainda em aberto: zero quando a parcela ja foi quitada. */
    public BigDecimal valorEmAberto() {
        return isQuitada() ? BigDecimal.ZERO : parcela.getValor();
    }

    @Override
    public String toString() {
        return numeroFormatado() + " - " + valorFormatado();
    }
}
