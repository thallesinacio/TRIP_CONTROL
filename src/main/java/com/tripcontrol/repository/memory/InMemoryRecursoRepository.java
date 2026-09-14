package com.tripcontrol.repository.memory;

import com.tripcontrol.model.Recurso;
import com.tripcontrol.model.TipoItemItinerario;
import com.tripcontrol.repository.RecursoRepository;

import java.util.List;

/** Implementacao volatil de {@link RecursoRepository} usada antes do banco real. */
public class InMemoryRecursoRepository extends RepositorioEmMemoria<Recurso> implements RecursoRepository {

    @Override
    protected Long extrairId(Recurso entidade) {
        return entidade.getId();
    }

    @Override
    protected void atribuirId(Recurso entidade, Long id) {
        entidade.setId(id);
    }

    @Override
    public synchronized List<Recurso> buscarPorTipo(TipoItemItinerario tipo) {
        return registros.values().stream()
                .filter(recurso -> recurso.getTipo() == tipo)
                .toList();
    }
}
