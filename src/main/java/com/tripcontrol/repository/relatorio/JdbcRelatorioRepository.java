package com.tripcontrol.repository.relatorio;

import com.tripcontrol.model.CriterioRanking;
import com.tripcontrol.repository.jdbc.JdbcRepositorioBase;
import com.tripcontrol.util.Formatadores;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * As cinco consultas do UC08 em SQL.
 *
 * <p>Padrao usado em todos os filtros opcionais: {@code (cast(? as tipo) is null or
 * coluna >= ?)}. O {@code cast} e necessario porque o PostgreSQL precisa saber o
 * tipo do parametro para decidir o plano; o efeito e que um filtro nulo
 * simplesmente nao restringe nada, e a mesma consulta serve para qualquer
 * combinacao de filtros preenchidos.</p>
 *
 * <p>Os relatorios de Pagamentos e de Ocupacao leem as views
 * {@code SituacaoFinanceiraReserva} e {@code SituacaoPacote} escritas pela equipe,
 * em vez de repetir aquelas regras.</p>
 */
public class JdbcRelatorioRepository extends JdbcRepositorioBase implements RelatorioRepository {

    @Override
    protected String nomeDaEntidade() {
        return "relatorio";
    }

    @Override
    public boolean disponivel() {
        return true;
    }

    // ------------------------------------------------------------------
    // Reservas
    // ------------------------------------------------------------------

    @Override
    public DadosRelatorio reservas(FiltrosValidados filtros) {
        // A coluna "situacao" do banco so distingue Ativa de Cancelada. "Concluida"
        // e derivada aqui — reserva nao cancelada cujo pacote ja terminou —, que e a
        // mesma regra usada no relatorio de Clientes para contar viagens concluidas.
        // Por isso a situacao exibida e a filtrada saem da mesma subconsulta.
        List<List<String>> linhas = buscarLista("""
                select codigo, cliente, codigoPacote, destino, dataInicio, dataFim,
                       quantidadeViajantes, situacaoExibida
                  from (
                        select r.codigo, c.nome as cliente, p.codigo as codigoPacote,
                               p.destino, r.dataInicio, r.dataFim, r.quantidadeViajantes,
                               r.fkCPFCliente, p.codigo as filtroPacote,
                               case when r.situacao = 'Cancelada' then 'Cancelada'
                                    when p.dataFim < current_date then 'Concluida'
                                    else 'Ativa'
                               end as situacaoExibida
                          from Reserva r
                          join Cliente c on c.CPFCliente = r.fkCPFCliente
                          join Pacote p on p.codPacote = r.fkcodPacote
                       ) reservas
                 where (cast(? as date) is null or dataFim >= ?)
                   and (cast(? as date) is null or dataInicio <= ?)
                   and (cast(? as varchar) is null or situacaoExibida = ?)
                   and (cast(? as varchar) is null or upper(filtroPacote) = upper(?))
                   and (cast(? as varchar) is null or fkCPFCliente = ?)
                 order by dataInicio, codigo
                """,
                resultado -> List.of(
                        texto(resultado.getString("codigo")),
                        texto(resultado.getString("cliente")),
                        texto(resultado.getString("codigopacote")) + " - "
                                + texto(resultado.getString("destino")),
                        periodo(resultado),
                        String.valueOf(resultado.getInt("quantidadeviajantes")),
                        texto(resultado.getString("situacaoexibida"))),
                filtros.dataInicio(), filtros.dataInicio(),
                filtros.dataFim(), filtros.dataFim(),
                filtros.situacaoReserva(), filtros.situacaoReserva(),
                filtros.codigoPacote(), filtros.codigoPacote(),
                filtros.cpfCliente(), filtros.cpfCliente());

        Map<String, String> totais = new LinkedHashMap<>();
        totais.put("Reservas", String.valueOf(linhas.size()));
        totais.put("Passageiros", String.valueOf(somarInteiros(linhas, 4)));
        return new DadosRelatorio(linhas, totais);
    }

    // ------------------------------------------------------------------
    // Pagamentos
    // ------------------------------------------------------------------

