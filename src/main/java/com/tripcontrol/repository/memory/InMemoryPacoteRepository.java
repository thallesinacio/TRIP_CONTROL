package com.tripcontrol.repository.memory;

import com.tripcontrol.model.Pacote;
import com.tripcontrol.repository.PacoteRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** Implementacao volatil de {@link PacoteRepository} usada antes do banco real. */
public class InMemoryPacoteRepository extends RepositorioEmMemoria<Pacote> implements PacoteRepository {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int CODIGO_INICIAL = 100;

    @Override
    protected Long extrairId(Pacote entidade) {
        return entidade.getId();
    }

    @Override
    protected void atribuirId(Pacote entidade, Long id) {
        entidade.setId(id);
    }

    @Override
    public synchronized Optional<Pacote> buscarPorCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return registros.values().stream()
                .filter(pacote -> codigo.equalsIgnoreCase(pacote.getCodigo()))
                .findFirst();
    }

    @Override
    public synchronized Optional<Pacote> buscarPorDestinoEPeriodo(String destino, LocalDate dataInicio, LocalDate dataFim) {
        if (destino == null || dataInicio == null || dataFim == null) {
            return Optional.empty();
        }
        return registros.values().stream()
                .filter(pacote -> destino.trim().equalsIgnoreCase(
                        pacote.getDestino() == null ? null : pacote.getDestino().trim()))
                .filter(pacote -> dataInicio.equals(pacote.getDataInicio()))
                .filter(pacote -> dataFim.equals(pacote.getDataFim()))
                .findFirst();
    }

    @Override
    public synchronized List<Pacote> buscarPorTermo(String termo) {
        if (termo == null || termo.isBlank()) {
            return buscarTodos();
        }
        String alvo = termo.trim();
        return registros.values().stream()
                .filter(pacote -> contemIgnorandoCaixa(pacote.getCodigo(), alvo)
                        || contemIgnorandoCaixa(pacote.getDestino(), alvo)
                        || (pacote.getDataInicio() != null
                            && pacote.getDataInicio().format(FORMATO_DATA).contains(alvo))
                        || (pacote.getDataFim() != null
                            && pacote.getDataFim().format(FORMATO_DATA).contains(alvo)))
                .toList();
    }

    @Override
    public synchronized List<Pacote> buscarPorPeriodo(LocalDate inicio, LocalDate fim) {
        return registros.values().stream()
                .filter(pacote -> pacote.getDataInicio() != null && pacote.getDataFim() != null)
                .filter(pacote -> (inicio == null || !pacote.getDataFim().isBefore(inicio))
                        && (fim == null || !pacote.getDataInicio().isAfter(fim)))
                .toList();
    }

    @Override
    public synchronized String proximoCodigo() {
        return String.format("PC-%03d", CODIGO_INICIAL + valorAtualDaSequencia() + 1);
    }
}
