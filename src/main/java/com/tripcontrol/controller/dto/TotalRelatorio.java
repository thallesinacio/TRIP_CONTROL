package com.tripcontrol.controller.dto;

/** Indicador exibido no cabeçalho da prévia (UC08, passo 8): rótulo e valor já formatado. */
public record TotalRelatorio(String rotulo, String valor) {

    @Override
    public String toString() {
        return rotulo + ": " + valor;
    }
}
