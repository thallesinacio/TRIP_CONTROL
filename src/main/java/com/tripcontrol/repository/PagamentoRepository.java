package com.tripcontrol.repository;

import com.tripcontrol.model.Pagamento;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Operacoes de persistencia de pagamentos (UC05, UC08). */
public interface PagamentoRepository extends Repositorio<Pagamento> {

    List<Pagamento> buscarPorReserva(Long reservaId);

    /** Suporte a FA04 do UC05: identificador de transacao nao pode se repetir. */
    Optional<Pagamento> buscarPorIdentificadorTransacao(String identificador);

    Optional<Pagamento> buscarPorReservaEParcela(Long reservaId, int numeroParcela);

    List<Pagamento> buscarPorPeriodoDeRecebimento(LocalDate inicio, LocalDate fim);
}