    @Override
    public DadosRelatorio pagamentos(FiltrosValidados filtros) {
        List<List<String>> linhas = buscarLista("""
                select r.codigo, sf.valorTotal, sf.totalPago, sf.saldoPendente,
                       sf.situacaoFinanceira,
                       (select string_agg(to_char(pa.vencimento, 'DD/MM/YYYY'), ', '
                                          order by pa.numParcela)
                          from Parcela pa where pa.fkidReserva = r.idReserva) as vencimentos
                  from Reserva r
                  join SituacaoFinanceiraReserva sf on sf.idReserva = r.idReserva
                  join Pacote p on p.codPacote = r.fkcodPacote
                 where (cast(? as varchar) is null or upper(p.codigo) = upper(?))
                   and (cast(? as varchar) is null or sf.situacaoFinanceira = ?)
                   -- periodo de recebimento: a reserva entra se teve algum
                   -- recebimento dentro do intervalo informado
                   and ((cast(? as date) is null and cast(? as date) is null)
                        or exists (select 1 from Parcela pa
                                    where pa.fkidReserva = r.idReserva
                                      and pa.dataRecebimento is not null
                                      and (cast(? as date) is null or pa.dataRecebimento >= ?)
                                      and (cast(? as date) is null or pa.dataRecebimento <= ?)))
                   and (cast(? as varchar) is null
                        or exists (select 1 from Parcela pa
                                    where pa.fkidReserva = r.idReserva
                                      and pa.formaPagamento = ?))
                 order by sf.saldoPendente desc, r.codigo
                """,
                resultado -> List.of(
                        texto(resultado.getString("codigo")),
                        Formatadores.formatarMoeda(resultado.getBigDecimal("valortotal")),
                        Formatadores.formatarMoeda(resultado.getBigDecimal("totalpago")),
                        Formatadores.formatarMoeda(resultado.getBigDecimal("saldopendente")),
                        texto(resultado.getString("vencimentos")),
                        texto(resultado.getString("situacaofinanceira"))),
                filtros.codigoPacote(), filtros.codigoPacote(),
                filtros.situacaoFinanceira(), filtros.situacaoFinanceira(),
                filtros.dataInicio(), filtros.dataFim(),
                filtros.dataInicio(), filtros.dataInicio(),
                filtros.dataFim(), filtros.dataFim(),
                filtros.formaPagamento(), filtros.formaPagamento());

        // Os totais monetarios sao somados pelo banco, e nao a partir das strings ja
        // formatadas das linhas: em pt-BR "R$ 1.500,00" tem separador de milhar e
        // espaco inquebravel, e refazer esse caminho de volta e pedir para errar.
        Map<String, String> totais = new LinkedHashMap<>();
        totais.put("Reservas", String.valueOf(linhas.size()));
        if (!linhas.isEmpty()) {
            List<String> codigos = linhas.stream().map(linha -> linha.get(0)).toList();
            totais.put("Total Recebido", Formatadores.formatarMoeda(
                    somarNoBanco("totalPago", codigos)));
            totais.put("Saldo Pendente", Formatadores.formatarMoeda(
                    somarNoBanco("saldoPendente", codigos)));
        }
        return new DadosRelatorio(linhas, totais);
    }

    // ------------------------------------------------------------------
    // Pacotes Mais Procurados
    // ------------------------------------------------------------------

