package com.tripcontrol.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pacote de viagem comercializado pela agencia (UC01).
 *
 * <p>A capacidade total e o unico dado de vagas persistido: as vagas ocupadas
 * sao sempre derivadas das reservas ativas (UC04, passo 4), evitando contadores
 * duplicados que possam divergir da realidade.</p>
 */
public class Pacote {

    private Long id;
    private String codigo;
    private String destino;
    private LocalDate dataInicio;
    private LocalDate dataFim;
    private String descricao;
    private BigDecimal preco;
    private int capacidadeTotal;
    private String roteiroPrevisto;
    private LocalDateTime dataCadastro = LocalDateTime.now();
    private final List<AlteracaoCapacidade> historicoCapacidade = new ArrayList<>();

    public Pacote() {
    }

    public Pacote(String destino, LocalDate dataInicio, LocalDate dataFim, String descricao,
                  BigDecimal preco, int capacidadeTotal, String roteiroPrevisto) {
        this.destino = destino;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.descricao = descricao;
        this.preco = preco;
        this.capacidadeTotal = capacidadeTotal;
        this.roteiroPrevisto = roteiroPrevisto;
    }

    /** @return duracao do pacote em noites, ou zero quando o periodo nao esta definido. */
    public long duracaoEmNoites() {
        if (dataInicio == null || dataFim == null) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(dataInicio, dataFim);
    }

    /**
     * Calcula a situacao do pacote conforme UC04.
     *
     * @param vagasOcupadas soma dos viajantes das reservas ativas
     * @param referencia    data considerada como "hoje"
     */
    public SituacaoPacote situacao(int vagasOcupadas, LocalDate referencia) {
        if (dataFim != null && referencia != null && dataFim.isBefore(referencia)) {
            return SituacaoPacote.ENCERRADO;
        }
        return vagasOcupadas >= capacidadeTotal ? SituacaoPacote.LOTADO : SituacaoPacote.DISPONIVEL;
    }

    /** @return vagas ainda disponiveis, nunca negativo (UC04, passo 4). */
    public int vagasDisponiveis(int vagasOcupadas) {
        return Math.max(0, capacidadeTotal - vagasOcupadas);
    }

    /** @return {@code true} se o periodo informado cabe dentro da vigencia do pacote (UC03, FA02). */
    public boolean contemPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || dataInicio == null || dataFim == null) {
            return false;
        }
        return !inicio.isBefore(dataInicio) && !fim.isAfter(dataFim);
    }

    public void registrarAlteracaoCapacidade(AlteracaoCapacidade alteracao) {
        if (alteracao != null) {
            historicoCapacidade.add(alteracao);
        }
    }

    public List<AlteracaoCapacidade> getHistoricoCapacidade() {
        return Collections.unmodifiableList(historicoCapacidade);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(LocalDate dataInicio) {
        this.dataInicio = dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public void setDataFim(LocalDate dataFim) {
        this.dataFim = dataFim;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public int getCapacidadeTotal() {
        return capacidadeTotal;
    }

    public void setCapacidadeTotal(int capacidadeTotal) {
        this.capacidadeTotal = capacidadeTotal;
    }

    public String getRoteiroPrevisto() {
        return roteiroPrevisto;
    }

    public void setRoteiroPrevisto(String roteiroPrevisto) {
        this.roteiroPrevisto = roteiroPrevisto;
    }

    public LocalDateTime getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDateTime dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Pacote outro)) {
            return false;
        }
        if (id != null && outro.id != null) {
            return id.equals(outro.id);
        }
        return Objects.equals(codigo, outro.codigo);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(codigo);
    }

    @Override
    public String toString() {
        return (codigo == null ? "" : codigo + " - ") + destino;
    }
}
