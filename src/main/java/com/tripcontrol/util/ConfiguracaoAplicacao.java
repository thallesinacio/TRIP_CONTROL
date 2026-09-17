package com.tripcontrol.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuracao geral da aplicacao, hoje com uma unica chave: qual implementacao
 * de repositorio usar.
 *
 * <p>Manter as duas implementacoes selecionaveis serve a dois propositos: permite
 * rodar a suite de testes sem exigir um PostgreSQL instalado e da um plano B se o
 * banco falhar na maquina onde o projeto for apresentado.</p>
 */
public final class ConfiguracaoAplicacao {

    private static final String ARQUIVO = "/config/aplicacao.properties";
    private static final String CHAVE = "repositorio.tipo";
    private static final String VARIAVEL_AMBIENTE = "TRIPCONTROL_REPOSITORIO";

    /** Implementacoes de repositorio disponiveis. */
    public enum TipoRepositorio {
        MEMORIA, JDBC;

        static TipoRepositorio doTexto(String texto) {
            if (texto == null || texto.isBlank()) {
                return MEMORIA;
            }
            return "jdbc".equalsIgnoreCase(texto.trim()) ? JDBC : MEMORIA;
        }
    }

    private ConfiguracaoAplicacao() {
    }

    /** @return tipo configurado; MEMORIA quando nada foi informado. */
    public static TipoRepositorio tipoDeRepositorio() {
        String doAmbiente = System.getenv(VARIAVEL_AMBIENTE);
        if (doAmbiente != null && !doAmbiente.isBlank()) {
            return TipoRepositorio.doTexto(doAmbiente);
        }
        String daPropriedadeDeSistema = System.getProperty(CHAVE);
        if (daPropriedadeDeSistema != null && !daPropriedadeDeSistema.isBlank()) {
            return TipoRepositorio.doTexto(daPropriedadeDeSistema);
        }

        Properties propriedades = new Properties();
        try (InputStream entrada = ConfiguracaoAplicacao.class.getResourceAsStream(ARQUIVO)) {
            if (entrada != null) {
                propriedades.load(entrada);
            }
        } catch (IOException excecao) {
            throw new IllegalStateException("Falha ao ler " + ARQUIVO, excecao);
        }
        return TipoRepositorio.doTexto(propriedades.getProperty(CHAVE));
    }
}
