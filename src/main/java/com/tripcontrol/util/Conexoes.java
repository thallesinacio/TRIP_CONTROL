package com.tripcontrol.util;

import com.tripcontrol.repository.RepositorioException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Unidade de trabalho da aplicacao: delimita transacoes sem que as interfaces de
 * repositorio precisem receber uma {@code Connection}.
 *
 * <p>Enquanto {@link #emTransacao(Supplier)} esta em andamento, a conexao fica
 * guardada na thread corrente e todo repositorio que chamar {@link #atual()} usa
 * a mesma conexao — por isso o cancelamento de reserva (UC07) e o registro de
 * reserva (UC03) conseguem ser atomicos mesmo tocando em mais de uma tabela.
 * Fora de uma transacao, cada chamada pega sua propria conexao do pool, em
 * autocommit.</p>
 *
 * <p>Com os repositorios em memoria nao existe conexao alguma: {@code emTransacao}
 * simplesmente executa o bloco, o que mantem os dois modos intercambiaveis.</p>
 *
 * <p><strong>Limite conhecido:</strong> a transacao vale para a thread que a
 * abriu. Numa aplicacao desktop com uma thread de interface isso e suficiente;
 * se um dia houver processamento paralelo, cada thread precisara da sua.</p>
 */
public final class Conexoes {

    private static final ThreadLocal<Connection> CONEXAO_DA_THREAD = new ThreadLocal<>();

    private Conexoes() {
    }

    /**
     * Conexao que os repositorios devem usar.
     *
     * <p>Dentro de uma transacao devolve um envoltorio cujo {@code close()} nao
     * faz nada, para que o {@code try-with-resources} do repositorio nao encerre
     * a transacao no meio.</p>
     */
    public static Connection atual() throws SQLException {
        Connection emTransacao = CONEXAO_DA_THREAD.get();
        return emTransacao != null ? naoFechavel(emTransacao) : ConnectionFactory.abrirConexao();
    }

    public static boolean existeTransacaoAtiva() {
        return CONEXAO_DA_THREAD.get() != null;
    }

    /**
     * Executa a operacao dentro de uma transacao: confirma no fim ou desfaz
     * tudo se qualquer excecao subir.
     *
     * <p>Chamadas aninhadas participam da transacao ja aberta em vez de criar
     * outra.</p>
     */
    public static <T> T emTransacao(Supplier<T> operacao) {
        if (CONEXAO_DA_THREAD.get() != null || !ConfiguracaoAplicacao.TipoRepositorio.JDBC
                .equals(ConfiguracaoAplicacao.tipoDeRepositorio())) {
            // Ja existe transacao em andamento, ou a aplicacao esta em memoria:
            // nos dois casos basta executar o bloco.
            return operacao.get();
        }

        try (Connection conexao = ConnectionFactory.abrirConexao()) {
            boolean autocommitAnterior = conexao.getAutoCommit();
            conexao.setAutoCommit(false);
            CONEXAO_DA_THREAD.set(conexao);
            try {
                T resultado = operacao.get();
                conexao.commit();
                return resultado;
            } catch (RuntimeException erro) {
                desfazer(conexao);
                throw erro;
            } finally {
                CONEXAO_DA_THREAD.remove();
                conexao.setAutoCommit(autocommitAnterior);
            }
        } catch (SQLException excecao) {
            throw new RepositorioException("Falha ao controlar a transacao.", excecao);
        }
    }

    /** Fixa uma conexao para a thread. Existe para os testes de integracao. */
    public static void fixarParaTeste(Connection conexao) {
        CONEXAO_DA_THREAD.set(conexao);
    }

    /** Remove a conexao fixada por {@link #fixarParaTeste(Connection)}. */
    public static void limparParaTeste() {
        CONEXAO_DA_THREAD.remove();
    }

    private static void desfazer(Connection conexao) {
        try {
            conexao.rollback();
        } catch (SQLException falhaAoDesfazer) {
            throw new RepositorioException("Falha ao desfazer a transacao.", falhaAoDesfazer);
        }
    }

    /** Envoltorio que ignora {@code close()} para preservar a transacao. */
    private static Connection naoFechavel(Connection conexao) {
        return (Connection) Proxy.newProxyInstance(
                Conexoes.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, metodo, argumentos) -> {
                    if ("close".equals(metodo.getName())) {
                        return null;
                    }
                    try {
                        return metodo.invoke(conexao, argumentos);
                    } catch (InvocationTargetException erro) {
                        throw erro.getCause();
                    }
                });
    }
}
