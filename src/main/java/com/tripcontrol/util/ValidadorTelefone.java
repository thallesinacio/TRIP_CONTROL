package com.tripcontrol.util;

/** Validacao e formatacao de telefone brasileiro com DDD (UC02, FA01). */
public final class ValidadorTelefone {

    private ValidadorTelefone() {
    }

    public static String apenasDigitos(String telefone) {
        return telefone == null ? "" : telefone.replaceAll("\\D", "");
    }

    /** Aceita telefone fixo (10 digitos) ou celular (11 digitos), sempre com DDD. */
    public static boolean isValido(String telefone) {
        String numeros = apenasDigitos(telefone);
        if (numeros.length() != 10 && numeros.length() != 11) {
            return false;
        }
        int ddd = Integer.parseInt(numeros.substring(0, 2));
        if (ddd < 11 || ddd > 99) {
            return false;
        }
        return numeros.length() != 11 || numeros.charAt(2) == '9';
    }

    /** Aplica a mascara (00) 00000-0000 ou (00) 0000-0000. */
    public static String formatar(String telefone) {
        String numeros = apenasDigitos(telefone);
        if (numeros.length() == 11) {
            return "(" + numeros.substring(0, 2) + ") " + numeros.substring(2, 7) + "-" + numeros.substring(7);
        }
        if (numeros.length() == 10) {
            return "(" + numeros.substring(0, 2) + ") " + numeros.substring(2, 6) + "-" + numeros.substring(6);
        }
        return telefone == null ? "" : telefone;
    }
}
