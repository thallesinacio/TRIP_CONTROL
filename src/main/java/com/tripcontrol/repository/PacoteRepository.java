package com.tripcontrol.repository;

import com.tripcontrol.model.Pacote;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Operacoes de persistencia de pacotes de viagem (UC01, UC04, UC06, UC08). */
public interface PacoteRepository extends Repositorio<Pacote> {

    Optional<Pacote> buscarPorCodigo(String codigo);

    /** Suporte a FA03 do UC01: mesmo destino e exatamente o mesmo periodo. */
    Optional<Pacote> buscarPorDestinoEPeriodo(String destino, LocalDate dataInicio, LocalDate dataFim);

    /** Busca livre por codigo, destino ou data (UC06, passo 2). */
    List<Pacote> buscarPorTermo(String termo);

    /** Pacotes cujo periodo intersecta o intervalo informado (UC08). */
    List<Pacote> buscarPorPeriodo(LocalDate inicio, LocalDate fim);

    /**
     * Gera o proximo codigo de negocio no formato {@code PC-000}.
     * Na implementacao JDBC correspondera a uma sequence do banco.
     */
    String proximoCodigo();
}
