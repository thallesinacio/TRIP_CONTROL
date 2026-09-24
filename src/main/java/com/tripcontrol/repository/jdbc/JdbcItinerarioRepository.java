package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.ItemItinerario;
import com.tripcontrol.model.Itinerario;
import com.tripcontrol.model.StatusItinerario;
import com.tripcontrol.model.TipoItemItinerario;
import com.tripcontrol.repository.ItinerarioRepository;
import com.tripcontrol.repository.RepositorioException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Itinerario de um pacote (UC06), montado sobre {@code Hospedagem},
 * {@code Transporte}, {@code Atividade} e a coluna
 * {@code Pacote.itinerarioFinalizadoEm}.
 *
 * <p><strong>Nao existe tabela Itinerario.</strong> O itinerario e 1:1 com o
 * pacote, e uma tabela 1:1 com outra e sinal de que aquelas colunas pertencem a
 * tabela original. O status tambem nao e gravado: nulo em
 * {@code itinerarioFinalizadoEm} significa rascunho, preenchido significa
 * finalizado — a mesma ideia de {@code Reserva.situacao} e da situacao
 * financeira, que tambem sao derivadas.</p>
 *
 * <p>Este repositorio e o unico que grava itens de itinerario. Nao ha
 * {@code ItemItinerarioRepository} porque o itinerario e um agregado: a tela
 * sempre envia o cronograma inteiro, e gravar item a item permitiria um
 * itinerario "finalizado" com metade dos itens. Cabecalho e itens entram na
 * mesma transacao, aberta pelo {@code ItinerarioController}.</p>
 */
public class JdbcItinerarioRepository extends JdbcRepositorioBase implements ItinerarioRepository {

    /**
     * Os tres tipos vem de tabelas diferentes e voltam num formato so.
     * Em Atividade, {@code dataA + horario} recompoe o timestamp que o dominio usa;
     * o local de encontro ocupa a coluna de destino, como o item foi modelado.
     */
    private static final String SELECAO_DE_ITENS = """
            select tipo, idItem, fkidRecurso, nomeServico, inicio, fim, origem, destino, instrucoes
              from (
                    select 'HOSPEDAGEM' as tipo, h.idHospedagem as idItem,
                           h.fkidRecurso as fkidRecurso, r.nome as nomeServico,
                           h.checkin as inicio, h.checkout as fim,
                           cast(null as varchar(120)) as origem,
                           cast(null as varchar(120)) as destino,
                           h.infoAdicional as instrucoes, h.fkcodPacote as pacote
                      from Hospedagem h left join Recurso r on r.idRecurso = h.fkidRecurso
                    union all
                    select 'TRANSPORTE', t.idTransporte, t.fkidRecurso, r.nome,
                           t.saida, t.chegada, t.origem, t.destino,
                           t.infoAdicional, t.fkcodPacote
                      from Transporte t left join Recurso r on r.idRecurso = t.fkidRecurso
                    union all
                    select 'ATIVIDADE', a.idAtividade, a.fkidRecurso, r.nome,
                           (a.dataA + a.horarioInicio), (a.dataA + a.horarioFim),
                           cast(null as varchar(120)), a.localA,
                           a.infoAdicional, a.fkcodPacote
                      from Atividade a left join Recurso r on r.idRecurso = a.fkidRecurso
                   ) itens
             where pacote = ?
             order by inicio
            """;

    @Override
    protected String nomeDaEntidade() {
        return "itinerario";
    }

    @Override
    public Itinerario salvar(Itinerario itinerario) {
        return gravar(itinerario);
    }

    @Override
    public Itinerario atualizar(Itinerario itinerario) {
        return gravar(itinerario);
    }

    /**
     * Regrava o cronograma inteiro do pacote.
     *
     * <p>Apaga e reinsere em vez de comparar item a item: o cronograma e um
     * conjunto pequeno, a tela sempre envia a versao completa, e a diferenca roda
     * dentro da transacao do {@code ItinerarioController} — ou tudo entra, ou o
     * cronograma anterior permanece intacto.</p>
     */
    private Itinerario gravar(Itinerario itinerario) {
        Long pacoteId = itinerario.getPacoteId();
        if (pacoteId == null) {
            throw new RepositorioException("O itinerario precisa estar vinculado a um pacote.", null);
        }

        apagarItens(pacoteId);
        for (ItemItinerario item : itinerario.getItens()) {
            inserirItem(pacoteId, item);
        }

        executar("marcar finalizacao do", """
                update Pacote set itinerarioFinalizadoEm = ? where codPacote = ?
                """, momentoDeFinalizacao(itinerario), pacoteId);

        itinerario.setId(pacoteId);
        return itinerario;
    }

    @Override
    public Optional<Itinerario> buscarPorPacote(Long pacoteId) {
        if (pacoteId == null) {
            return Optional.empty();
        }
        // O filtro "is not null" faz a consulta nao devolver linha nenhuma quando o
        // itinerario ainda e rascunho, que e exatamente a resposta esperada.
        Optional<LocalDateTime> finalizadoEm = buscarUm("""
                select itinerarioFinalizadoEm from Pacote
                 where codPacote = ? and itinerarioFinalizadoEm is not null
                """,
                resultado -> resultado.getObject("itinerariofinalizadoem", LocalDateTime.class),
                pacoteId);

        if (finalizadoEm.isEmpty()) {
            return Optional.empty();
        }

        Itinerario itinerario = new Itinerario(pacoteId);
        itinerario.setId(pacoteId);
        itinerario.setStatus(StatusItinerario.FINALIZADO);
        itinerario.setDataFinalizacao(finalizadoEm.get());
        buscarLista(SELECAO_DE_ITENS, resultado -> mapearItem(pacoteId, resultado), pacoteId)
                .forEach(itinerario::adicionarItem);
        return Optional.of(itinerario);
    }

