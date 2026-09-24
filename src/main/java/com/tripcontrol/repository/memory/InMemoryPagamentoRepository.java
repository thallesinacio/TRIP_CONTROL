package com.tripcontrol.repository.memory;

import com.tripcontrol.model.Pagamento;
import com.tripcontrol.repository.PagamentoRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Implementacao volatil de {@link PagamentoRepository} usada antes do banco real. */
public class InMemoryPagamentoRepository extends RepositorioEmMemoria<Pagamento> implements PagamentoRepository {

    @Override
    protected Long extrairId(Pagamento entidade) {
        return entidade.getId();
    }

    @Override
    protected void atribuirId(Pagamento entidade, Long id) {
        entidade.setId(id);
    }

    @Override
    public synchronized List<Pagamento> buscarPorReserva(Long reservaId) {
        return registros.values().stream()
                .filter(pagamento -> Objects.equals(pagamento.getReservaId(), reservaId))
                .toList();
    }

    @Override
    public synchronized Optional<Pagamento> buscarPorIdentificadorTransacao(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            return Optional.empty();
        }
        return registros.values().stream()
                .filter(pagamento -> identificador.trim().equalsIgnoreCase(pagamento.getIdentificadorTransacao()))
                .findFirst();
    }

    @Override
    public synchronized Optional<Pagamento> buscarPorReservaEParcela(Long reservaId, int numeroParcela) {
        return registros.values().stream()
                .filter(pagamento -> Objects.equals(pagamento.getReservaId(), reservaId))
                .filter(pagamento -> pagamento.getNumeroParcela() != null
                        && pagamento.getNumeroParcela() == numeroParcela)
                .findFirst();
    }

    @Override
    public synchronized List<Pagamento> buscarPorPeriodoDeRecebimento(LocalDate inicio, LocalDate fim) {
        return registros.values().stream()
                .filter(pagamento -> pagamento.getDataRecebimento() != null)
                .filter(pagamento -> (inicio == null || !pagamento.getDataRecebimento().isBefore(inicio))
                        && (fim == null || !pagamento.getDataRecebimento().isAfter(fim)))
                .toList();
    }

    /**
     * Numero de recibo sequencial. Toma como base o maior numero ja gravado, e nao a
     * quantidade de registros, para que a remocao de um pagamento nunca faca a
     * sequencia reaproveitar um numero de recibo ja emitido.
     */
    @Override
    public synchronized String proximoNumeroRecibo() {
        long ultimo = registros.values().stream()
                .map(Pagamento::getNumeroRecibo)
                .filter(numero -> numero != null && numero.matches("RC-\\d+"))
                .map(numero -> Long.parseLong(numero.substring(3)))
                .max(Comparator.naturalOrder())
                .orElse(0L);
        return String.format("RC-%06d", ultimo + 1);
    }
}
