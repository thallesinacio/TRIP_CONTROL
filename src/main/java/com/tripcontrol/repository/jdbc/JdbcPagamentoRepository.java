package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.FormaPagamento;
import com.tripcontrol.model.Pagamento;
import com.tripcontrol.repository.ConflitoDeConcorrenciaException;
import com.tripcontrol.repository.PagamentoRepository;
import com.tripcontrol.repository.RepositorioException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Recebimentos do UC05, gravados nas colunas de pagamento da tabela
 * {@code Parcela}.
 *
 * <p><strong>Nao existe tabela Pagamento.</strong> O schema da equipe guarda o
 * recebimento na propria parcela ({@code valorPago}, {@code dataRecebimento},
 * {@code formaPagamento}, {@code identificadorPagamento}, {@code comprovante},
 * {@code observacao}), e a decisao A da equipe — um pagamento quita exatamente
 * uma parcela — torna a relacao 1:1. Uma tabela separada so acrescentaria um
 * join. A consequencia aceita conscientemente: nao ha pagamento parcial de
 * parcela nem historico de tentativas.</p>
 *
 * <p>Nomes que valem memorizar, porque as duas colunas sao facilmente
 * confundidas:</p>
 *
 * <ul>
 *   <li>{@code identificadorPagamento} = <strong>numero do recibo</strong>,
 *       gerado pelo sistema, obrigatorio e unico (UC05 passo 10);</li>
 *   <li>{@code comprovante} = <strong>identificador da transacao</strong>
 *       digitado pelo funcionario, opcional (UC05 passo 6) e base do FA04.</li>
 * </ul>
 */
public class JdbcPagamentoRepository extends JdbcRepositorioBase implements PagamentoRepository {

    /** So linhas com valorPago preenchido sao pagamentos; as demais sao parcelas em aberto. */
    private static final String SELECAO = """
            select numParcela, fkidReserva, valorPago, dataRecebimento, formaPagamento,
                   identificadorPagamento, comprovante, observacao
              from Parcela
             where valorPago is not null
            """;

    @Override
    protected String nomeDaEntidade() {
        return "pagamento";
    }

    /**
     * Grava o recebimento sobre a parcela correspondente.
     *
     * <p>O {@code and valorPago is null} do WHERE e o que fecha a corrida do FA05:
     * se outra instancia da aplicacao quitou a mesma parcela entre a leitura da
     * tela e esta gravacao, nenhuma linha e afetada e a operacao e recusada, em
     * vez de sobrescrever silenciosamente o pagamento do outro.</p>
     */
    @Override
    public Pagamento salvar(Pagamento pagamento) {
        exigirChave(pagamento);
        int linhas = executar("salvar", """
                update Parcela
                   set valorPago = ?, dataRecebimento = ?, formaPagamento = ?,
                       identificadorPagamento = ?, comprovante = ?, observacao = ?
                 where numParcela = ? and fkidReserva = ? and valorPago is null
                """,
                pagamento.getValor(), pagamento.getDataRecebimento(), nomeDaForma(pagamento),
                pagamento.getNumeroRecibo(), pagamento.getIdentificadorTransacao(),
                pagamento.getObservacao(),
                pagamento.getNumeroParcela(), pagamento.getReservaId());

        if (linhas == 0) {
            if (buscarParcela(pagamento).isEmpty()) {
                throw new RepositorioException("A parcela " + pagamento.getNumeroParcela()
                        + " da reserva " + pagamento.getReservaId() + " nao existe.", null);
            }
            throw new ConflitoDeConcorrenciaException("A parcela " + pagamento.getNumeroParcela()
                    + " ja foi quitada por outro processo enquanto a tela estava aberta.");
        }

        pagamento.setId(ChaveDaParcela.id(pagamento.getReservaId(), pagamento.getNumeroParcela()));
        return pagamento;
    }

    @Override
    public Pagamento atualizar(Pagamento pagamento) {
        exigirChave(pagamento);
        int linhas = executar("atualizar", """
                update Parcela
                   set valorPago = ?, dataRecebimento = ?, formaPagamento = ?,
                       identificadorPagamento = ?, comprovante = ?, observacao = ?
                 where numParcela = ? and fkidReserva = ?
                """,
                pagamento.getValor(), pagamento.getDataRecebimento(), nomeDaForma(pagamento),
                pagamento.getNumeroRecibo(), pagamento.getIdentificadorTransacao(),
                pagamento.getObservacao(),
                pagamento.getNumeroParcela(), pagamento.getReservaId());

        if (linhas == 0) {
            throw new RepositorioException("Pagamento inexistente para atualizacao: parcela "
                    + pagamento.getNumeroParcela() + " da reserva " + pagamento.getReservaId(), null);
        }
        return pagamento;
    }

