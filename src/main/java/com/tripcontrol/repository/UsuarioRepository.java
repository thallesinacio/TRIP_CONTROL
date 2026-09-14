package com.tripcontrol.repository;

import com.tripcontrol.model.Usuario;

import java.util.Optional;

/** Operacoes de persistencia de usuarios do sistema (RNF04). */
public interface UsuarioRepository extends Repositorio<Usuario> {

    Optional<Usuario> buscarPorEmail(String email);
}
