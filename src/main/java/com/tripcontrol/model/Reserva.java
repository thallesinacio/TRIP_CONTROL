package com.tripcontrol.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Vinculo entre um cliente e um pacote (UC03).
 *
 * <p>Guarda apenas os identificadores de cliente e pacote: assim a entidade
 * continua valida quando o repositorio JDBC substituir o repositorio em memoria,
 * sem carregar grafos de objetos desnecessarios.</p>
 */
public class Reserva {

    private Long id;
    private String codigo;
    private Long clienteId;
    private Long pacoteId;
    private int quantidadeViajantes;
    private LocalDate dataInicio;
    private LocalDate dataFim;
    private String observacoes;
    private BigDecimal valorTotal = BigDecimal.ZERO;
    private StatusReserva status = StatusReserva.ATIVA;
    private LocalDateTime dataRegistro = LocalDateTime.now();
    private Cancelamento cancelamento;
    /**
     * Versao da linha, incrementada a cada gravacao pelo repositorio e usada no
     * bloqueio otimista (UC07 FA05, UC04 FA04).
     */
    private long versao;

    public Reserva() {
    }

    public Reserva(Long clienteId, Long pacoteId, int quantidadeViajantes,
                   LocalDate dataInicio, LocalDate dataFim, String observacoes) {
        this.clienteId = clienteId;
        this.pacoteId = pacoteId;
        this.quantidadeViajantes = quantidadeViajantes;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.observacoes = observacoes;
    }

    public boolean isAtiva() {
        return status == StatusReserva.ATIVA;
    }

    public boolean isCancelada() {
        return status == StatusReserva.CANCELADA;
    }

    /**
     * Aplica o cancelamento mantendo o historico financeiro (UC07, passo 9).
     *
     * <p>Nao mexe na versao: quem incrementa e o repositorio, ao gravar, para que
     * o valor em memoria continue sendo a versao efetivamente persistida (base do
     * bloqueio otimista).</p>
     */
    public void cancelar(Cancelamento dadosCancelamento) {
        this.cancelamento = dadosCancelamento;
        this.status = StatusReserva.CANCELADA;
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

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }

    public Long getPacoteId() {
        return pacoteId;
    }

    public void setPacoteId(Long pacoteId) {
        this.pacoteId = pacoteId;
    }

    public int getQuantidadeViajantes() {
        return quantidadeViajantes;
    }

    public void setQuantidadeViajantes(int quantidadeViajantes) {
        this.quantidadeViajantes = quantidadeViajantes;
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

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    public StatusReserva getStatus() {
        return status;
    }

    public void setStatus(StatusReserva status) {
        this.status = status;
    }

    public LocalDateTime getDataRegistro() {
        return dataRegistro;
    }

    public void setDataRegistro(LocalDateTime dataRegistro) {
        this.dataRegistro = dataRegistro;
    }

    public Cancelamento getCancelamento() {
        return cancelamento;
    }

    public void setCancelamento(Cancelamento cancelamento) {
        this.cancelamento = cancelamento;
    }

    public long getVersao() {
        return versao;
    }

    public void setVersao(long versao) {
        this.versao = versao;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Reserva outra)) {
            return false;
        }
        if (id != null && outra.id != null) {
            return id.equals(outra.id);
        }
        return Objects.equals(codigo, outra.codigo);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(codigo);
    }

    @Override
    public String toString() {
        return "Reserva " + codigo + " (" + status + ")";
    }
}
