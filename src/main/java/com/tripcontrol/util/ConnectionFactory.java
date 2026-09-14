package com.tripcontrol.util;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Ponto unico de abertura de conexoes JDBC com o PostgreSQL local (RNF05).
 *
 * <p><strong>Esqueleto: nao ha chamada a esta classe em nenhum ponto do sistema
 * nesta fase.</strong> O metodo {@link #abrirConexao()} esta propositalmente
 * comentado porque o modelo de dados ainda nao foi fechado pela equipe; ligar o
 * banco significara descomentar o corpo do metodo, criar os repositorios
 * {@code Jdbc*Repository} e trocar a montagem em {@code ContextoAplicacao}.</p>
 */
public final class ConnectionFactory {

    private static DatabaseConfig configuracao;

    private ConnectionFactory() {
    }

    /** Carrega (uma unica vez) os parametros de conexao. */
    public static synchronized DatabaseConfig getConfiguracao() {
        if (configuracao == null) {
            configuracao = DatabaseConfig.carregar();
        }
        return configuracao;
    }

    /**
     * Abre uma conexao com o banco local.
     *
     * @throws UnsupportedOperationException enquanto a persistencia real nao estiver habilitada
     */
    public static Connection abrirConexao() throws SQLException {
        throw new UnsupportedOperationException(
                "Persistencia em banco ainda nao habilitada nesta fase do projeto. "
                        + "Os dados estao nos repositorios em memoria.");

        /*
         * Implementacao prevista para a proxima fase:
         *
         * DatabaseConfig config = getConfiguracao();
         * return DriverManager.getConnection(
         *         config.getUrlJdbc(), config.getUsuario(), config.getSenha());
         */
    }
}
