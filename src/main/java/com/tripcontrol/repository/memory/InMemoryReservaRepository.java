package com.tripcontrol.repository.memory;

import com.tripcontrol.model.Reserva;
import com.tripcontrol.model.StatusReserva;
import com.tripcontrol.repository.ReservaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Implementacao volatil de {@link ReservaRepository} usada antes do banco real. */
public class InMemoryReservaRepository extends RepositorioEmMemoria<Reserva> implements ReservaRepository {

    private static final int CODIGO_INICIAL = 8900;

    @Override
    protected Long extrairId(Reserva entidade) {
        return entidade.getId();
    }

    @Override
    protected void atribuirId(Reserva entidade, Long id) {
        entidade.setId(id);
    }

    /** Espelha o comportamento do repositorio JDBC: cada gravacao avanca a versao. */
    @Override
    public synchronized Reserva atualizar(Reserva entidade) {
        Reserva atualizada = super.atualizar(entidade);
        atualizada.setVersao(atualizada.getVersao() + 1);
        return atualizada;
    }

    @Override
    public synchronized Optional<Reserva> buscarPorCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return registros.values().stream()
                .filter(reserva -> codigo.trim().equalsIgnoreCase(reserva.getCodigo()))
                .findFirst();
    }

    @Override
    public synchronized List<Reserva> buscarPorCliente(Long clienteId) {
        return registros.values().stream()
                .filter(reserva -> Objects.equals(reserva.getClienteId(), clienteId))
                .toList();
    }

    @Override
    public synchronized List<Reserva> buscarPorPacote(Long pacoteId) {
        return registros.values().stream()
                .filter(reserva -> Objects.equals(reserva.getPacoteId(), pacoteId))
                .toList();
    }

    @Override
    public synchronized List<Reserva> buscarAtivasPorPacote(Long pacoteId) {
        return registros.values().stream()
                .filter(reserva -> Objects.equals(reserva.getPacoteId(), pacoteId))
                .filter(Reserva::isAtiva)
                .toList();
    }

    @Override
    public synchronized List<Reserva> buscarPorStatus(StatusReserva status) {
        return registros.values().stream()
                .filter(reserva -> reserva.getStatus() == status)
                .toList();
    }

    @Override
    public synchronized List<Reserva> buscarPorPeriodoDeViagem(LocalDate inicio, LocalDate fim) {
        return registros.values().stream()
                .filter(reserva -> reserva.getDataInicio() != null && reserva.getDataFim() != null)
                .filter(reserva -> (inicio == null || !reserva.getDataFim().isBefore(inicio))
                        && (fim == null || !reserva.getDataInicio().isAfter(fim)))
                .toList();
    }

    @Override
    public synchronized String proximoCodigo() {
        return String.format("RE-%04d", CODIGO_INICIAL + valorAtualDaSequencia() + 1);
    }
}