    @Override
    public DadosRelatorio pacotesMaisProcurados(FiltrosValidados filtros) {
        // A coluna de ordenacao vem do enum, nunca de texto digitado: nao ha como
        // injetar SQL por aqui.
        String ordenacao = filtros.criterioRanking() == CriterioRanking.NUMERO_DE_VIAJANTES
                ? "viajantes" : "reservas";
        int posicoes = filtros.posicoesRanking() == null ? 10 : filtros.posicoesRanking();

        // TODO: o UC08 nao diz se uma reserva cancelada continua contando como
        //  "procura" pelo pacote. Aqui so as reservas ativas contam, para ficar
        //  coerente com o calculo de vagas do UC04 e com a view SituacaoPacote.
        //  A equipe precisa confirmar com o professor.
        String consulta = """
                select p.codigo, p.destino,
                       count(r.idReserva) as reservas,
                       coalesce(sum(r.quantidadeViajantes), 0) as viajantes
                  from Pacote p
                  join Reserva r on r.fkcodPacote = p.codPacote and r.situacao = 'Ativa'
                 where (cast(? as date) is null or r.dataCriacao >= ?)
                   and (cast(? as date) is null or r.dataCriacao < cast(? as date) + 1)
                 group by p.codigo, p.destino
                """ + " order by " + ordenacao + " desc, p.codigo limit ?";

        List<List<String>> linhas = buscarLista(consulta,
                resultado -> List.of(
                        texto(resultado.getString("codigo")),
                        texto(resultado.getString("destino")),
                        String.valueOf(resultado.getLong("reservas")),
                        String.valueOf(resultado.getLong("viajantes"))),
                filtros.dataInicio(), filtros.dataInicio(),
                filtros.dataFim(), filtros.dataFim(),
                posicoes);

        Map<String, String> totais = new LinkedHashMap<>();
        totais.put("Pacotes no ranking", String.valueOf(linhas.size()));
        totais.put("Reservas", String.valueOf(somarInteiros(linhas, 2)));
        totais.put("Passageiros", String.valueOf(somarInteiros(linhas, 3)));
        return new DadosRelatorio(linhas, totais);
    }

    // ------------------------------------------------------------------
    // Clientes
    // ------------------------------------------------------------------

    @Override
    public DadosRelatorio clientes(FiltrosValidados filtros) {
        // "Viagem concluida" = reserva nao cancelada cujo pacote ja terminou.
        // E derivado, nunca gravado: nenhum caso de uso muda a reserva para
        // "Concluida", e um valor gravado ficaria desatualizado sozinho.
        List<List<String>> linhas = buscarLista("""
                select nome, cpf, contato, preferencias, concluidas
                  from (
                        select c.nome,
                               c.CPFCliente as cpf,
                               coalesce(c.telefone, c.email) as contato,
                               (select string_agg(cp.preferencia, ', ' order by cp.preferencia)
                                  from ClientePreferencia cp
                                 where cp.fkCPFCliente = c.CPFCliente) as preferencias,
                               (select count(*)
                                  from Reserva r
                                  join Pacote p on p.codPacote = r.fkcodPacote
                                 where r.fkCPFCliente = c.CPFCliente
                                   and r.situacao <> 'Cancelada'
                                   and p.dataFim < current_date) as concluidas
                          from Cliente c
                         where (cast(? as varchar) is null
                                or upper(c.nome) like '%' || upper(?) || '%')
                           and (cast(? as varchar) is null or c.CPFCliente = ?)
                           and (cast(? as varchar) is null
                                or exists (select 1 from ClientePreferencia cp
                                            where cp.fkCPFCliente = c.CPFCliente
                                              and upper(cp.preferencia) = upper(?)))
                       ) clientes
                 where (cast(? as int) is null or concluidas >= ?)
                 order by concluidas desc, nome
                """,
                resultado -> List.of(
                        texto(resultado.getString("nome")),
                        com.tripcontrol.util.ValidadorCpf.formatar(resultado.getString("cpf")),
                        texto(resultado.getString("contato")),
                        texto(resultado.getString("preferencias")),
                        String.valueOf(resultado.getLong("concluidas"))),
                filtros.nomeCliente(), filtros.nomeCliente(),
                filtros.cpfCliente(), filtros.cpfCliente(),
                filtros.preferencia(), filtros.preferencia(),
                filtros.minimoViagensConcluidas(), filtros.minimoViagensConcluidas());

        Map<String, String> totais = new LinkedHashMap<>();
        totais.put("Clientes", String.valueOf(linhas.size()));
        totais.put("Viagens concluídas", String.valueOf(somarInteiros(linhas, 4)));
        return new DadosRelatorio(linhas, totais);
    }

    // ------------------------------------------------------------------
    // Ocupacao dos Pacotes
    // ------------------------------------------------------------------

