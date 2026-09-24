package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.DadosItemItinerario;
import com.tripcontrol.controller.dto.DadosRecurso;
import com.tripcontrol.controller.dto.ItemCronograma;
import com.tripcontrol.controller.dto.PacoteComVagas;
import com.tripcontrol.model.ItemItinerario;
import com.tripcontrol.model.Itinerario;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Recurso;
import com.tripcontrol.model.TipoItemItinerario;
import com.tripcontrol.repository.ItinerarioRepository;
import com.tripcontrol.repository.PacoteRepository;
import com.tripcontrol.repository.RecursoRepository;
import com.tripcontrol.util.Conexoes;
import com.tripcontrol.util.Formatadores;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Regras de aplicacao do UC06 - Montar Itinerario, mais o cadastro de recursos
 * que o caso de uso pressupoe (decisao B da equipe).
 *
 * <p>O itinerario e montado como <strong>rascunho em memoria</strong> e so vai para
 * a persistencia em "Finalizar Itinerario" (passo 11). Isso e o que torna o FA07
 * possivel: descartar a elaboracao e simplesmente jogar o rascunho fora, sem
 * precisar apagar nada. Enquanto o item nao tem itinerario gravado
 * ({@code itinerarioId == null}), a tela o mostra com a tag "Rascunho".</p>
 *
 * <p>A deteccao de conflito e a comparacao de intervalos reaproveitam
 * {@link ItemItinerario#conflitaCom} e {@link Itinerario#buscarConflito}, que ja
 * existiam no dominio.</p>
 */
public class ItinerarioController {

    private final ItinerarioRepository itinerarioRepository;
    private final RecursoRepository recursoRepository;
    private final PacoteRepository pacoteRepository;
    private final PacoteController pacoteController;

    public ItinerarioController(ItinerarioRepository itinerarioRepository,
                                RecursoRepository recursoRepository,
                                PacoteRepository pacoteRepository,
                                PacoteController pacoteController) {
        this.itinerarioRepository = itinerarioRepository;
        this.recursoRepository = recursoRepository;
        this.pacoteRepository = pacoteRepository;
        this.pacoteController = pacoteController;
    }

    // ------------------------------------------------------------------
    // Pesquisa do pacote (UC06, passos 2 e 3)
    // ------------------------------------------------------------------

    /**
     * Busca livre por codigo, destino ou data, como no campo unico do prototipo.
     *
     * @return SUCESSO com os pacotes e a ocupacao ja calculada (o mesmo calculo do
     *         UC04); NAO_ENCONTRADO no FA01; ERRO_VALIDACAO sem termo de pesquisa
     */
    public Resultado<List<PacoteComVagas>> buscarPacotes(String termo) {
        String procurado = Formatadores.textoOuNulo(termo);
        if (procurado == null) {
            return Resultado.erroValidacao("termo",
                    "Informe o código, o destino ou a data do pacote.");
        }

        List<PacoteComVagas> encontrados = pacoteRepository.buscarPorTermo(procurado).stream()
                .map(pacoteController::comVagas)
                .toList();

        // FA01 - nenhum pacote corresponde aos criterios; a tela oferece o UC01.
        if (encontrados.isEmpty()) {
            return Resultado.naoEncontrado(
                    "Nenhum pacote corresponde a \"" + procurado + "\". "
                            + "Revise a pesquisa ou cadastre o pacote em Pacotes.");
        }
        return Resultado.sucesso(encontrados, encontrados.size() + " pacote(s) encontrado(s).");
    }

    /**
     * Abre o itinerario do pacote: o ja gravado, quando existe, ou um rascunho novo
     * (UC06, passo 4).
     *
     * @return SUCESSO com o itinerario; NAO_ENCONTRADO quando o pacote nao existe
     */
    public Resultado<Itinerario> abrir(Long pacoteId) {
        Optional<Pacote> pacote = pacoteRepository.buscarPorId(pacoteId);
        if (pacote.isEmpty()) {
            return Resultado.naoEncontrado("Pacote nao localizado. Refaca a pesquisa.");
        }
        Itinerario itinerario = itinerarioRepository.buscarPorPacote(pacoteId)
                .orElseGet(() -> new Itinerario(pacoteId));
        return Resultado.sucesso(itinerario, null);
    }

    /** Itens do cronograma em ordem cronologica, com o recurso e a marca de rascunho. */
    public List<ItemCronograma> cronograma(Itinerario itinerario) {
        if (itinerario == null) {
            return List.of();
        }
        return itinerario.getItens().stream()
                .sorted(Comparator.comparing(ItemItinerario::getInicio,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(item -> new ItemCronograma(item, recursoDe(item), item.getItinerarioId() == null))
                .toList();
    }

    private Recurso recursoDe(ItemItinerario item) {
        return item.getRecursoId() == null
                ? null : recursoRepository.buscarPorId(item.getRecursoId()).orElse(null);
    }

    // ------------------------------------------------------------------
    // Cadastro de recursos (decisao B; atalho do FA02)
    // ------------------------------------------------------------------

    /** Recursos ja cadastrados do tipo escolhido, usados no combo do passo 5. */
    public List<Recurso> listarRecursos(TipoItemItinerario tipo) {
        List<Recurso> encontrados = tipo == null
                ? recursoRepository.buscarTodos() : recursoRepository.buscarPorTipo(tipo);
        return encontrados.stream()
                .sorted(Comparator.comparing(Recurso::getNome,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    /**
     * Cadastra uma hospedagem, um transporte ou uma atividade (FA02, passo 5.2).
     *
     * @return SUCESSO com o recurso gravado; ERRO_VALIDACAO com os campos pendentes;
     *         DUPLICIDADE, com o recurso existente, quando o par tipo+nome ja existe
     */
    public Resultado<Recurso> cadastrarRecurso(DadosRecurso dados) {
        List<ErroValidacao> erros = new ArrayList<>();

        if (dados.tipo() == null) {
            erros.add(new ErroValidacao("tipo", "Selecione o tipo do cadastro."));
        }
        String nome = Formatadores.textoOuNulo(dados.nome());
        if (nome == null) {
            erros.add(new ErroValidacao("nome", "Informe o nome do serviço."));
        }
        if (!erros.isEmpty()) {
            return Resultado.erroValidacao(erros);
        }

        Optional<Recurso> existente = recursoRepository.buscarPorTipo(dados.tipo()).stream()
                .filter(recurso -> nome.equalsIgnoreCase(recurso.getNome()))
                .findFirst();
        if (existente.isPresent()) {
            return Resultado.duplicidade(existente.get(),
                    "Já existe " + dados.tipo().getDescricao().toLowerCase()
                            + " cadastrada com o nome \"" + nome + "\".");
        }

        Recurso recurso = new Recurso(dados.tipo(), nome, Formatadores.textoOuNulo(dados.local()));
        recurso.setContato(Formatadores.textoOuNulo(dados.contato()));
        recurso.setDescricao(Formatadores.textoOuNulo(dados.descricao()));
        recursoRepository.salvar(recurso);

        return Resultado.sucesso(recurso, dados.tipo().getDescricao() + " \"" + nome + "\" cadastrada.");
    }

    // ------------------------------------------------------------------
    // Adicionar item ao rascunho (UC06, passos 5 a 8)
    // ------------------------------------------------------------------

    /**
     * Valida e adiciona um item ao rascunho do cronograma.
     *
     * @return SUCESSO com o item adicionado; ERRO_VALIDACAO no FA05 (campos
     *         obrigatorios), no FA03 (fora do periodo do pacote) e no FA04
     *         (intervalo invalido ou conflitante)
     */
    public Resultado<ItemItinerario> adicionarItem(Itinerario itinerario,
                                                   Pacote pacote,
                                                   DadosItemItinerario dados) {
        if (itinerario == null || pacote == null) {
            return Resultado.naoEncontrado("Selecione o pacote antes de montar o cronograma.");
        }

        // FA05 - tipo, recurso e campos obrigatorios do tipo escolhido.
        List<ErroValidacao> erros = new ArrayList<>();
        if (dados.tipo() == null) {
            erros.add(new ErroValidacao("tipo", "Selecione o tipo de evento."));
        }
        Recurso recurso = dados.recursoId() == null
                ? null : recursoRepository.buscarPorId(dados.recursoId()).orElse(null);
        if (recurso == null) {
            erros.add(new ErroValidacao("recurso",
                    "Selecione o registro já cadastrado. Use \"Cadastrar novo\" se ele ainda não existir."));
        }

        Optional<LocalDateTime> inicio = Formatadores.lerDataHora(dados.dataInicio(), dados.horaInicio());
        if (inicio.isEmpty()) {
            erros.add(new ErroValidacao("inicio", rotuloInicio(dados.tipo()) + " inválido(a) ou não informado(a)."));
        }

        // Na atividade o termino e no mesmo dia: a tabela Atividade guarda uma data
        // e dois horarios, entao nao existe data de termino para digitar.
        String dataDoFim = dados.tipo() == TipoItemItinerario.ATIVIDADE ? dados.dataInicio() : dados.dataFim();
        Optional<LocalDateTime> fim = Formatadores.lerDataHora(dataDoFim, dados.horaFim());
        if (fim.isEmpty()) {
            erros.add(new ErroValidacao("fim", rotuloFim(dados.tipo()) + " inválido(a) ou não informado(a)."));
        }

        erros.addAll(validarCamposDoTipo(dados));

        if (!erros.isEmpty()) {
            return Resultado.erroValidacao(erros);
        }

        ItemItinerario item = montarItem(dados, recurso, inicio.get(), fim.get());

        // FA04 (primeira parte) - o termino tem de ser posterior ao inicio.
        if (!item.getFim().isAfter(item.getInicio())) {
            return Resultado.erroValidacao("fim",
                    rotuloFim(dados.tipo()) + " deve ser posterior a " + rotuloInicio(dados.tipo()).toLowerCase() + ".");
        }

        // FA03 - o item precisa caber na vigencia do pacote; a mensagem informa o periodo valido.
        if (!pacote.contemPeriodo(item.getInicio().toLocalDate(), item.getFim().toLocalDate())) {
            return Resultado.erroValidacao("inicio",
                    "Data fora do período do pacote. Período válido: "
                            + Formatadores.formatarData(pacote.getDataInicio()) + " a "
                            + Formatadores.formatarData(pacote.getDataFim()) + ".");
        }

        // FA04 (segunda parte) - sobreposicao com outro item ja no cronograma.
        ItemItinerario conflitante = buscarConflitoRelevante(itinerario, item);
        if (conflitante != null) {
            return Resultado.erroValidacao("inicio",
                    "Conflito de horário com \"" + conflitante.getNomeServico() + "\" ("
                            + Formatadores.formatarMomento(conflitante.getInicio()) + " a "
                            + Formatadores.formatarMomento(conflitante.getFim()) + ").");
        }

        itinerario.adicionarItem(item);
        return Resultado.sucesso(item, item.getTipo().getDescricao() + " \""
                + item.getNomeServico() + "\" adicionada ao cronograma.");
    }

    /** Remove um item do rascunho; itens ja gravados exigem nova finalizacao para valer. */
    public void removerItem(Itinerario itinerario, ItemItinerario item) {
        if (itinerario != null) {
            itinerario.removerItem(item);
        }
    }

    // ------------------------------------------------------------------
    // Finalizar e cancelar (UC06, passos 10 e 11; FA06 e FA07)
    // ------------------------------------------------------------------

    /**
     * Grava o itinerario e os seus itens (UC06, passo 11).
     *
     * <p>Cabecalho e itens entram na mesma transacao: um itinerario "Finalizado" com
     * parte dos itens de fora seria pior que nenhum itinerario gravado. Em memoria o
     * bloco apenas executa; na Etapa 6, com as tabelas {@code Hospedagem},
     * {@code Transporte} e {@code Atividade} no banco, ele ja vira transacao JDBC.</p>
     *
     * @return SUCESSO com o itinerario finalizado; OPERACAO_BLOQUEADA no FA06
     */
    public Resultado<Itinerario> finalizar(Itinerario itinerario) {
        if (itinerario == null) {
            return Resultado.naoEncontrado("Selecione o pacote antes de finalizar o itinerário.");
        }

        // FA06 - itinerario sem itens nao pode ser finalizado.
        if (itinerario.isVazio()) {
            return Resultado.operacaoBloqueada(
                    "O itinerário deve possuir ao menos um item antes de ser finalizado.");
        }

        try {
            Conexoes.emTransacao(() -> {
                itinerario.finalizar();
                Itinerario gravado = itinerario.getId() == null
                        ? itinerarioRepository.salvar(itinerario)
                        : itinerarioRepository.atualizar(itinerario);
                // A partir daqui os itens deixam de ser rascunho: pertencem a um
                // itinerario com identidade propria.
                gravado.getItens().forEach(item -> item.setItinerarioId(gravado.getId()));
                return gravado;
            });
        } catch (RuntimeException falha) {
            itinerario.setStatus(com.tripcontrol.model.StatusItinerario.RASCUNHO);
            itinerario.setDataFinalizacao(null);
            return Resultado.operacaoBloqueada(
                    "Não foi possível gravar o itinerário. Nenhuma alteração foi aplicada. Tente novamente.");
        }

        return Resultado.sucesso(itinerario, "Itinerário finalizado com "
                + itinerario.getItens().size() + " item(ns) e vinculado ao pacote.");
    }

    /**
     * FA07 - descarta a elaboracao: os itens que nunca foram gravados somem e a tela
     * volta ao ultimo itinerario persistido do pacote (ou a um rascunho vazio).
     *
     * <p>A confirmacao do passo 4.2 e responsabilidade da tela; aqui so acontece o
     * descarte propriamente dito.</p>
     */
    public Itinerario descartarRascunho(Long pacoteId) {
        Optional<Itinerario> gravado = itinerarioRepository.buscarPorPacote(pacoteId);
        if (gravado.isEmpty()) {
            return new Itinerario(pacoteId);
        }
        Itinerario itinerario = gravado.get();
        // Em memoria o repositorio devolve a mesma instancia que a tela vinha
        // alterando, entao os itens de rascunho precisam sair explicitamente. Com o
        // banco (Etapa 6) esta limpeza fica redundante, mas continua correta: os
        // itens sem itinerario gravado nunca chegaram a existir na persistencia.
        List<ItemItinerario> rascunhos = itinerario.getItens().stream()
                .filter(item -> item.getItinerarioId() == null)
                .toList();
        rascunhos.forEach(itinerario::removerItem);
        return itinerario;
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /**
     * Conflito de horario que realmente impede a inclusao do item (UC06, FA04).
     *
     * <p>Usa {@link Itinerario#buscarConflito} como primeira passada e
     * {@link ItemItinerario#conflitaCom} para refinar, com uma excecao: a hospedagem
     * nao conflita com itens de outro tipo. A hospedagem e o intervalo de fundo da
     * viagem — o passageiro esta hospedado enquanto faz o passeio —, entao trata-la
     * como sobreposicao proibida tornaria impossivel o proprio exemplo do prototipo
     * (hotel de 05/05 a 12/05 com um tour no dia 06/05) e o criterio de pronto da
     * Etapa 5, que pede hospedagem + transporte + atividade no mesmo roteiro.</p>
     *
     * <p>Duas hospedagens sobrepostas continuam em conflito, assim como transporte
     * contra atividade.</p>
     */
    // TODO: o FA04 do documento de requisitos diz apenas "o intervalo se sobrepoe a
    //  outro item do itinerario", sem excecao. Lido ao pe da letra, nenhum passeio
    //  poderia acontecer durante a hospedagem, o que contradiz o protótipo e o
    //  critério de pronto. A equipe precisa confirmar com o professor se a
    //  hospedagem e mesmo um intervalo de fundo (como implementado aqui) ou se o
    //  conflito deve valer entre todos os tipos.
    private ItemItinerario buscarConflitoRelevante(Itinerario itinerario, ItemItinerario candidato) {
        if (itinerario.buscarConflito(candidato) == null) {
            return null;
        }
        return itinerario.getItens().stream()
                .filter(existente -> !hospedagemCobreOutroTipo(existente, candidato))
                .filter(existente -> existente.conflitaCom(candidato))
                .findFirst()
                .orElse(null);
    }

    /** @return {@code true} quando um dos dois e hospedagem e o outro nao. */
    private static boolean hospedagemCobreOutroTipo(ItemItinerario um, ItemItinerario outro) {
        boolean umEHospedagem = um.getTipo() == TipoItemItinerario.HOSPEDAGEM;
        boolean outroEHospedagem = outro.getTipo() == TipoItemItinerario.HOSPEDAGEM;
        return umEHospedagem != outroEHospedagem;
    }

    private ItemItinerario montarItem(DadosItemItinerario dados, Recurso recurso,
                                      LocalDateTime inicio, LocalDateTime fim) {
        ItemItinerario item = new ItemItinerario(dados.tipo(), recurso.getNome(), inicio, fim);
        item.setRecursoId(recurso.getId());
        item.setInstrucoesOperacionais(Formatadores.textoOuNulo(dados.instrucoesOperacionais()));

        if (dados.tipo() == TipoItemItinerario.TRANSPORTE) {
            item.setLocalOrigem(Formatadores.textoOuNulo(dados.localOrigem()));
            item.setLocalDestino(Formatadores.textoOuNulo(dados.localDestino()));
        } else if (dados.tipo() == TipoItemItinerario.ATIVIDADE) {
            // A atividade tem um unico local (o de encontro). Ele ocupa localDestino
            // porque e para onde o passageiro vai; e o campo que a Etapa 6 vai gravar
            // em Atividade.localA.
            item.setLocalDestino(Formatadores.textoOuNulo(dados.localDestino()));
        }
        return item;
    }

    /** FA05 - campos que so existem em alguns tipos (tabela Transporte e Atividade). */
    private List<ErroValidacao> validarCamposDoTipo(DadosItemItinerario dados) {
        List<ErroValidacao> erros = new ArrayList<>();
        if (dados.tipo() == TipoItemItinerario.TRANSPORTE) {
            if (Formatadores.textoOuNulo(dados.localOrigem()) == null) {
                erros.add(new ErroValidacao("localOrigem", "Informe o local de origem."));
            }
            if (Formatadores.textoOuNulo(dados.localDestino()) == null) {
                erros.add(new ErroValidacao("localDestino", "Informe o local de destino."));
            }
        } else if (dados.tipo() == TipoItemItinerario.ATIVIDADE
                && Formatadores.textoOuNulo(dados.localDestino()) == null) {
            erros.add(new ErroValidacao("localDestino", "Informe o local de encontro da atividade."));
        }
        return erros;
    }

    private static String rotuloInicio(TipoItemItinerario tipo) {
        if (tipo == TipoItemItinerario.HOSPEDAGEM) {
            return "Data/hora de check-in";
        }
        if (tipo == TipoItemItinerario.TRANSPORTE) {
            return "Data/hora de saída";
        }
        return "Data e hora de início";
    }

    private static String rotuloFim(TipoItemItinerario tipo) {
        if (tipo == TipoItemItinerario.HOSPEDAGEM) {
            return "Data/hora de check-out";
        }
        if (tipo == TipoItemItinerario.TRANSPORTE) {
            return "Data/hora de chegada";
        }
        return "Hora de término";
    }

    /** @return periodo do pacote no formato usado nas mensagens de FA03. */
    public String periodoDoPacote(Pacote pacote) {
        if (pacote == null) {
            return "-";
        }
        return Formatadores.formatarData(pacote.getDataInicio()) + " a "
                + Formatadores.formatarData(pacote.getDataFim())
                + " (" + pacote.duracaoEmNoites() + " Noites)";
    }

    /** Data sugerida ao abrir o formulario: o primeiro dia do pacote. */
    public LocalDate primeiroDia(Pacote pacote) {
        return pacote == null ? null : pacote.getDataInicio();
    }
}
