package com.tripcontrol.repository;

import java.util.List;
import java.util.Optional;

/**
 * Contrato basico de persistencia (padrao Repository/DAO).
 *
 * <p>Toda a aplicacao depende apenas desta abstracao. Hoje existem somente as
 * implementacoes em memoria (pacote {@code repository.memory}); quando o modelo
 * de banco estiver fechado, bastara criar as versoes JDBC e trocar a montagem
 * feita em {@code com.tripcontrol.app.ContextoAplicacao}, sem alterar
 * Controllers nem Views.</p>
 *
 * @param <T> tipo da entidade de dominio
 */
public interface Repositorio<T> {

    /**
     * Persiste uma nova entidade e devolve a instancia com o identificador gerado.
     */
    T salvar(T entidade);

    /**
     * Atualiza uma entidade ja existente.
     *
     * @return a entidade atualizada
     * @throws IllegalArgumentException se a entidade nao possuir identificador conhecido
     */
    T atualizar(T entidade);

    Optional<T> buscarPorId(Long id);

    List<T> buscarTodos();

    /**
     * Remove a entidade do repositorio.
     *
     * @return {@code true} se algo foi removido
     */
    boolean remover(Long id);

    long contar();
}