    @Override
    public DadosRelatorio ocupacaoDosPacotes(FiltrosValidados filtros) {
        List<List<String>> linhas = buscarLista("""
                select codigo, destino, capacidadeAtual, vagasOcupadas, vagasDisponiveis,
                       percentual, situacao
                  from (
                        select p.codigo, sp.destino, sp.capacidadeAtual, sp.vagasOcupadas,
                               sp.vagasDisponiveis, sp.situacao, sp.dataInicio,
                               round(sp.vagasOcupadas * 100.0
                                     / nullif(sp.capacidadeAtual, 0), 1) as percentual
                          from SituacaoPacote sp
                          join Pacote p on p.codPacote = sp.codPacote
                       ) pacotes
                 where (cast(? as date) is null or dataInicio >= ?)
                   and (cast(? as date) is null or dataInicio <= ?)
                   and (cast(? as varchar) is null
                        or upper(destino) like '%' || upper(?) || '%')
                   and (cast(? as varchar) is null or situacao = ?)
                   and (cast(? as numeric) is null or coalesce(percentual, 0) >= ?)
                 order by percentual desc nulls last, codigo
                """,
                resultado -> List.of(
                        texto(resultado.getString("codigo")) + " - "
                                + texto(resultado.getString("destino")),
                        String.valueOf(resultado.getInt("capacidadeatual")),
                        String.valueOf(resultado.getLong("vagasocupadas")),
                        String.valueOf(resultado.getLong("vagasdisponiveis")),
                        Formatadores.formatarPercentual(resultado.getBigDecimal("percentual")),
                        texto(resultado.getString("situacao"))),
                filtros.dataInicio(), filtros.dataInicio(),
                filtros.dataFim(), filtros.dataFim(),
                filtros.destino(), filtros.destino(),
                filtros.situacaoPacote(), filtros.situacaoPacote(),
                filtros.percentualMinimoOcupacao(), filtros.percentualMinimoOcupacao());

        Map<String, String> totais = new LinkedHashMap<>();
        totais.put("Pacotes", String.valueOf(linhas.size()));
        totais.put("Vagas ocupadas", String.valueOf(somarInteiros(linhas, 2)));
        totais.put("Vagas disponíveis", String.valueOf(somarInteiros(linhas, 3)));
        totais.put("Ocupação média", Formatadores.formatarPercentual(ocupacaoMedia(linhas)));
        return new DadosRelatorio(linhas, totais);
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? "-" : valor;
    }

    private static String periodo(ResultSet resultado) throws SQLException {
        LocalDate inicio = resultado.getObject("datainicio", LocalDate.class);
        LocalDate fim = resultado.getObject("datafim", LocalDate.class);
        if (inicio == null || fim == null) {
            return "-";
        }
        return Formatadores.formatarData(inicio) + " a " + Formatadores.formatarData(fim);
    }

    /**
     * Soma uma coluna da view financeira para as reservas que entraram no relatorio.
     *
     * @param coluna nome de coluna da view, vindo do codigo e nunca de texto digitado
     */
    private BigDecimal somarNoBanco(String coluna, List<String> codigosDeReserva) {
        String marcadores = String.join(", ", codigosDeReserva.stream().map(codigo -> "?").toList());
        return consultarEscalar("select coalesce(sum(sf." + coluna + "), 0)"
                        + " from SituacaoFinanceiraReserva sf"
                        + " join Reserva r on r.idReserva = sf.idReserva"
                        + " where r.codigo in (" + marcadores + ")",
                resultado -> resultado.getBigDecimal(1),
                codigosDeReserva.toArray());
    }

    private static long somarInteiros(List<List<String>> linhas, int coluna) {
        return linhas.stream().mapToLong(linha -> Long.parseLong(linha.get(coluna))).sum();
    }

    /** Ocupacao media ponderada: vagas ocupadas sobre capacidade total do conjunto. */
    private static BigDecimal ocupacaoMedia(List<List<String>> linhas) {
        long capacidade = somarInteiros(linhas, 1);
        if (capacidade == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(somarInteiros(linhas, 2) * 100.0 / capacidade)
                .setScale(1, RoundingMode.HALF_UP);
    }
}
