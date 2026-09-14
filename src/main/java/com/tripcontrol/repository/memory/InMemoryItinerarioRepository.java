package com.tripcontrol.repository.memory;

import com.tripcontrol.model.Itinerario;
import com.tripcontrol.repository.ItinerarioRepository;

import java.util.Objects;
import java.util.Optional;

/** Implementacao volatil de {@link ItinerarioRepository} usada antes do banco real. */
public class InMemoryItinerarioRepository extends RepositorioEmMemoria<Itinerario> implements ItinerarioRepository {

    @Override
    protected Long extrairId(Itinerario entidade) {
        return entidade.getId();
    }

    @Override
    protected void atribuirId(Itinerario entidade, Long id) {
        entidade.setId(id);
    }

    @Override
    public synchronized Optional<Itinerario> buscarPorPacote(Long pacoteId) {
        return registros.values().stream()
                .filter(itinerario -> Objects.equals(itinerario.getPacoteId(), pacoteId))
                .findFirst();
    }
}
