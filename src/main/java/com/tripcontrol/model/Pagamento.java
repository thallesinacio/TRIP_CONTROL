package com.tripcontrol.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Recebimento registrado para uma reserva (UC05, passo 8).
 */
public class Pagamento {

    private Long id;
    private Long reservaId;
    private Integer numeroParcela;
    private BigDecimal valor;
    private LocalDate dataRecebimento;
    private FormaPagamento formaPagamento;
    private String identificadorTransacao;
    private String observacao;
    private String numeroRecibo;
    private LocalDateTime dataRegistro = LocalDateTime.now();

    public Pagamento() {
    }

    public Pagamento(Long reservaId, Integer numeroParcela, BigDecimal valor,
                     LocalDate dataRecebimento, FormaPagamento formaPagamento) {
        this.reservaId = reservaId;
        this.numeroParcela = numeroParcela;
        this.valor = valor;
        this.dataRecebimento = dataRecebimento;
        this.formaPagamento = formaPagamento;
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

    public Integer getNumeroParcela() {
        return numeroParcela;
    }

    public void setNumeroParcela(Integer numeroParcela) {
        this.numeroParcela = numeroParcela;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public LocalDate getDataRecebimento() {
        return dataRecebimento;
    }

    public void setDataRecebimento(LocalDate dataRecebimento) {
        this.dataRecebimento = dataRecebimento;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public void setFormaPagamento(FormaPagamento formaPagamento) {
        this.formaPagamento = formaPagamento;
    }

    public String getIdentificadorTransacao() {
        return identificadorTransacao;
    }

    public void setIdentificadorTransacao(String identificadorTransacao) {
        this.identificadorTransacao = identificadorTransacao;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public String getNumeroRecibo() {
        return numeroRecibo;
    }

    public void setNumeroRecibo(String numeroRecibo) {
        this.numeroRecibo = numeroRecibo;
    }

    public LocalDateTime getDataRegistro() {
        return dataRegistro;
    }

    public void setDataRegistro(LocalDateTime dataRegistro) {
        this.dataRegistro = dataRegistro;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Pagamento outro)) {
            return false;
        }
        return id != null && id.equals(outro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Pagamento{recibo=" + numeroRecibo + ", valor=" + valor + ", forma=" + formaPagamento + '}';
    }
}
