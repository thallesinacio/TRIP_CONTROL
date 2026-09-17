package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.Cancelamento;
import com.tripcontrol.model.MotivoCancelamento;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.model.StatusReserva;
import com.tripcontrol.repository.ConflitoDeConcorrenciaException;
import com.tripcontrol.repository.RepositorioException;
import com.tripcontrol.repository.ReservaRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de reservas (UC03, UC04, UC07).
 *
 * <p>Dois pontos merecem atencao ao ler este codigo:</p>
 *
 * <ul>
 *   <li>A coluna {@code situacao} e <em>gerada</em> pelo banco a partir de
 *       {@code motivoCancelamento}. Por isso nada aqui escreve nela: gravar o
 *       motivo do cancelamento e o que faz a reserva virar "Cancelada".</li>
 *   <li>O {@code UPDATE} usa {@code where versao = ?}: se ninguem tiver alterado
 *       a linha desde a leitura, uma linha e afetada e a versao avanca; caso
 *       contrario nenhuma linha muda e sobe {@link ConflitoDeConcorrenciaException},
 *       que e como o FA05 do UC07 e o FA04 do UC04 sao detectados de verdade.</li>
 * </ul>
 */
public class JdbcReservaRepository extends JdbcRepositorioBase implements ReservaRepository {

    private static final String SELECAO = """
            select r.idReserva, r.codigo, c.id as clienteId, r.fkcodPacote, r.quantidadeViajantes,
                   r.observacoes, r.valorTotal, r.dataCriacao, r.dataInicio, r.dataFim, r.versao,
                   r.situacao, r.motivoCancelamento, r.dataCancelamento, r.descricaoCancelamento,
                   r.responsavelCancelamento, r.vagasDevolvidas
              from Reserva r
              join Cliente c on c.CPFCliente = r.fkCPFCliente
            """;

    @Override
    protected String nomeDaEntidade() {
        return "reserva";
    }

    @Override
    public Reserva salvar(Reserva reserva) {
        // O CPF do cliente vem de subconsulta: o dominio trabalha com id numerico,
        // enquanto a chave estrangeira da tabela e o CPF.
        long id = inserirRetornandoChave("""
                insert into Reserva (codigo, fkcodPacote, fkCPFCliente, quantidadeViajantes,
                                     observacoes, valorTotal, dataCriacao, dataInicio, dataFim, versao)
                select ?, ?, c.CPFCliente, ?, ?, ?, ?, ?, ?, 0
                  from Cliente c
                 where c.id = ?
                returning idReserva
                """,
                reserva.getCodigo(), reserva.getPacoteId(), reserva.getQuantidadeViajantes(),
                reserva.getObservacoes(), reserva.getValorTotal(), dataDeRegistro(reserva),
                reserva.getDataInicio(), reserva.getDataFim(), reserva.getClienteId());

        reserva.setId(id);
        reserva.setVersao(0L);
        return reserva;
    }

    @Override
    public Reserva atualizar(Reserva reserva) {
        Cancelamento cancelamento = reserva.getCancelamento();
        int linhas = executar("atualizar", """
                update Reserva
                   set codigo = ?, fkcodPacote = ?, quantidadeViajantes = ?, observacoes = ?,
                       valorTotal = ?, dataInicio = ?, dataFim = ?,
                       motivoCancelamento = ?, dataCancelamento = ?, descricaoCancelamento = ?,
                       responsavelCancelamento = ?, vagasDevolvidas = ?,
                       versao = versao + 1
                 where idReserva = ? and versao = ?
                """,
                reserva.getCodigo(), reserva.getPacoteId(), reserva.getQuantidadeViajantes(),
                reserva.getObservacoes(), reserva.getValorTotal(),
                reserva.getDataInicio(), reserva.getDataFim(),
                cancelamento == null || cancelamento.getMotivo() == null
                        ? null : cancelamento.getMotivo().name(),
                cancelamento == null ? null : cancelamento.getDataHora(),
                cancelamento == null ? null : cancelamento.getDescricao(),
                cancelamento == null ? null : cancelamento.getUsuarioResponsavel(),
                cancelamento == null ? null : cancelamento.getVagasDevolvidas(),
                reserva.getId(), reserva.getVersao());

        if (linhas == 0) {
            if (buscarPorId(reserva.getId()).isEmpty()) {
                throw new RepositorioException("Reserva inexistente para atualizacao: id=" + reserva.getId(), null);
            }
            throw new ConflitoDeConcorrenciaException(
                    "A reserva " + reserva.getCodigo() + " foi alterada por outro processo.");
        }

        reserva.setVersao(reserva.getVersao() + 1);
        return reserva;
    }

