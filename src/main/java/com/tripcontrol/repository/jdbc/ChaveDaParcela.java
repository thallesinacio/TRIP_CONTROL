package com.tripcontrol.repository.jdbc;

/**
 * Traducao entre o {@code Long id} que as entidades usam e a chave primaria
 * composta de {@code Parcela} ({@code numParcela + fkidReserva}).
 *
 * <p>A tabela foi modelada pela equipe com chave composta, e mudar isso exigiria
 * alterar o schema sem ganho real. Como as interfaces de repositorio do projeto
 * exigem {@code buscarPorId(Long)}, o id vira um numero derivado da propria
 * chave: {@code reservaId * 1000 + numeroDaParcela}. E estavel, reversivel e nao
 * precisa de coluna nova.</p>
 *
 * <p>O limite de 999 parcelas por reserva e folgado: um parcelamento de agencia
 * de turismo raramente passa de 12.</p>
 */
final class ChaveDaParcela {

    static final long PARCELAS_POR_RESERVA = 1000L;

    private ChaveDaParcela() {
    }

    static Long id(Long reservaId, int numero) {
        return reservaId == null ? null : reservaId * PARCELAS_POR_RESERVA + numero;
    }

    static long reservaId(long id) {
        return id / PARCELAS_POR_RESERVA;
    }

    static int numero(long id) {
        return (int) (id % PARCELAS_POR_RESERVA);
    }
}
