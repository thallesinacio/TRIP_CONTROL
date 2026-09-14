package com.tripcontrol.controller.dto;

import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Reserva;

import java.math.BigDecimal;

/**
 * Reserva acompanhada do cliente e do pacote correspondentes, usada no
 * comprovante do UC03 (passo 6) e nas listagens das demais telas.
 */
public record ResumoReserva(Reserva reserva, Cliente cliente, Pacote pacote) {

    public String codigo() {
        return reserva.getCodigo();
    }

    public String nomeCliente() {
        return cliente == null ? "-" : cliente.getNome();
    }

    public String destino() {
        return pacote == null ? "-" : pacote.getDestino();
    }

    public BigDecimal valorTotal() {
        return reserva.getValorTotal();
    }
}
