package com.tripcontrol.controller;

/**
 * Erro associado a um campo especifico do formulario.
 * O nome do campo permite que a tela destaque exatamente o componente
 * com problema, como exigido nos fluxos alternativos de validacao.
 *
 * @param campo    identificador logico do campo (ex.: {@code "destino"})
 * @param mensagem texto exibido ao funcionario
 */
public record ErroValidacao(String campo, String mensagem) {
}
