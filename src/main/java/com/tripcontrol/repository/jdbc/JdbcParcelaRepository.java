package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.Parcela;
import com.tripcontrol.model.StatusParcela;
import com.tripcontrol.repository.ParcelaRepository;
import com.tripcontrol.repository.RepositorioException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Plano de parcelas de uma reserva (UC05), na tabela {@code Parcela}.
 *
 * <p>Dois pontos merecem atencao ao ler este codigo:</p>
 *
 * <ul>
 *   <li><strong>{@code totalParcelas} nao e coluna.</strong> Ele e contado na
 *       propria consulta com {@code count(*) over (partition by fkidReserva)},
 *       porque guardar o total em cada linha seria redundante e poderia divergir
 *       do numero real de parcelas.</li>
 *   <li><strong>Este repositorio nao escreve as colunas de pagamento</strong>
 *       ({@code valorPago}, {@code formaPagamento}, {@code identificadorPagamento},
 *       {@code comprovante}, {@code observacao}). Elas pertencem a
 *       {@link JdbcPagamentoRepository}, que grava na mesma linha. Se os dois
 *       escrevessem tudo, a baixa da parcela apagaria o pagamento que acabou de
 *       ser registrado.</li>
 * </ul>
 */
public class JdbcParcelaRepository extends JdbcRepositorioBase implements ParcelaRepository {

    /**
     * O total de parcelas e calculado sobre todas as linhas da reserva antes de
     * qualquer filtro, por isso a janela fica na subconsulta.
     */
    private static final String SELECAO = """
            select numParcela, fkidReserva, valor, vencimento, dataRecebimento, valorPago,
                   totalParcelas
              from (
                    select p.numParcela, p.fkidReserva, p.valor, p.vencimento,
                           p.dataRecebimento, p.valorPago,
                           count(*) over (partition by p.fkidReserva) as totalParcelas
                      from Parcela p
                   ) parcelas
            """;

    @Override
    protected String nomeDaEntidade() {
        return "parcela";
    }

    @Override
    public Parcela salvar(Parcela parcela) {
        exigirChave(parcela);
        executar("salvar", """
                insert into Parcela (numParcela, fkidReserva, valor, vencimento)
                values (?, ?, ?, ?)
                """,
                parcela.getNumero(), parcela.getReservaId(), parcela.getValor(),
                parcela.getDataVencimento());

        parcela.setId(ChaveDaParcela.id(parcela.getReservaId(), parcela.getNumero()));
        return parcela;
    }

    @Override
    public Parcela atualizar(Parcela parcela) {
        exigirChave(parcela);
        int linhas = executar("atualizar", """
                update Parcela set valor = ?, vencimento = ?, dataRecebimento = ?
                 where numParcela = ? and fkidReserva = ?
                """,
                parcela.getValor(), parcela.getDataVencimento(), parcela.getDataPagamento(),
                parcela.getNumero(), parcela.getReservaId());

        if (linhas == 0) {
            throw new RepositorioException("Parcela inexistente para atualizacao: "
                    + parcela.getNumero() + " da reserva " + parcela.getReservaId(), null);
        }
        return parcela;
    }

    @Override
    public Optional<Parcela> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return buscarPorReservaENumero(ChaveDaParcela.reservaId(id), ChaveDaParcela.numero(id));
    }

    @Override
    public List<Parcela> buscarTodos() {
        return buscarLista(SELECAO + " order by fkidReserva, numParcela", this::mapear);
    }

    @Override
    public boolean remover(Long id) {
        if (id == null) {
            return false;
        }
        return executar("remover", "delete from Parcela where numParcela = ? and fkidReserva = ?",
                ChaveDaParcela.numero(id), ChaveDaParcela.reservaId(id)) > 0;
    }

    @Override
    public long contar() {
        return consultarEscalar("select count(*) from Parcela", resultado -> resultado.getLong(1));
    }

    @Override
    public List<Parcela> buscarPorReserva(Long reservaId) {
        if (reservaId == null) {
            return List.of();
        }
        return buscarLista(SELECAO + " where fkidReserva = ? order by numParcela",
                this::mapear, reservaId);
    }

    @Override
    public Optional<Parcela> buscarPorReservaENumero(Long reservaId, int numero) {
        if (reservaId == null) {
            return Optional.empty();
        }
        return buscarUm(SELECAO + " where fkidReserva = ? and numParcela = ?",
                this::mapear, reservaId, numero);
    }

    @Override
    public void removerPorReserva(Long reservaId) {
        if (reservaId != null) {
            executar("remover parcelas de", "delete from Parcela where fkidReserva = ?", reservaId);
        }
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private Parcela mapear(ResultSet resultado) throws SQLException {
        Parcela parcela = new Parcela();
        Long reservaId = lerLongOuNulo(resultado, "fkidreserva");
        int numero = resultado.getInt("numparcela");

        parcela.setReservaId(reservaId);
        parcela.setNumero(numero);
        parcela.setId(ChaveDaParcela.id(reservaId, numero));
        parcela.setTotalParcelas(resultado.getInt("totalparcelas"));
        parcela.setValor(resultado.getBigDecimal("valor"));
        parcela.setDataVencimento(resultado.getObject("vencimento", LocalDate.class));
        parcela.setDataPagamento(resultado.getObject("datarecebimento", LocalDate.class));

        // O status nunca vem do banco: quitada quando ha recebimento, e a
        // distincao entre Pendente e Atrasada fica com a CalculadoraFinanceira,
        // que conhece a data de referencia.
        boolean quitada = resultado.getObject("datarecebimento") != null;
        parcela.setStatus(quitada ? StatusParcela.QUITADA : StatusParcela.PENDENTE);
        if (quitada) {
            parcela.setPagamentoId(ChaveDaParcela.id(reservaId, numero));
        }
        return parcela;
    }

    private static void exigirChave(Parcela parcela) {
        if (parcela.getReservaId() == null || parcela.getNumero() < 1) {
            throw new RepositorioException(
                    "A parcela precisa da reserva e do numero para ser gravada (sao a chave primaria).", null);
        }
    }
}
