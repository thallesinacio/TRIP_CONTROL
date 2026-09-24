package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.Recurso;
import com.tripcontrol.model.TipoItemItinerario;
import com.tripcontrol.repository.RecursoRepository;
import com.tripcontrol.repository.RepositorioException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Catalogo de hospedagens, transportes e atividades reutilizaveis (UC06).
 *
 * <p>A tabela {@code Recurso} nasce na V3. Ela guarda o <em>cadastro</em> do
 * fornecedor, enquanto {@code Hospedagem}, {@code Transporte} e {@code Atividade}
 * guardam a <em>ocorrencia</em> dele dentro de um pacote — a mesma relacao que
 * {@code Pacote} tem com {@code Reserva}.</p>
 */
public class JdbcRecursoRepository extends JdbcRepositorioBase implements RecursoRepository {

    private static final String SELECAO =
            "select idRecurso, tipo, nome, local, contato, descricao from Recurso";

    @Override
    protected String nomeDaEntidade() {
        return "recurso";
    }

    @Override
    public Recurso salvar(Recurso recurso) {
        exigirTipo(recurso);
        long id = inserirRetornandoChave("""
                insert into Recurso (tipo, nome, local, contato, descricao)
                values (?, ?, ?, ?, ?)
                returning idRecurso
                """,
                recurso.getTipo().name(), recurso.getNome(), recurso.getLocal(),
                recurso.getContato(), recurso.getDescricao());
        recurso.setId(id);
        return recurso;
    }

    @Override
    public Recurso atualizar(Recurso recurso) {
        exigirTipo(recurso);
        int linhas = executar("atualizar", """
                update Recurso set tipo = ?, nome = ?, local = ?, contato = ?, descricao = ?
                 where idRecurso = ?
                """,
                recurso.getTipo().name(), recurso.getNome(), recurso.getLocal(),
                recurso.getContato(), recurso.getDescricao(), recurso.getId());

        if (linhas == 0) {
            throw new RepositorioException("Recurso inexistente para atualizacao: id=" + recurso.getId(), null);
        }
        return recurso;
    }

    @Override
    public Optional<Recurso> buscarPorId(Long id) {
        return id == null ? Optional.empty()
                : buscarUm(SELECAO + " where idRecurso = ?", this::mapear, id);
    }

    @Override
    public List<Recurso> buscarTodos() {
        return buscarLista(SELECAO + " order by tipo, nome", this::mapear);
    }

    @Override
    public List<Recurso> buscarPorTipo(TipoItemItinerario tipo) {
        if (tipo == null) {
            return buscarTodos();
        }
        return buscarLista(SELECAO + " where tipo = ? order by nome", this::mapear, tipo.name());
    }

    @Override
    public boolean remover(Long id) {
        return id != null && executar("remover", "delete from Recurso where idRecurso = ?", id) > 0;
    }

    @Override
    public long contar() {
        return consultarEscalar("select count(*) from Recurso", resultado -> resultado.getLong(1));
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private Recurso mapear(ResultSet resultado) throws SQLException {
        Recurso recurso = new Recurso();
        recurso.setId(lerLongOuNulo(resultado, "idrecurso"));
        recurso.setTipo(TipoItemItinerario.valueOf(resultado.getString("tipo")));
        recurso.setNome(resultado.getString("nome"));
        recurso.setLocal(resultado.getString("local"));
        recurso.setContato(resultado.getString("contato"));
        recurso.setDescricao(resultado.getString("descricao"));
        return recurso;
    }

    private static void exigirTipo(Recurso recurso) {
        if (recurso.getTipo() == null) {
            throw new RepositorioException(
                    "O tipo do recurso e obrigatorio: ele define em qual lista o cadastro aparece.", null);
        }
    }
}
