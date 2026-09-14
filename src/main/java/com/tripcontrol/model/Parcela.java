package com.tripcontrol.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Parcela do plano de pagamento de uma reserva (UC05).
 */
public class Parcela {

    private Long id;
    private Long reservaId;
    private int numero;
    private int totalParcelas;
    private BigDecimal valor;
    private LocalDate dataVencimento;
    private LocalDate dataPagamento;
    private StatusParcela status = StatusParcela.PENDENTE;
    private Long pagamentoId;

    public Parcela() {
    }

    public Parcela(Long reservaId, int numero, int totalParcelas, BigDecimal valor, LocalDate dataVencimento) {
        this.reservaId = reservaId;
        this.numero = numero;
        this.totalParcelas = totalParcelas;
        this.valor = valor;
        this.dataVencimento = dataVencimento;
    }

    /** @return {@code true} quando a parcela esta vencida e ainda nao foi quitada. */
    public boolean estaVencida(LocalDate referencia) {
        return status != StatusParcela.QUITADA
                && dataVencimento != null
                && referencia != null
                && dataVencimento.isBefore(referencia);
    }

    public void quitar(LocalDate dataPagamento, Long pagamentoId) {
        this.dataPagamento = dataPagamento;
        this.pagamentoId = pagamentoId;
        this.status = StatusParcela.QUITADA;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReservaId() {
        return reservaId;
    }

    public void setReservaId(Long reservaId) {
        this.reservaId = reservaId;
    }

    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public int getTotalParcelas() {
        return totalParcelas;
    }

    public void setTotalParcelas(int totalParcelas) {
        this.totalParcelas = totalParcelas;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public void setDataVencimento(LocalDate dataVencimento) {
        this.dataVencimento = dataVencimento;
    }

    public LocalDate getDataPagamento() {
        return dataPagamento;
    }

    public void setDataPagamento(LocalDate dataPagamento) {
        this.dataPagamento = dataPagamento;
    }

    public StatusParcela getStatus() {
        return status;
    }

    public void setStatus(StatusParcela status) {
        this.status = status;
    }

    public Long getPagamentoId() {
        return pagamentoId;
    }

    public void setPagamentoId(Long pagamentoId) {
        this.pagamentoId = pagamentoId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Parcela outra)) {
            return false;
        }
        if (id != null && outra.id != null) {
            return id.equals(outra.id);
        }
        return Objects.equals(reservaId, outra.reservaId) && numero == outra.numero;
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(reservaId, numero);
    }

    @Override
    public String toString() {
        return numero + "/" + totalParcelas + " - " + valor + " (" + status + ")";
    }
}
