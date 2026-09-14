package com.tripcontrol.repository.memory;

import com.tripcontrol.model.Parcela;
import com.tripcontrol.repository.ParcelaRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Implementacao volatil de {@link ParcelaRepository} usada antes do banco real. */
public class InMemoryParcelaRepository extends RepositorioEmMemoria<Parcela> implements ParcelaRepository {

    @Override
    protected Long extrairId(Parcela entidade) {
        return entidade.getId();
    }

    @Override
    protected void atribuirId(Parcela entidade, Long id) {
        entidade.setId(id);
    }

    @Override
    public synchronized List<Parcela> buscarPorReserva(Long reservaId) {
        return registros.values().stream()
                .filter(parcela -> Objects.equals(parcela.getReservaId(), reservaId))
                .sorted(Comparator.comparingInt(Parcela::getNumero))
                .toList();
    }

    @Override
    public synchronized Optional<Parcela> buscarPorReservaENumero(Long reservaId, int numero) {
        return registros.values().stream()
                .filter(parcela -> Objects.equals(parcela.getReservaId(), reservaId))
                .filter(parcela -> parcela.getNumero() == numero)
                .findFirst();
    }

    @Override
    public synchronized void removerPorReserva(Long reservaId) {
        registros.values().removeIf(parcela -> Objects.equals(parcela.getReservaId(), reservaId));
    }
}
