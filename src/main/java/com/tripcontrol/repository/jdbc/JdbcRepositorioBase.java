package com.tripcontrol.repository.jdbc;

import com.tripcontrol.repository.RepositorioException;
import com.tripcontrol.util.Conexoes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Encanamento comum das implementacoes JDBC: abrir conexao, preencher o
 * {@link PreparedStatement}, percorrer o {@link ResultSet} e traduzir
 * {@link SQLException}.
 *
 * <p>De proposito nao e um mini-ORM: o SQL e o mapeamento de cada entidade ficam
 * explicitos no repositorio correspondente. O projeto decidiu nao usar ORM, e
 * reinventar um pela metade daria o pior dos dois mundos.</p>
 */
public abstract class JdbcRepositorioBase {

    /** Converte a linha corrente do {@link ResultSet} em objeto de dominio. */
    @FunctionalInterface
    protected interface Mapeador<T> {
        T mapear(ResultSet resultado) throws SQLException;
    }

    /** Nome da operacao usado nas mensagens de erro (ex.: "salvar pacote"). */
    protected abstract String nomeDaEntidade();

    protected <T> Optional<T> buscarUm(String sql, Mapeador<T> mapeador, Object... parametros) {
        try (Connection conexao = Conexoes.atual();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            preencher(comando, parametros);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? Optional.of(mapeador.mapear(resultado)) : Optional.empty();
            }
        } catch (SQLException excecao) {
            throw RepositorioException.traduzir("consultar " + nomeDaEntidade(), excecao);
        }
    }

    protected <T> List<T> buscarLista(String sql, Mapeador<T> mapeador, Object... parametros) {
        try (Connection conexao = Conexoes.atual();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            preencher(comando, parametros);
            try (ResultSet resultado = comando.executeQuery()) {
                List<T> encontrados = new ArrayList<>();
                while (resultado.next()) {
                    encontrados.add(mapeador.mapear(resultado));
                }
                return encontrados;
            }
        } catch (SQLException excecao) {
            throw RepositorioException.traduzir("listar " + nomeDaEntidade(), excecao);
        }
    }

    /** @return quantidade de linhas afetadas. */
    protected int executar(String operacao, String sql, Object... parametros) {
        try (Connection conexao = Conexoes.atual();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            preencher(comando, parametros);
            return comando.executeUpdate();
        } catch (SQLException excecao) {
            throw RepositorioException.traduzir(operacao + " " + nomeDaEntidade(), excecao);
        }
    }

    /** Executa um INSERT com {@code RETURNING} e devolve a chave gerada. */
    protected long inserirRetornandoChave(String sql, Object... parametros) {
        try (Connection conexao = Conexoes.atual();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            preencher(comando, parametros);
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    throw new RepositorioException(
                            "O banco nao devolveu a chave gerada ao salvar " + nomeDaEntidade() + ".", null);
                }
                return resultado.getLong(1);
            }
        } catch (SQLException excecao) {
            throw RepositorioException.traduzir("salvar " + nomeDaEntidade(), excecao);
        }
    }

    /** Consulta que devolve um unico valor escalar (contagens, sequences). */
    protected <T> T consultarEscalar(String sql, Mapeador<T> mapeador, Object... parametros) {
        return buscarUm(sql, mapeador, parametros)
                .orElseThrow(() -> new RepositorioException(
                        "Consulta sem resultado ao processar " + nomeDaEntidade() + ".", null));
    }

    /**
     * Le uma coluna inteira como {@link Long}, preservando nulo.
     *
     * <p>Existe porque {@code getObject(coluna, Long.class)} nao funciona sobre
     * colunas {@code serial}/{@code integer} do PostgreSQL (o driver recusa a
     * conversao de {@code int4} para {@code Long}); {@code getLong} aceita
     * qualquer tipo inteiro, e {@code wasNull} devolve a distincao entre zero e
     * ausencia de valor.</p>
     */
    protected static Long lerLongOuNulo(ResultSet resultado, String coluna) throws SQLException {
        long valor = resultado.getLong(coluna);
        return resultado.wasNull() ? null : valor;
    }

    /**
     * Preenche os parametros na ordem.
     *
     * <p>Datas e horas sao passadas como {@code java.time} direto (JDBC 4.2), sem
     * as classes antigas {@code java.sql.Date}/{@code Timestamp}.</p>
     */
    protected void preencher(PreparedStatement comando, Object... parametros) throws SQLException {
        if (parametros == null) {
            return;
        }
        for (int posicao = 0; posicao < parametros.length; posicao++) {
            Object valor = parametros[posicao];
            if (valor == null) {
                comando.setNull(posicao + 1, Types.NULL);
            } else {
                comando.setObject(posicao + 1, valor);
            }
        }
    }
}
