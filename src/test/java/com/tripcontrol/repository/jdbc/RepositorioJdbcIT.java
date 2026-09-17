package com.tripcontrol.repository.jdbc;

import com.tripcontrol.util.Conexoes;
import com.tripcontrol.util.ConnectionFactory;
import com.tripcontrol.util.DatabaseConfig;
import com.tripcontrol.util.Migracoes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Base dos testes de integracao: aponta para o banco de teste, aplica as
 * migracoes uma vez e envolve cada teste numa transacao desfeita no final.
 *
 * <p>Assim o banco de desenvolvimento nunca e sujo e um teste nao enxerga o que
 * o outro gravou. Se nao houver banco disponivel, os testes se auto-ignoram em
 * vez de quebrar a build de quem nao subiu o PostgreSQL.</p>
 *
 * <p>Configuracao por variavel de ambiente (com os padroes entre parenteses):
 * {@code TRIPCONTROL_TEST_DB_HOST} (localhost), {@code ..._PORT} (5432),
 * {@code ..._NAME} (tripcontrol_test), {@code ..._USER} (postgres),
 * {@code ..._PASSWORD} (postgres).</p>
 */
abstract class RepositorioJdbcIT {

    private static boolean bancoDisponivel;
    private static String motivoDaAusencia = "";

    private Connection conexao;

    @BeforeAll
    static void prepararBancoDeTeste() {
        ConnectionFactory.configurar(DatabaseConfig.de(
                variavel("TRIPCONTROL_TEST_DB_HOST", "localhost"),
                Integer.parseInt(variavel("TRIPCONTROL_TEST_DB_PORT", "5432")),
                variavel("TRIPCONTROL_TEST_DB_NAME", "tripcontrol_test"),
                variavel("TRIPCONTROL_TEST_DB_USER", "postgres"),
                variavel("TRIPCONTROL_TEST_DB_PASSWORD", "postgres")));
        try {
            Migracoes.aplicar(ConnectionFactory.getDataSource());
            bancoDisponivel = true;
        } catch (RuntimeException falha) {
            bancoDisponivel = false;
            motivoDaAusencia = falha.getMessage();
        }
    }

    @BeforeEach
    void abrirTransacaoDoTeste() throws SQLException {
        assumeTrue(bancoDisponivel, "Banco de teste indisponivel: " + motivoDaAusencia);
        conexao = ConnectionFactory.abrirConexao();
        conexao.setAutoCommit(false);
        Conexoes.fixarParaTeste(conexao);
    }

    @AfterEach
    void desfazerTransacaoDoTeste() throws SQLException {
        Conexoes.limparParaTeste();
        if (conexao != null) {
            conexao.rollback();
            conexao.close();
            conexao = null;
        }
    }

    @AfterAll
    static void encerrarPool() {
        ConnectionFactory.encerrar();
    }

    private static String variavel(String nome, String padrao) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
