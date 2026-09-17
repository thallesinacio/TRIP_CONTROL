package com.tripcontrol.repository;

import java.sql.SQLException;

/**
 * Falha de infraestrutura de persistencia.
 *
 * <p>Existe para que detalhes de JDBC nao vazem para Controllers e Views: um
 * {@code SQLException} vira esta excecao, que a camada de aplicacao trata como
 * "nao foi possivel concluir" (por exemplo, o FA06 do UC07 e o FA04 do UC08).</p>
 */
public class RepositorioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** SQLState 23505 do PostgreSQL: violacao de restricao de unicidade. */
    private static final String VIOLACAO_DE_UNICIDADE = "23505";

    public RepositorioException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

    public static RepositorioException traduzir(String operacao, SQLException causa) {
        return new RepositorioException("Falha ao " + operacao + ": " + causa.getMessage(), causa);
    }

    /**
     * @return {@code true} quando a falha foi uma duplicidade barrada pelo banco
     *         (a corrida rara em que duas instancias gravam o mesmo registro ao
     *         mesmo tempo, depois de ambas terem passado pela checagem previa)
     */
    public boolean isViolacaoDeUnicidade() {
        return getCause() instanceof SQLException sql && VIOLACAO_DE_UNICIDADE.equals(sql.getSQLState());
    }
}
