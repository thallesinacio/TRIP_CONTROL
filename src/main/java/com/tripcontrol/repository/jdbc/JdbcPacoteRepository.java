package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.AlteracaoCapacidade;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.repository.PacoteRepository;
import com.tripcontrol.repository.RepositorioException;
import com.tripcontrol.util.Conexoes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistencia de pacotes de viagem (UC01, UC04). */
public class JdbcPacoteRepository extends JdbcRepositorioBase implements PacoteRepository {

    private static final String COLUNAS = """
            codPacote, codigo, destino, dataInicio, dataFim, descricao, roteiro,
            capacidadeAtual, preco, dataCadastro
            """;

    @Override
    protected String nomeDaEntidade() {
        return "pacote";
    }

    @Override
    public Pacote salvar(Pacote pacote) {
        long id = inserirRetornandoChave("""
                insert into Pacote (codigo, destino, dataInicio, dataFim, descricao, roteiro,
                                    capacidadeAtual, preco, dataCadastro)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning codPacote
                """,
                pacote.getCodigo(), pacote.getDestino(), pacote.getDataInicio(), pacote.getDataFim(),
                pacote.getDescricao(), pacote.getRoteiroPrevisto(), pacote.getCapacidadeTotal(),
                pacote.getPreco(), dataDeCadastro(pacote));

        pacote.setId(id);
        gravarHistoricoPendente(pacote);
        return pacote;
    }

    @Override
    public Pacote atualizar(Pacote pacote) {
        int linhas = executar("atualizar", """
                update Pacote
                   set codigo = ?, destino = ?, dataInicio = ?, dataFim = ?, descricao = ?,
                       roteiro = ?, capacidadeAtual = ?, preco = ?
                 where codPacote = ?
                """,
                pacote.getCodigo(), pacote.getDestino(), pacote.getDataInicio(), pacote.getDataFim(),
                pacote.getDescricao(), pacote.getRoteiroPrevisto(), pacote.getCapacidadeTotal(),
                pacote.getPreco(), pacote.getId());

        if (linhas == 0) {
            throw new RepositorioException("Pacote inexistente para atualizacao: id=" + pacote.getId(), null);
        }
        gravarHistoricoPendente(pacote);
        return pacote;
    }