    @Override
    public Optional<Itinerario> buscarPorId(Long id) {
        // O id do itinerario e o proprio codigo do pacote: a relacao e 1:1.
        return buscarPorPacote(id);
    }

    @Override
    public List<Itinerario> buscarTodos() {
        List<Long> pacotes = buscarLista("""
                select codPacote from Pacote where itinerarioFinalizadoEm is not null
                 order by itinerarioFinalizadoEm desc
                """, resultado -> resultado.getLong("codpacote"));

        List<Itinerario> encontrados = new ArrayList<>();
        pacotes.forEach(pacoteId -> buscarPorPacote(pacoteId).ifPresent(encontrados::add));
        return encontrados;
    }

    @Override
    public boolean remover(Long id) {
        if (id == null) {
            return false;
        }
        apagarItens(id);
        return executar("remover", """
                update Pacote set itinerarioFinalizadoEm = null
                 where codPacote = ? and itinerarioFinalizadoEm is not null
                """, id) > 0;
    }

    @Override
    public long contar() {
        return consultarEscalar("select count(*) from Pacote where itinerarioFinalizadoEm is not null",
                resultado -> resultado.getLong(1));
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private void apagarItens(Long pacoteId) {
        executar("limpar hospedagens do", "delete from Hospedagem where fkcodPacote = ?", pacoteId);
        executar("limpar transportes do", "delete from Transporte where fkcodPacote = ?", pacoteId);
        executar("limpar atividades do", "delete from Atividade where fkcodPacote = ?", pacoteId);
    }

    private void inserirItem(Long pacoteId, ItemItinerario item) {
        if (item.getTipo() == null || item.getInicio() == null || item.getFim() == null) {
            throw new RepositorioException(
                    "Item de itinerario sem tipo ou sem intervalo nao pode ser gravado.", null);
        }

        long chave = switch (item.getTipo()) {
            case HOSPEDAGEM -> inserirRetornandoChave("""
                    insert into Hospedagem (fkcodPacote, fkidRecurso, checkin, checkout, infoAdicional)
                    values (?, ?, ?, ?, ?)
                    returning idHospedagem
                    """,
                    pacoteId, item.getRecursoId(), item.getInicio(), item.getFim(),
                    item.getInstrucoesOperacionais());

            case TRANSPORTE -> inserirRetornandoChave("""
                    insert into Transporte (fkcodPacote, fkidRecurso, saida, chegada,
                                            origem, destino, infoAdicional)
                    values (?, ?, ?, ?, ?, ?, ?)
                    returning idTransporte
                    """,
                    pacoteId, item.getRecursoId(), item.getInicio(), item.getFim(),
                    item.getLocalOrigem(), item.getLocalDestino(), item.getInstrucoesOperacionais());

            // A atividade cabe num dia so: a tabela guarda uma data e dois horarios.
            case ATIVIDADE -> inserirRetornandoChave("""
                    insert into Atividade (fkcodPacote, fkidRecurso, dataA, horarioInicio,
                                           horarioFim, localA, infoAdicional)
                    values (?, ?, ?, ?, ?, ?, ?)
                    returning idAtividade
                    """,
                    pacoteId, item.getRecursoId(), item.getInicio().toLocalDate(),
                    item.getInicio().toLocalTime(), item.getFim().toLocalTime(),
                    item.getLocalDestino(), item.getInstrucoesOperacionais());
        };

        item.setId(ChaveDoItem.id(item.getTipo(), chave));
        item.setItinerarioId(pacoteId);
    }

    private ItemItinerario mapearItem(Long pacoteId, ResultSet resultado) throws SQLException {
        TipoItemItinerario tipo = TipoItemItinerario.valueOf(resultado.getString("tipo"));

        ItemItinerario item = new ItemItinerario();
        item.setTipo(tipo);
        item.setId(ChaveDoItem.id(tipo, resultado.getLong("iditem")));
        item.setItinerarioId(pacoteId);
        item.setRecursoId(lerLongOuNulo(resultado, "fkidrecurso"));
        item.setNomeServico(resultado.getString("nomeservico"));
        item.setInicio(resultado.getObject("inicio", LocalDateTime.class));
        item.setFim(resultado.getObject("fim", LocalDateTime.class));
        item.setLocalOrigem(resultado.getString("origem"));
        item.setLocalDestino(resultado.getString("destino"));
        item.setInstrucoesOperacionais(resultado.getString("instrucoes"));
        return item;
    }

    private static LocalDateTime momentoDeFinalizacao(Itinerario itinerario) {
        if (itinerario.getStatus() != StatusItinerario.FINALIZADO) {
            return null;
        }
        return itinerario.getDataFinalizacao() == null
                ? LocalDateTime.now() : itinerario.getDataFinalizacao();
    }
}
