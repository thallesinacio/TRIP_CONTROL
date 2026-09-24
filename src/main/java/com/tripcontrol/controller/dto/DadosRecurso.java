package com.tripcontrol.controller.dto;

import com.tripcontrol.model.TipoItemItinerario;

/**
 * Dados do cadastro de hospedagem, transporte ou atividade (decisao B da equipe,
 * atalho do FA02 do UC06).
 *
 * <p>Sao exatamente os campos que a entidade {@code Recurso} ja possui: o cadastro
 * existe para alimentar a lista do passo 5 do UC06, nao para criar atributos novos.</p>
 */
public record DadosRecurso(TipoItemItinerario tipo,
                           String nome,
                           String local,
                           String contato,
                           String descricao) {
}
