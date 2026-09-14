package com.tripcontrol.controller.dto;

/**
 * Dados brutos do formulario de cadastro de pacote (UC01, passo 3).
 *
 * <p>Os campos chegam como texto, exatamente como digitados na tela: a
 * conversao e a validacao acontecem no Controller, o que mantem a View sem
 * regra de negocio e permite testar todos os fluxos alternativos sem JavaFX.</p>
 */
public record DadosPacote(String destino,
                          String dataInicio,
                          String dataFim,
                          String descricao,
                          String preco,
                          String capacidadeTotal,
                          String roteiroPrevisto) {
}
