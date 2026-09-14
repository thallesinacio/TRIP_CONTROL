package com.tripcontrol.repository.memory;

import com.tripcontrol.repository.Repositorio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base das implementacoes em memoria: guarda as entidades em um {@link LinkedHashMap}
 * sincronizado (preservando a ordem de insercao) e gera identificadores sequenciais,
 * imitando o comportamento de uma chave primaria auto incremental.
 *
 * <p>Esta classe existe apenas enquanto o modelo de banco esta sendo fechado.
 * Nenhum Controller conhece este tipo: todos dependem das interfaces de repositorio.</p>
 *
 * @param <T> tipo da entidade de dominio
 */
public abstract class RepositorioEmMemoria<T> implements Repositorio<T> {

    /** Armazenamento interno; o acesso e sincronizado em {@code this}. */
    protected final Map<Long, T> registros = new LinkedHashMap<>();
    private final AtomicLong sequencia = new AtomicLong(0L);

    protected abstract Long extrairId(T entidade);

    protected abstract void atribuirId(T entidade, Long id);

    @Override
    public synchronized T salvar(T entidade) {
        if (entidade == null) {
            throw new IllegalArgumentException("Entidade nao pode ser nula.");
        }
        Long id = extrairId(entidade);
        if (id == null) {
            id = sequencia.incrementAndGet();
            atribuirId(entidade, id);
        } else {
            final long idInformado = id;
            sequencia.updateAndGet(atual -> Math.max(atual, idInformado));
        }
        registros.put(id, entidade);
        return entidade;
    }

    @Override
    public synchronized T atualizar(T entidade) {
        Long id = extrairId(entidade);
        if (id == null || !registros.containsKey(id)) {
            throw new IllegalArgumentException("Registro inexistente para atualizacao: id=" + id);
        }
        registros.put(id, entidade);
        return entidade;
    }

    @Override
    public synchronized Optional<T> buscarPorId(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(registros.get(id));
    }

    @Override
    public synchronized List<T> buscarTodos() {
        return new ArrayList<>(registros.values());
    }

    @Override
    public synchronized boolean remover(Long id) {
        return id != null && registros.remove(id) != null;
    }

    @Override
    public synchronized long contar() {
        return registros.size();
    }

    /** Numero de registros ja criados, usado na geracao de codigos de negocio. */
    protected long valorAtualDaSequencia() {
        return sequencia.get();
    }

    /** Comparacao textual tolerante a nulos, acentos irrelevantes e caixa. */
    protected static boolean contemIgnorandoCaixa(String texto, String trecho) {
        if (texto == null || trecho == null) {
            return false;
        }
        return texto.toLowerCase().contains(trecho.toLowerCase());
    }
}
