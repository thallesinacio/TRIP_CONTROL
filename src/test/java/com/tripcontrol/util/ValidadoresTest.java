package com.tripcontrol.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica as regras de formato usadas pelas validacoes dos casos de uso. */
class ValidadoresTest {

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725", "111.444.777-35"})
    @DisplayName("CPFs com digitos verificadores corretos sao aceitos")
    void aceitaCpfValido(String cpf) {
        assertTrue(ValidadorCpf.isValido(cpf));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123.456.789-00", "111.111.111-11", "1234567890", ""})
    @DisplayName("CPFs invalidos ou repetidos sao recusados")
    void recusaCpfInvalido(String cpf) {
        assertFalse(ValidadorCpf.isValido(cpf));
    }

    @Test
    @DisplayName("CPF e formatado com a mascara padrao")
    void formataCpf() {
        assertEquals("529.982.247-25", ValidadorCpf.formatar("52998224725"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"(87) 99999-0000", "8799999 0000", "(81) 3333-4444"})
    @DisplayName("Telefones fixos e celulares com DDD sao aceitos")
    void aceitaTelefoneValido(String telefone) {
        assertTrue(ValidadorTelefone.isValido(telefone));
    }

    @ParameterizedTest
    @ValueSource(strings = {"99999-0000", "(00) 99999-0000", "(87) 89999-0000"})
    @DisplayName("Telefones sem DDD valido ou sem o nono digito sao recusados")
    void recusaTelefoneInvalido(String telefone) {
        assertFalse(ValidadorTelefone.isValido(telefone));
    }

    @Test
    @DisplayName("Valores monetarios aceitam os formatos digitados nas telas")
    void leValoresMonetarios() {
        assertEquals(new BigDecimal("3490.00"), Formatadores.lerValorMonetario("R$ 3.490,00").orElseThrow());
        assertEquals(new BigDecimal("3490.00"), Formatadores.lerValorMonetario("3490,00").orElseThrow());
        assertEquals(new BigDecimal("3490.00"), Formatadores.lerValorMonetario("3490.00").orElseThrow());
        assertTrue(Formatadores.lerValorMonetario("abc").isEmpty());
    }

    @Test
    @DisplayName("Datas seguem o padrao brasileiro nas duas direcoes")
    void converteDatas() {
        assertEquals(LocalDate.of(2026, 5, 5), Formatadores.lerData("05/05/2026").orElseThrow());
        assertEquals("05/05/2026", Formatadores.formatarData(LocalDate.of(2026, 5, 5)));
        assertTrue(Formatadores.lerData("31/02/2026").isEmpty());
    }

    @Test
    @DisplayName("Hash de senha nao expoe o texto puro e valida a conferencia")
    void gerenciaSenhas() {
        String hash = SenhaUtils.gerarHash("tripcontrol");

        assertFalse(hash.contains("tripcontrol"));
        assertTrue(SenhaUtils.conferir("tripcontrol", hash));
        assertFalse(SenhaUtils.conferir("outra", hash));
    }

    @Test
    @DisplayName("formatarMoeda e lerValorMonetario fecham o ciclo, inclusive com o espaco do pt-BR")
    void valorMonetarioFazIdaEVolta() {
        java.math.BigDecimal valor = new java.math.BigDecimal("1500.00");
        String formatado = Formatadores.formatarMoeda(valor);

        // NumberFormat em pt-BR usa espaco inquebravel depois do "R$"; se lerValor
        // nao o tratar, somar valores vindos da tela devolve zero silenciosamente.
        assertEquals(0, valor.compareTo(Formatadores.lerValorMonetario(formatado).orElseThrow()));
        assertEquals(0, new java.math.BigDecimal("3490.00").compareTo(
                Formatadores.lerValorMonetario("R$\u00a03.490,00").orElseThrow()));
    }

    @Test
    @DisplayName("Percentual sai no padrao brasileiro, com virgula")
    void formatacaoDePercentual() {
        assertEquals("35,7%", Formatadores.formatarPercentual(new java.math.BigDecimal("35.74")));
        assertEquals("100,0%", Formatadores.formatarPercentual(new java.math.BigDecimal("100")));
        assertEquals("-", Formatadores.formatarPercentual(null));
    }

    @Test
    @DisplayName("Horas do itinerario aceitam as formas digitadas na tela")
    void leituraDeHoras() {
        assertEquals(java.time.LocalTime.of(8, 30), Formatadores.lerHora("08:30").orElseThrow());
        assertEquals(java.time.LocalTime.of(8, 30), Formatadores.lerHora("8:30").orElseThrow());
        assertEquals(java.time.LocalTime.of(14, 0), Formatadores.lerHora("14").orElseThrow());
        assertTrue(Formatadores.lerHora("25:00").isEmpty());
        assertTrue(Formatadores.lerHora("abc").isEmpty());
    }
}