    @Override
    public Optional<Pagamento> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return buscarPorReservaEParcela(ChaveDaParcela.reservaId(id), ChaveDaParcela.numero(id));
    }

    @Override
    public List<Pagamento> buscarTodos() {
        return buscarLista(SELECAO + " order by dataRecebimento desc, fkidReserva, numParcela",
                this::mapear);
    }

    /** Desfaz o recebimento, devolvendo a parcela ao estado de pendente. */
    @Override
    public boolean remover(Long id) {
        if (id == null) {
            return false;
        }
        return executar("remover", """
                update Parcela
                   set valorPago = null, dataRecebimento = null, formaPagamento = null,
                       identificadorPagamento = null, comprovante = null, observacao = null
                 where numParcela = ? and fkidReserva = ? and valorPago is not null
                """,
                ChaveDaParcela.numero(id), ChaveDaParcela.reservaId(id)) > 0;
    }

    @Override
    public long contar() {
        return consultarEscalar("select count(*) from Parcela where valorPago is not null",
                resultado -> resultado.getLong(1));
    }

    @Override
    public List<Pagamento> buscarPorReserva(Long reservaId) {
        if (reservaId == null) {
            return List.of();
        }
        return buscarLista(SELECAO + " and fkidReserva = ? order by numParcela", this::mapear, reservaId);
    }

    /** FA04 do UC05: o identificador informado pelo funcionario nao pode se repetir. */
    @Override
    public Optional<Pagamento> buscarPorIdentificadorTransacao(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            return Optional.empty();
        }
        return buscarUm(SELECAO + " and lower(comprovante) = lower(?)", this::mapear, identificador.trim());
    }

    @Override
    public Optional<Pagamento> buscarPorReservaEParcela(Long reservaId, int numeroParcela) {
        if (reservaId == null) {
            return Optional.empty();
        }
        return buscarUm(SELECAO + " and fkidReserva = ? and numParcela = ?",
                this::mapear, reservaId, numeroParcela);
    }

    @Override
    public List<Pagamento> buscarPorPeriodoDeRecebimento(LocalDate inicio, LocalDate fim) {
        return buscarLista(SELECAO + """
                   and (cast(? as date) is null or dataRecebimento >= ?)
                   and (cast(? as date) is null or dataRecebimento <= ?)
                 order by dataRecebimento
                """, this::mapear, inicio, inicio, fim, fim);
    }

    @Override
    public String proximoNumeroRecibo() {
        return consultarEscalar("select 'RC-' || lpad(nextval('seq_numero_recibo')::text, 6, '0')",
                resultado -> resultado.getString(1));
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private Pagamento mapear(ResultSet resultado) throws SQLException {
        Pagamento pagamento = new Pagamento();
        Long reservaId = lerLongOuNulo(resultado, "fkidreserva");
        int numero = resultado.getInt("numparcela");

        pagamento.setId(ChaveDaParcela.id(reservaId, numero));
        pagamento.setReservaId(reservaId);
        pagamento.setNumeroParcela(numero);
        pagamento.setValor(resultado.getBigDecimal("valorpago"));
        LocalDate recebimento = resultado.getObject("datarecebimento", LocalDate.class);
        pagamento.setDataRecebimento(recebimento);
        pagamento.setDataRegistro(recebimento == null ? null : recebimento.atStartOfDay());

        String forma = resultado.getString("formapagamento");
        pagamento.setFormaPagamento(forma == null ? null : FormaPagamento.valueOf(forma));
        pagamento.setNumeroRecibo(resultado.getString("identificadorpagamento"));
        pagamento.setIdentificadorTransacao(resultado.getString("comprovante"));
        pagamento.setObservacao(resultado.getString("observacao"));
        return pagamento;
    }

    private Optional<Pagamento> buscarParcela(Pagamento pagamento) {
        return buscarUm("""
                select numParcela, fkidReserva, valorPago, dataRecebimento, formaPagamento,
                       identificadorPagamento, comprovante, observacao
                  from Parcela
                 where numParcela = ? and fkidReserva = ?
                """, this::mapear, pagamento.getNumeroParcela(), pagamento.getReservaId());
    }

    private static String nomeDaForma(Pagamento pagamento) {
        return pagamento.getFormaPagamento() == null ? null : pagamento.getFormaPagamento().name();
    }

    private static void exigirChave(Pagamento pagamento) {
        if (pagamento.getReservaId() == null || pagamento.getNumeroParcela() == null) {
            throw new RepositorioException(
                    "O pagamento precisa da reserva e do numero da parcela que ele quita.", null);
        }
    }
}
