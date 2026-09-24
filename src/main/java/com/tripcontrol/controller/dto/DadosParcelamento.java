package com.tripcontrol.controller.dto;

/**
 * Dados da acao "Definir Parcelamento" (decisao A da equipe): quantidade de
 * parcelas e vencimento da primeira. As demais vencem de mes em mes.
 */
public record DadosParcelamento(String quantidadeParcelas, String dataPrimeiroVencimento) {
}