    @Override
    public Optional<Pacote> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return buscarUm("select " + COLUNAS + " from Pacote where codPacote = ?", this::mapear, id);
    }

    @Override
    public List<Pacote> buscarTodos() {
        return buscarLista("select " + COLUNAS + " from Pacote order by dataInicio, codPacote", this::mapear);
    }

    @Override
    public boolean remover(Long id) {
        return id != null && executar("remover", "delete from Pacote where codPacote = ?", id) > 0;
    }

    @Override
    public long contar() {
        return consultarEscalar("select count(*) from Pacote", resultado -> resultado.getLong(1));
    }

    @Override
    public Optional<Pacote> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return buscarUm("select " + COLUNAS + " from Pacote where upper(codigo) = upper(?)",
                this::mapear, codigo.trim());
    }

    @Override
    public Optional<Pacote> buscarPorDestinoEPeriodo(String destino, LocalDate dataInicio, LocalDate dataFim) {
        if (destino == null || dataInicio == null || dataFim == null) {
            return Optional.empty();
        }
        return buscarUm("select " + COLUNAS + """
                 from Pacote
                where lower(trim(destino)) = lower(trim(?))
                  and dataInicio = ? and dataFim = ?
                """, this::mapear, destino, dataInicio, dataFim);
    }

    @Override
    public List<Pacote> buscarPorTermo(String termo) {
        if (termo == null || termo.isBlank()) {
            return buscarTodos();
        }
        String alvo = "%" + termo.trim() + "%";
        return buscarLista("select " + COLUNAS + """
                 from Pacote
                where codigo ilike ?
                   or destino ilike ?
                   or to_char(dataInicio, 'DD/MM/YYYY') like ?
                   or to_char(dataFim, 'DD/MM/YYYY') like ?
                order by dataInicio
                """, this::mapear, alvo, alvo, alvo, alvo);
    }

    @Override
    public List<Pacote> buscarPorPeriodo(LocalDate inicio, LocalDate fim) {
        return buscarLista("select " + COLUNAS + """
                 from Pacote
                where (cast(? as date) is null or dataFim >= ?)
                  and (cast(? as date) is null or dataInicio <= ?)
                order by dataInicio
                """, this::mapear, inicio, inicio, fim, fim);
    }

    /**
     * Trava a linha do pacote ate o fim da transacao (UC03).
     *
     * <p>Sem isso, duas instancias da aplicacao podem ler a mesma quantidade de
     * vagas livres e gravar reservas que juntas estouram a capacidade — o bloqueio
     * otimista nao resolve esse caso, porque a linha do pacote e apenas lida.</p>
     */
    @Override
    public void bloquearParaAtualizacao(Long pacoteId) {
        if (pacoteId == null) {
            return;
        }
        if (!Conexoes.existeTransacaoAtiva()) {
            // Fora de transacao o bloqueio seria liberado na hora seguinte, o que
            // daria falsa sensacao de protecao.
            return;
        }
        try (Connection conexao = Conexoes.atual();
             PreparedStatement comando =
                     conexao.prepareStatement("select 1 from Pacote where codPacote = ? for update")) {
            preencher(comando, pacoteId);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
            }
        } catch (SQLException excecao) {
            throw RepositorioException.traduzir("bloquear pacote", excecao);
        }
    }

    @Override
    public String proximoCodigo() {
        return consultarEscalar("select 'PC-' || lpad(nextval('seq_codigo_pacote')::text, 3, '0')",
                resultado -> resultado.getString(1));
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private Pacote mapear(ResultSet resultado) throws SQLException {
        Pacote pacote = new Pacote();
        pacote.setId(resultado.getLong("codpacote"));
        pacote.setCodigo(resultado.getString("codigo"));
        pacote.setDestino(resultado.getString("destino"));
        pacote.setDataInicio(resultado.getObject("datainicio", LocalDate.class));
        pacote.setDataFim(resultado.getObject("datafim", LocalDate.class));
        pacote.setDescricao(resultado.getString("descricao"));
        pacote.setRoteiroPrevisto(resultado.getString("roteiro"));
        pacote.setCapacidadeTotal(resultado.getInt("capacidadeatual"));
        pacote.setPreco(resultado.getBigDecimal("preco"));
        pacote.setDataCadastro(resultado.getObject("datacadastro", LocalDateTime.class));
        carregarHistorico(pacote);
        return pacote;
    }

    /** Historico de alteracoes de capacidade (UC04, passo 7). */
    private void carregarHistorico(Pacote pacote) {
        List<AlteracaoCapacidade> alteracoes = buscarLista("""
                select idCapacidade, fkcodPacote, capacidadeAnterior, quantidade,
                       justificativa, responsavel, dataInsercao
                  from CapacidadeLog
                 where fkcodPacote = ?
                 order by dataInsercao
                """, this::mapearAlteracao, pacote.getId());
        alteracoes.forEach(pacote::registrarAlteracaoCapacidade);
    }

    private AlteracaoCapacidade mapearAlteracao(ResultSet resultado) throws SQLException {
        AlteracaoCapacidade alteracao = new AlteracaoCapacidade();
        alteracao.setId(resultado.getLong("idcapacidade"));
        alteracao.setPacoteId(resultado.getLong("fkcodpacote"));
        alteracao.setCapacidadeAnterior(resultado.getInt("capacidadeanterior"));
        alteracao.setCapacidadeNova(resultado.getInt("quantidade"));
        alteracao.setJustificativa(resultado.getString("justificativa"));
        alteracao.setUsuarioResponsavel(resultado.getString("responsavel"));
        alteracao.setDataHora(resultado.getObject("datainsercao", LocalDateTime.class));
        return alteracao;
    }

    /** Grava as alteracoes de capacidade que ainda nao tem identificador. */
    private void gravarHistoricoPendente(Pacote pacote) {
        for (AlteracaoCapacidade alteracao : pacote.getHistoricoCapacidade()) {
            if (alteracao.getId() != null) {
                continue;
            }
            long id = inserirRetornandoChave("""
                    insert into CapacidadeLog (fkcodPacote, capacidadeAnterior, quantidade,
                                               justificativa, responsavel, dataInsercao)
                    values (?, ?, ?, ?, ?, ?)
                    returning idCapacidade
                    """,
                    pacote.getId(), alteracao.getCapacidadeAnterior(), alteracao.getCapacidadeNova(),
                    alteracao.getJustificativa(), alteracao.getUsuarioResponsavel(),
                    alteracao.getDataHora() == null ? LocalDateTime.now() : alteracao.getDataHora());
            alteracao.setId(id);
            alteracao.setPacoteId(pacote.getId());
        }
    }

    private static LocalDateTime dataDeCadastro(Pacote pacote) {
        return pacote.getDataCadastro() == null ? LocalDateTime.now() : pacote.getDataCadastro();
    }
}
