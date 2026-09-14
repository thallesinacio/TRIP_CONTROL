package com.tripcontrol.repository;

import com.tripcontrol.model.Reserva;
import com.tripcontrol.model.StatusReserva;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Operacoes de persistencia de reservas (UC03, UC04, UC05, UC07, UC08). */
public interface ReservaRepository extends Repositorio<Reserva> {

    Optional<Reserva> buscarPorCodigo(String codigo);

    List<Reserva> buscarPorCliente(Long clienteId);

    List<Reserva> buscarPorPacote(Long pacoteId);

    /** Reservas ativas de um pacote: base do calculo de vagas ocupadas (UC04). */
    List<Reserva> buscarAtivasPorPacote(Long pacoteId);

    List<Reserva> buscarPorStatus(StatusReserva status);

    /** Reservas registradas dentro do intervalo informado (UC08). */
    List<Reserva> buscarPorPeriodoDeViagem(LocalDate inicio, LocalDate fim);

    /**
     * Gera o proximo codigo de negocio no formato {@code RE-0000}.
     * Na implementacao JDBC correspondera a uma sequence do banco.
     */
    String proximoCodigo();
}
