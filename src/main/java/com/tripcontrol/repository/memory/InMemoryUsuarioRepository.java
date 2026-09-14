package com.tripcontrol.repository.memory;

import com.tripcontrol.model.Usuario;
import com.tripcontrol.repository.UsuarioRepository;

import java.util.Optional;

/** Implementacao volatil de {@link UsuarioRepository} usada antes do banco real. */
public class InMemoryUsuarioRepository extends RepositorioEmMemoria<Usuario> implements UsuarioRepository {

    @Override
    protected Long extrairId(Usuario entidade) {
        return entidade.getId();
    }

    @Override
    protected void atribuirId(Usuario entidade, Long id) {
        entidade.setId(id);
    }

    @Override
    public synchronized Optional<Usuario> buscarPorEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return registros.values().stream()
                .filter(usuario -> email.trim().equalsIgnoreCase(usuario.getEmail()))
                .findFirst();
    }
}
