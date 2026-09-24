package com.tripcontrol.controller.dto;

import com.tripcontrol.model.TipoItemItinerario;

/**
 * Dados digitados no painel "Adicionar Item ao Cronograma" (UC06, passos 5 e 6).
 *
 * <p>Um unico formato atende aos tres tipos, porque o que muda entre eles e quais
 * campos a tela mostra, nao o significado do intervalo:</p>
 *
 * <ul>
 *   <li><strong>Hospedagem</strong>: inicio = check-in, fim = check-out;</li>
 *   <li><strong>Transporte</strong>: inicio = saida, fim = chegada, mais origem e destino;</li>
 *   <li><strong>Atividade</strong>: uma unica data com hora de inicio e de termino, e o
 *       local de encontro em {@code local} — por isso {@code dataFim} nao e usado neste
 *       tipo: a tabela {@code Atividade} do banco guarda uma data e dois horarios, o que
 *       impede uma atividade atravessar a meia-noite.</li>
 * </ul>
 */
public record DadosItemItinerario(TipoItemItinerario tipo,
                                  Long recursoId,
                                  String dataInicio,
                                  String horaInicio,
                                  String dataFim,
                                  String horaFim,
                                  String localOrigem,
                                  String localDestino,
                                  String instrucoesOperacionais) {
}
