package com.tripcontrol.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Optional;

/**
 * Conversao entre o texto digitado nas telas e os tipos do dominio,
 * sempre no padrao brasileiro (dd/MM/yyyy e R$ 0.000,00).
 */
public final class Formatadores {

    public static final Locale BRASIL = Locale.forLanguageTag("pt-BR");

    /**
     * Formato de data do sistema.
     *
     * <p>Usa {@link ResolverStyle#STRICT} (e por isso o padrao de ano e {@code uuuu},
     * exigido pelo modo estrito) para que datas inexistentes sejam recusadas em vez
     * de silenciosamente ajustadas: no modo padrao, "31/02/2026" viraria 28/02/2026,
     * o que faria o sistema aceitar um periodo que o funcionario nao digitou.</p>
     */
    public static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    public static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT);

    private Formatadores() {
    }

    public static String formatarData(LocalDate data) {
        return data == null ? "" : data.format(DATA);
    }

    public static String formatarDataHora(LocalDateTime dataHora) {
        return dataHora == null ? "" : dataHora.format(DATA_HORA);
    }

    /** Converte "dd/MM/yyyy" em {@link LocalDate}; vazio quando o texto e invalido. */
    public static Optional<LocalDate> lerData(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(texto.trim(), DATA));
        } catch (DateTimeParseException excecao) {
            return Optional.empty();
        }
    }

    public static String formatarMoeda(BigDecimal valor) {
        BigDecimal seguro = valor == null ? BigDecimal.ZERO : valor;
        return NumberFormat.getCurrencyInstance(BRASIL).format(seguro);
    }

    /**
     * Converte texto monetario em {@link BigDecimal}, aceitando as formas
     * "R$ 3.490,00", "3490,00" e "3490.00".
     */
    public static Optional<BigDecimal> lerValorMonetario(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        // O \u00A0 (espaco inquebravel) e o que NumberFormat em pt-BR coloca depois do
        // "R$". Sem tira-lo, formatarMoeda e lerValorMonetario nao fecham o ciclo.
        String limpo = texto.replace("R$", "").replaceAll("[\\s\\u00A0]", "").trim();
        if (limpo.contains(",")) {
            limpo = limpo.replace(".", "").replace(",", ".");
        }
        try {
            return Optional.of(new BigDecimal(limpo).setScale(2, RoundingMode.HALF_UP));
        } catch (NumberFormatException excecao) {
            return Optional.empty();
        }
    }

    /** Converte texto em inteiro; vazio quando o texto nao e um numero inteiro. */
    public static Optional<Integer> lerInteiro(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(texto.trim()));
        } catch (NumberFormatException excecao) {
            return Optional.empty();
        }
    }

    /** Devolve {@code null} quando o texto e nulo ou so possui espacos. */
    public static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    // ------------------------------------------------------------------
    // Horas e datas/horas compostas (UC06)
    // ------------------------------------------------------------------

    /** Formato de hora das telas de itinerario: HH:mm, 24 horas. */
    public static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

    public static String formatarHora(LocalTime hora) {
        return hora == null ? "" : hora.format(HORA);
    }

    /**
     * Converte "HH:mm" em {@link LocalTime}; vazio quando o texto e invalido.
     * Aceita tambem "HH:mm:ss" e a forma "8:30", completando o zero a esquerda.
     */
    public static Optional<LocalTime> lerHora(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String limpo = texto.trim().replace('h', ':').replace('H', ':');
        if (limpo.endsWith(":")) {
            limpo = limpo.substring(0, limpo.length() - 1);
        }
        if (limpo.matches("\\d:\\d{2}")) {
            limpo = "0" + limpo;
        }
        if (limpo.matches("\\d{1,2}")) {
            limpo = (limpo.length() == 1 ? "0" + limpo : limpo) + ":00";
        }
        try {
            return Optional.of(LocalTime.parse(limpo, HORA));
        } catch (DateTimeParseException excecao) {
            return Optional.empty();
        }
    }

    /**
     * Junta os textos de data e hora digitados em campos separados.
     * Vazio quando qualquer um dos dois for invalido ou nao informado.
     */
    public static Optional<LocalDateTime> lerDataHora(String data, String hora) {
        Optional<LocalDate> dia = lerData(data);
        Optional<LocalTime> instante = lerHora(hora);
        if (dia.isEmpty() || instante.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.of(dia.get(), instante.get()));
    }

    /** Percentual no padrao brasileiro, com uma casa decimal: "73,3%". */
    public static String formatarPercentual(BigDecimal valor) {
        if (valor == null) {
            return "-";
        }
        return valor.setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%";
    }

    /** "05/05/2026 08:30h", formato usado nos cartoes do cronograma (UC06). */
    public static String formatarMomento(LocalDateTime momento) {
        return momento == null ? "" : formatarDataHora(momento) + "h";
    }
}
