package com.tripcontrol.controller.dto;

import java.util.List;

/** Dados brutos do formulario de cadastro de cliente (UC02, passo 3). */
public record DadosCliente(String nome,
                           String cpf,
                           String telefone,
                           String email,
                           String endereco,
                           List<String> preferencias) {

    public DadosCliente {
        preferencias = preferencias == null ? List.of() : List.copyOf(preferencias);
    }
}
