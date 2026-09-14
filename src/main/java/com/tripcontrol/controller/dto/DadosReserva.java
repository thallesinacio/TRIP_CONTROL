package com.tripcontrol.controller.dto;

/**
 * Dados brutos do formulario de reserva (UC03, passo 3).
 * Cliente e pacote chegam como identificadores porque a tela os seleciona
 * em listas ja carregadas do repositorio.
 */
public record DadosReserva(Long clienteId,
                           Long pacoteId,
                           String quantidadeViajantes,
                           String dataInicio,
                           String dataFim,
                           String observacoes) {
}
