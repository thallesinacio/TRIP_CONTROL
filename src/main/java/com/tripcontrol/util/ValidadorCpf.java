package com.tripcontrol.util;

/**
 * Validacao e formatacao de CPF (UC02, FA01).
 * Alem do formato, os digitos verificadores sao conferidos, o que evita
 * cadastros com numeros sintaticamente corretos porem invalidos.
 */
public final class ValidadorCpf {

    private ValidadorCpf() {
    }

    /** Remove qualquer caractere que nao seja digito. */
    public static String apenasDigitos(String cpf) {
        return cpf == null ? "" : cpf.replaceAll("\\D", "");
    }

    /** @return {@code true} se o CPF possui 11 digitos e digitos verificadores validos. */
    public static boolean isValido(String cpf) {
        String numeros = apenasDigitos(cpf);
        if (numeros.length() != 11 || numeros.chars().distinct().count() == 1) {
            return false;
        }
        return digitoVerificador(numeros, 9) == Character.getNumericValue(numeros.charAt(9))
                && digitoVerificador(numeros, 10) == Character.getNumericValue(numeros.charAt(10));
    }

    /** Aplica a mascara 000.000.000-00; devolve o valor original se nao houver 11 digitos. */
    public static String formatar(String cpf) {
        String numeros = apenasDigitos(cpf);
        if (numeros.length() != 11) {
            return cpf == null ? "" : cpf;
        }
        return numeros.substring(0, 3) + "." + numeros.substring(3, 6) + "."
                + numeros.substring(6, 9) + "-" + numeros.substring(9);
    }

    private static int digitoVerificador(String numeros, int quantidadeDeDigitos) {
        int soma = 0;
        int peso = quantidadeDeDigitos + 1;
        for (int i = 0; i < quantidadeDeDigitos; i++) {
            soma += Character.getNumericValue(numeros.charAt(i)) * peso--;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
