package com.tripcontrol.repository;

import com.tripcontrol.model.Itinerario;

import java.util.Optional;

/** Operacoes de persistencia do itinerario de um pacote (UC06). */
public interface ItinerarioRepository extends Repositorio<Itinerario> {

    Optional<Itinerario> buscarPorPacote(Long pacoteId);
}