    @Override
    public Optional<Reserva> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return buscarUm(SELECAO + " where r.idReserva = ?", this::mapear, id);
    }

    @Override
    public List<Reserva> buscarTodos() {
        return buscarLista(SELECAO + " order by r.dataCriacao desc, r.idReserva desc", this::mapear);
    }

    @Override
    public boolean remover(Long id) {
        return id != null && executar("remover", "delete from Reserva where idReserva = ?", id) > 0;
    }

    @Override
    public long contar() {
        return consultarEscalar("select count(*) from Reserva", resultado -> resultado.getLong(1));
    }

    @Override
    public Optional<Reserva> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return buscarUm(SELECAO + " where upper(r.codigo) = upper(?)", this::mapear, codigo.trim());
    }

    @Override
    public List<Reserva> buscarPorCliente(Long clienteId) {
        return buscarLista(SELECAO + " where c.id = ? order by r.dataInicio desc", this::mapear, clienteId);
    }

    @Override
    public List<Reserva> buscarPorPacote(Long pacoteId) {
        return buscarLista(SELECAO + " where r.fkcodPacote = ? order by r.dataCriacao", this::mapear, pacoteId);
    }

    /** Base do calculo de vagas ocupadas (UC04): so reservas ativas ocupam vaga. */
    @Override
    public List<Reserva> buscarAtivasPorPacote(Long pacoteId) {
        return buscarLista(SELECAO + " where r.fkcodPacote = ? and r.situacao = 'Ativa'",
                this::mapear, pacoteId);
    }

    @Override
    public List<Reserva> buscarPorStatus(StatusReserva status) {
        if (status == null) {
            return List.of();
        }
        return buscarLista(SELECAO + " where r.situacao = ?", this::mapear, descricaoDaSituacao(status));
    }

    @Override
    public List<Reserva> buscarPorPeriodoDeViagem(LocalDate inicio, LocalDate fim) {
        return buscarLista(SELECAO + """
                 where r.dataInicio is not null and r.dataFim is not null
                   and (cast(? as date) is null or r.dataFim >= ?)
                   and (cast(? as date) is null or r.dataInicio <= ?)
                 order by r.dataInicio
                """, this::mapear, inicio, inicio, fim, fim);
    }

    @Override
    public String proximoCodigo() {
        return consultarEscalar("select 'RE-' || lpad(nextval('seq_codigo_reserva')::text, 4, '0')",
                resultado -> resultado.getString(1));
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private Reserva mapear(ResultSet resultado) throws SQLException {
        Reserva reserva = new Reserva();
        reserva.setId(resultado.getLong("idreserva"));
        reserva.setCodigo(resultado.getString("codigo"));
        reserva.setClienteId(lerLongOuNulo(resultado, "clienteid"));
        reserva.setPacoteId(resultado.getLong("fkcodpacote"));
        reserva.setQuantidadeViajantes(resultado.getInt("quantidadeviajantes"));
        reserva.setObservacoes(resultado.getString("observacoes"));
        reserva.setValorTotal(resultado.getBigDecimal("valortotal"));
        reserva.setDataRegistro(resultado.getObject("datacriacao", LocalDateTime.class));
        reserva.setDataInicio(resultado.getObject("datainicio", LocalDate.class));
        reserva.setDataFim(resultado.getObject("datafim", LocalDate.class));
        reserva.setVersao(resultado.getLong("versao"));

        String motivo = resultado.getString("motivocancelamento");
        if (motivo != null) {
            Cancelamento cancelamento = new Cancelamento();
            cancelamento.setMotivo(MotivoCancelamento.valueOf(motivo));
            cancelamento.setDataHora(resultado.getObject("datacancelamento", LocalDateTime.class));
            cancelamento.setDescricao(resultado.getString("descricaocancelamento"));
            cancelamento.setUsuarioResponsavel(resultado.getString("responsavelcancelamento"));
            cancelamento.setVagasDevolvidas(resultado.getInt("vagasdevolvidas"));
            reserva.setCancelamento(cancelamento);
        }
        reserva.setStatus(statusDaSituacao(resultado.getString("situacao")));
        return reserva;
    }

    /** A coluna gerada no banco so distingue Ativa de Cancelada. */
    private static StatusReserva statusDaSituacao(String situacao) {
        return "Cancelada".equalsIgnoreCase(situacao) ? StatusReserva.CANCELADA : StatusReserva.ATIVA;
    }

    private static String descricaoDaSituacao(StatusReserva status) {
        return status == StatusReserva.CANCELADA ? "Cancelada" : "Ativa";
    }

    private static LocalDateTime dataDeRegistro(Reserva reserva) {
        return reserva.getDataRegistro() == null ? LocalDateTime.now() : reserva.getDataRegistro();
    }
}
