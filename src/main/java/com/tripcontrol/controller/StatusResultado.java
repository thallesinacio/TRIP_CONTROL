package com.tripcontrol.controller;

/**
 * Classifica o desfecho de uma operacao de Controller, permitindo que a tela
 * reaja de forma diferente a cada fluxo alternativo previsto nos casos de uso.
 */
public enum StatusResultado {
    /** Fluxo principal concluido. */
    SUCESSO,
    /** Campos obrigatorios ausentes ou valores invalidos. */
    ERRO_VALIDACAO,
    /** Registro equivalente ja existe (UC01 FA03, UC02 FA02, UC05 FA04). */
    DUPLICIDADE,
    /** Registro pesquisado nao localizado (UC05 FA01, UC07 FA01). */
    NAO_ENCONTRADO,
    /** Dados alterados por outro processo apos a abertura da tela (UC04 FA04, UC07 FA05). */
    CONFLITO_CONCORRENCIA,
    /** Operacao bloqueada por regra de negocio (ex.: reserva cancelada, UC05 FA02). */
    OPERACAO_BLOQUEADA
}
