package com.tripcontrol.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Leitura dos parametros de conexao do banco relacional local (RNF05).
 *
 * <p><strong>Nao utilizada nesta fase.</strong> O modelo de dados ainda esta sendo
 * desenhado pela equipe, entao os repositorios em memoria seguem sendo a unica
 * implementacao ativa. A classe ja existe para que a virada para o PostgreSQL
 * seja apenas a criacao dos repositorios JDBC e a troca da montagem em
 * {@code ContextoAplicacao}.</p>
 *
 * <p>A configuracao vem de {@code src/main/resources/config/database.properties}
 * e cada chave pode ser sobrescrita por variavel de ambiente
 * ({@code TRIPCONTROL_DB_HOST}, {@code TRIPCONTROL_DB_PORT}, {@code TRIPCONTROL_DB_NAME},
 * {@code TRIPCONTROL_DB_USER}, {@code TRIPCONTROL_DB_PASSWORD}).</p>
 */
public final class DatabaseConfig {

    private static final String ARQUIVO = "/config/database.properties";

    private final String host;
    private final int porta;
    private final String nomeBanco;
    private final String usuario;
    private final String senha;

    private DatabaseConfig(String host, int porta, String nomeBanco, String usuario, String senha) {
        this.host = host;
        this.porta = porta;
        this.nomeBanco = nomeBanco;
        this.usuario = usuario;
        this.senha = senha;
    }

    /** Carrega o arquivo de propriedades aplicando as variaveis de ambiente por cima. */
    public static DatabaseConfig carregar() {
        Properties propriedades = new Properties();
        try (InputStream entrada = DatabaseConfig.class.getResourceAsStream(ARQUIVO)) {
            if (entrada != null) {
                propriedades.load(entrada);
            }
        } catch (IOException excecao) {
            throw new IllegalStateException("Falha ao ler " + ARQUIVO, excecao);
        }
        return new DatabaseConfig(
                valor(propriedades, "db.host", "TRIPCONTROL_DB_HOST", "localhost"),
                Integer.parseInt(valor(propriedades, "db.port", "TRIPCONTROL_DB_PORT", "5432")),
                valor(propriedades, "db.name", "TRIPCONTROL_DB_NAME", "tripcontrol"),
                valor(propriedades, "db.user", "TRIPCONTROL_DB_USER", "postgres"),
                valor(propriedades, "db.password", "TRIPCONTROL_DB_PASSWORD", ""));
    }

    private static String valor(Properties propriedades, String chave, String variavelAmbiente, String padrao) {
        String doAmbiente = System.getenv(variavelAmbiente);
        if (doAmbiente != null && !doAmbiente.isBlank()) {
            return doAmbiente;
        }
        return propriedades.getProperty(chave, padrao);
    }

    /** @return URL JDBC no formato {@code jdbc:postgresql://host:porta/banco}. */
    public String getUrlJdbc() {
        return "jdbc:postgresql://" + host + ":" + porta + "/" + nomeBanco;
    }

    public String getHost() {
        return host;
    }

    public int getPorta() {
        return porta;
    }

    public String getNomeBanco() {
        return nomeBanco;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getSenha() {
        return senha;
    }
}
