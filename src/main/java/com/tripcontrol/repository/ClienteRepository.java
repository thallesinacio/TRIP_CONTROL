package com.tripcontrol.repository;

import com.tripcontrol.model.Cliente;

import java.util.List;
import java.util.Optional;

/** Operacoes de persistencia de clientes (UC02, UC03, UC05, UC08). */
public interface ClienteRepository extends Repositorio<Cliente> {

    /** Suporte a FA02 do UC02: CPF e o identificador natural do cliente. */
    Optional<Cliente> buscarPorCpf(String cpf);

    List<Cliente> buscarPorNome(String trechoDoNome);

    List<Cliente> buscarPorPreferencia(String preferencia);
}
