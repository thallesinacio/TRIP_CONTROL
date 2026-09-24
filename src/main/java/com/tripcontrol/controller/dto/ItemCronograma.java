package com.tripcontrol.controller.dto;

import com.tripcontrol.model.ItemItinerario;
import com.tripcontrol.model.Recurso;
import com.tripcontrol.model.TipoItemItinerario;
import com.tripcontrol.util.Formatadores;

/**
 * Cartao do "Cronograma Atual do Roteiro" (UC06, passo 4): o item, o recurso que
 * ele usa e a marca de rascunho.
 *
 * @param rascunho {@code true} enquanto o item existe apenas na tela. Um item so
 *                 deixa de ser rascunho quando "Finalizar Itinerario" grava o
 *                 conjunto e ele passa a apontar para um itinerario com id.
 */
public record ItemCronograma(ItemItinerario item, Recurso recurso, boolean rascunho) {

    /** Texto da tag colorida por tipo: TRANSPORTE, HOSPEDAGEM ou ATIVIDADE. */
    public String tag() {
        return item.getTipo() == null ? "" : item.getTipo().name();
    }

    public String titulo() {
        if (item.getNomeServico() != null && !item.getNomeServico().isBlank()) {
            return item.getNomeServico();
        }
        return recurso == null ? "-" : recurso.getNome();
    }

    /** Linha de detalhes com os rotulos proprios de cada tipo, como no prototipo. */
    public String detalhes() {
        String inicio = Formatadores.formatarMomento(item.getInicio());
        String fim = Formatadores.formatarMomento(item.getFim());

        if (item.getTipo() == TipoItemItinerario.HOSPEDAGEM) {
            String texto = "Check-in: " + inicio + " | Check-out: " + fim;
            String local = recurso == null ? null : recurso.getLocal();
            return local == null || local.isBlank() ? texto : texto + " | Local: " + local;
        }
        if (item.getTipo() == TipoItemItinerario.TRANSPORTE) {
            return "Saída: " + inicio + " (" + textoOuTraco(item.getLocalOrigem()) + ")"
                    + " | Chegada: " + fim + " (" + textoOuTraco(item.getLocalDestino()) + ")";
        }
        return "Data: " + Formatadores.formatarData(item.getInicio().toLocalDate())
                + " | Horário: " + Formatadores.formatarHora(item.getInicio().toLocalTime())
                + "h às " + Formatadores.formatarHora(item.getFim().toLocalTime()) + "h"
                + " | Local de Encontro: " + textoOuTraco(item.getLocalDestino());
    }

    private static String textoOuTraco(String texto) {
        return texto == null || texto.isBlank() ? "-" : texto;
    }
}
