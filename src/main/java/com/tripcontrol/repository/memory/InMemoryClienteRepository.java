package com.tripcontrol.repository.memory;

import com.tripcontrol.model.Cliente;
import com.tripcontrol.repository.ClienteRepository;

import java.util.List;
import java.util.Optional;

/** Implementacao volatil de {@link ClienteRepository} usada antes do banco real. */
public class InMemoryClienteRepository extends RepositorioEmMemoria<Cliente> implements ClienteRepository {

    @Override
    protected Long extrairId(Cliente entidade) {
        return entidade.getId();
    }

    @Override
    protected void atribuirId(Cliente entidade, Long id) {
        entidade.setId(id);
    }

    @Override
    public synchronized Optional<Cliente> buscarPorCpf(String cpf) {
        if (cpf == null) {
            return Optional.empty();
        }
        String somenteDigitos = cpf.replaceAll("\\D", "");
        return registros.values().stream()
                .filter(cliente -> cliente.getCpf() != null
                        && cliente.getCpf().replaceAll("\\D", "").equals(somenteDigitos))
                .findFirst();
    }

    @Override
    public synchronized List<Cliente> buscarPorNome(String trechoDoNome) {
        if (trechoDoNome == null || trechoDoNome.isBlank()) {
            return buscarTodos();
        }
        return registros.values().stream()
                .filter(cliente -> contemIgnorandoCaixa(cliente.getNome(), trechoDoNome.trim()))
                .toList();
    }

    @Override
    public synchronized List<Cliente> buscarPorPreferencia(String preferencia) {
        if (preferencia == null || preferencia.isBlank()) {
            return buscarTodos();
        }
        return registros.values().stream()
                .filter(cliente -> cliente.getPreferencias().stream()
                        .anyMatch(item -> item.equalsIgnoreCase(preferencia.trim())))
                .toList();
    }
}
