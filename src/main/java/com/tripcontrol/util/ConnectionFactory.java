package com.tripcontrol.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Ponto unico de acesso ao PostgreSQL local (RNF05).
 *
 * <p>Usa um pool de conexoes mesmo sendo uma aplicacao desktop: abrir conexao com
 * o PostgreSQL custa dezenas a centenas de milissegundos, e cada acao de tela
 * dispara pelo menos uma consulta. O pool tambem denuncia conexao esquecida
 * aberta ({@code leakDetectionThreshold}), que e o erro mais comum ao escrever
 * JDBC na mao.</p>
 */
public final class ConnectionFactory {

    /** Desktop local, poucas operacoes simultaneas: cinco conexoes sobram. */
    private static final int TAMANHO_MAXIMO_DO_POOL = 5;
    private static final long TEMPO_LIMITE_DE_CONEXAO_MS = 5_000L;
    private static final long LIMITE_DE_VAZAMENTO_MS = 10_000L;

    private static HikariDataSource dataSource;
    private static DatabaseConfig configuracao;

    private ConnectionFactory() {
    }

    /**
     * Substitui a configuracao lida do arquivo. Usado pelos testes de integracao
     * para apontar para o banco de teste sem mexer no {@code .properties}.
     * Encerra o pool anterior, se houver.
     */
    public static synchronized void configurar(DatabaseConfig novaConfiguracao) {
        encerrar();
        configuracao = novaConfiguracao;
    }

    public static synchronized DatabaseConfig getConfiguracao() {
        if (configuracao == null) {
            configuracao = DatabaseConfig.carregar();
        }
        return configuracao;
    }

    /** @return pool de conexoes, criado na primeira chamada. */
    public static synchronized DataSource getDataSource() {
        if (dataSource == null) {
            DatabaseConfig config = getConfiguracao();
            HikariConfig hikari = new HikariConfig();
            hikari.setJdbcUrl(config.getUrlJdbc());
            hikari.setUsername(config.getUsuario());
            hikari.setPassword(config.getSenha());
            hikari.setMaximumPoolSize(TAMANHO_MAXIMO_DO_POOL);
            hikari.setMinimumIdle(1);
            hikari.setConnectionTimeout(TEMPO_LIMITE_DE_CONEXAO_MS);
            hikari.setLeakDetectionThreshold(LIMITE_DE_VAZAMENTO_MS);
            hikari.setPoolName("TripControlPool");
            hikari.setAutoCommit(true);
            dataSource = new HikariDataSource(hikari);
        }
        return dataSource;
    }

    /**
     * Abre uma conexao do pool.
     *
     * <p>Repositorios nao devem chamar este metodo diretamente: usam
     * {@link Conexoes#atual()}, que devolve a conexao da transacao corrente
     * quando existe uma em andamento.</p>
     */
    public static Connection abrirConexao() throws SQLException {
        return getDataSource().getConnection();
    }

    /** Fecha o pool. Chamado no encerramento da aplicacao e entre testes. */
    public static synchronized void encerrar() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}
