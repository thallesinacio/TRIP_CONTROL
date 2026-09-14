package com.tripcontrol.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Geracao e conferencia de hash de senha (RNF04).
 * O sistema nunca armazena a senha em texto puro: apenas o hash BCrypt,
 * que ja embute o salt aleatorio no proprio resultado.
 */
public final class SenhaUtils {

    /** Custo do BCrypt: equilibrio entre seguranca e tempo de resposta em desktop. */
    private static final int FATOR_CUSTO = 10;

    private SenhaUtils() {
    }

    public static String gerarHash(String senhaEmTextoPuro) {
        if (senhaEmTextoPuro == null || senhaEmTextoPuro.isBlank()) {
            throw new IllegalArgumentException("Senha nao pode ser vazia.");
        }
        return BCrypt.hashpw(senhaEmTextoPuro, BCrypt.gensalt(FATOR_CUSTO));
    }

    public static boolean conferir(String senhaEmTextoPuro, String hashArmazenado) {
        if (senhaEmTextoPuro == null || hashArmazenado == null || hashArmazenado.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(senhaEmTextoPuro, hashArmazenado);
        } catch (IllegalArgumentException excecao) {
            return false;
        }
    }
}
