package com.tripcontrol.repository;

import com.tripcontrol.model.Parcela;

import java.util.List;
import java.util.Optional;

/** Operacoes de persistencia das parcelas do plano de pagamento (UC05). */
public interface ParcelaRepository extends Repositorio<Parcela> {

    List<Parcela> buscarPorReserva(Long reservaId);

    Optional<Parcela> buscarPorReservaENumero(Long reservaId, int numero);

    void removerPorReserva(Long reservaId);
}
