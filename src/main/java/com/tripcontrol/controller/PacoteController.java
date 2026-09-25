package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.DadosPacote;
import com.tripcontrol.controller.dto.PacoteComVagas;
import com.tripcontrol.model.AlteracaoCapacidade;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.model.SituacaoPacote;
import com.tripcontrol.repository.PacoteRepository;
import com.tripcontrol.repository.ReservaRepository;
import com.tripcontrol.util.Conexoes;
import com.tripcontrol.util.Formatadores;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Regras de aplicacao dos casos de uso UC01 (Cadastrar Pacotes) e
 * UC04 (Controlar Vagas).
 *
 * <p>O Controller nao conhece JavaFX nem banco de dados: recebe os dados
 * digitados, aplica as validacoes dos fluxos principal e alternativos e
 * conversa apenas com as interfaces de repositorio.</p>
 */
public class PacoteController {

    private final PacoteRepository pacoteRepository;
    private final ReservaRepository reservaRepository;
    private final Clock relogio;

    public PacoteController(PacoteRepository pacoteRepository, ReservaRepository reservaRepository) {
        this(pacoteRepository, reservaRepository, Clock.systemDefaultZone());
    }

    /** Construtor com relogio injetavel: usado pelos testes para fixar "hoje". */
    public PacoteController(PacoteRepository pacoteRepository, ReservaRepository reservaRepository, Clock relogio) {
        this.pacoteRepository = pacoteRepository;
        this.reservaRepository = reservaRepository;
        this.relogio = relogio;
    }

    // ------------------------------------------------------------------
    // UC01 - Cadastrar Pacotes
    // ------------------------------------------------------------------

    /**
     * Cadastra um novo pacote (UC01, passos 4 a 6).
     *
     * @return SUCESSO com o pacote gravado; ERRO_VALIDACAO nas situacoes FA01 e FA02;
     *         DUPLICIDADE, com o pacote ja existente, na situacao FA03
     */
    public Resultado<Pacote> cadastrar(DadosPacote dados) {
        List<ErroValidacao> erros = validarCampos(dados);
        if (!erros.isEmpty()) {
            return Resultado.erroValidacao(erros);
        }

        LocalDate inicio = Formatadores.lerData(dados.dataInicio()).orElseThrow();
        LocalDate fim = Formatadores.lerData(dados.dataFim()).orElseThrow();

        // FA02 - o inicio no passado so e barrado em cadastros novos.
        if (inicio.isBefore(LocalDate.now(relogio))) {
            return Resultado.erroValidacao("dataInicio",
                    "A data de inicio nao pode ser anterior a data atual.");
        }

        // FA03 - mesmo destino e exatamente o mesmo periodo.
        Optional<Pacote> existente = pacoteRepository.buscarPorDestinoEPeriodo(dados.destino().trim(), inicio, fim);
        if (existente.isPresent()) {
            return Resultado.duplicidade(existente.get(),
                    "Ja existe o pacote " + existente.get().getCodigo() + " para " + dados.destino().trim()
                            + " no periodo de " + Formatadores.formatarData(inicio)
                            + " a " + Formatadores.formatarData(fim) + ". Deseja abrir o cadastro existente?");
        }

        Pacote pacote = new Pacote();
        pacote.setCodigo(pacoteRepository.proximoCodigo());
        aplicarDados(pacote, dados, inicio, fim);
        pacoteRepository.salvar(pacote);

        return Resultado.sucesso(pacote,
                "Pacote " + pacote.getCodigo() + " cadastrado com sucesso para " + pacote.getDestino() + ".");
    }

    /**
     * Atualiza um pacote existente, caminho oferecido ao funcionario quando o
     * cadastro esbarra na duplicidade (UC01, FA03, passo 4.3).
     */
    public Resultado<Pacote> atualizar(Long pacoteId, DadosPacote dados) {
        Optional<Pacote> encontrado = pacoteRepository.buscarPorId(pacoteId);
        if (encontrado.isEmpty()) {
            return Resultado.naoEncontrado("Pacote nao localizado para edicao.");
        }

        List<ErroValidacao> erros = validarCampos(dados);
        if (!erros.isEmpty()) {
            return Resultado.erroValidacao(erros);
        }

        Pacote pacote = encontrado.get();
        LocalDate inicio = Formatadores.lerData(dados.dataInicio()).orElseThrow();
        LocalDate fim = Formatadores.lerData(dados.dataFim()).orElseThrow();

        int ocupadas = vagasOcupadas(pacoteId);
        int novaCapacidade = Formatadores.lerInteiro(dados.capacidadeTotal()).orElseThrow();
        if (novaCapacidade < ocupadas) {
            return Resultado.erroValidacao("capacidadeTotal",
                    "A capacidade nao pode ser menor que as " + ocupadas + " vagas ja ocupadas.");
        }

        Optional<Pacote> duplicado = pacoteRepository.buscarPorDestinoEPeriodo(dados.destino().trim(), inicio, fim);
        if (duplicado.isPresent() && !duplicado.get().getId().equals(pacoteId)) {
            return Resultado.duplicidade(duplicado.get(),
                    "Outro pacote (" + duplicado.get().getCodigo() + ") ja usa esse destino e periodo.");
        }

        aplicarDados(pacote, dados, inicio, fim);
        pacoteRepository.atualizar(pacote);
        return Resultado.sucesso(pacote, "Pacote " + pacote.getCodigo() + " atualizado com sucesso.");
    }

    // ------------------------------------------------------------------
    // UC04 - Controlar Vagas
    // ------------------------------------------------------------------

    /** @return soma dos viajantes das reservas ativas do pacote (UC04, passo 4). */
    public int vagasOcupadas(Long pacoteId) {
        return reservaRepository.buscarAtivasPorPacote(pacoteId).stream()
                .mapToInt(Reserva::getQuantidadeViajantes)
                .sum();
    }

    /** Monta a linha de ocupacao de um pacote especifico. */
    public PacoteComVagas comVagas(Pacote pacote) {
        int ocupadas = vagasOcupadas(pacote.getId());
        return new PacoteComVagas(pacote, ocupadas, pacote.vagasDisponiveis(ocupadas),
                pacote.situacao(ocupadas, LocalDate.now(relogio)));
    }

    /** Listagem completa da tela Vagas, ordenada por data de inicio (UC04, passo 2). */
    public List<PacoteComVagas> listarComVagas() {
        return pacoteRepository.buscarTodos().stream()
                .sorted(Comparator.comparing(Pacote::getDataInicio,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::comVagas)
                .toList();
    }

    public List<Pacote> listar() {
        return pacoteRepository.buscarTodos();
    }

    /** Pacotes que ainda aceitam reservas, usados no combo da tela de reservas. */
    public List<Pacote> listarDisponiveis() {
        return listarComVagas().stream()
                .filter(linha -> linha.situacao() == SituacaoPacote.DISPONIVEL)
                .map(PacoteComVagas::pacote)
                .toList();
    }

    public List<Pacote> buscar(String termo) {
        return pacoteRepository.buscarPorTermo(termo);
    }

    public Optional<Pacote> buscarPorId(Long id) {
        return pacoteRepository.buscarPorId(id);
    }

    /**
     * Altera a capacidade total de um pacote (UC04, passos 5 a 8).
     *
     * @param ocupadasNaAberturaDaTela vagas ocupadas exibidas quando a tela foi carregada;
     *                                 divergencia indica alteracao por outro processo (FA04)
     */
    public Resultado<PacoteComVagas> alterarCapacidade(Long pacoteId,
                                                       String novaCapacidadeTexto,
                                                       String justificativa,
                                                       String usuarioResponsavel,
                                                       int ocupadasNaAberturaDaTela) {
        Optional<Pacote> encontrado = pacoteRepository.buscarPorId(pacoteId);
        if (encontrado.isEmpty()) {
            return Resultado.naoEncontrado("Pacote nao localizado.");
        }
        Pacote pacote = encontrado.get();

        List<ErroValidacao> erros = new ArrayList<>();
        Optional<Integer> novaCapacidade = Formatadores.lerInteiro(novaCapacidadeTexto);
        if (novaCapacidade.isEmpty()) {
            erros.add(new ErroValidacao("novaCapacidade", "Informe a nova capacidade como numero inteiro."));
        } else if (novaCapacidade.get() <= 0) {
            erros.add(new ErroValidacao("novaCapacidade", "A capacidade deve ser um numero inteiro positivo."));
        }
        if (Formatadores.textoOuNulo(justificativa) == null) {
            erros.add(new ErroValidacao("justificativa", "A justificativa da alteracao e obrigatoria."));
        }
        if (!erros.isEmpty()) {
            return Resultado.erroValidacao(erros);
        }

        int ocupadasAgora = vagasOcupadas(pacoteId);

        // FA04 - as reservas mudaram depois que a tela foi aberta.
        if (ocupadasAgora != ocupadasNaAberturaDaTela) {
            return Resultado.conflitoConcorrencia(comVagas(pacote),
                    "As reservas deste pacote mudaram enquanto a tela estava aberta. "
                            + "Os dados foram recarregados: revise antes de salvar.");
        }

        // FA03 - nova capacidade menor que as vagas ja ocupadas.
        if (novaCapacidade.get() < ocupadasAgora) {
            return Resultado.erroValidacao("novaCapacidade",
                    "Valor minimo permitido: " + ocupadasAgora + " (vagas ja ocupadas).");
        }

        // A nova capacidade e o registro da justificativa entram juntos (UC04, passo 7).
        Conexoes.emTransacao(() -> {
            AlteracaoCapacidade alteracao = new AlteracaoCapacidade(pacoteId, pacote.getCapacidadeTotal(),
                    novaCapacidade.get(), justificativa.trim(), usuarioResponsavel);
            pacote.setCapacidadeTotal(novaCapacidade.get());
            pacote.registrarAlteracaoCapacidade(alteracao);
            return pacoteRepository.atualizar(pacote);
        });

        PacoteComVagas atualizado = comVagas(pacote);
        return Resultado.sucesso(atualizado,
                "Capacidade do pacote " + pacote.getCodigo() + " atualizada para "
                        + novaCapacidade.get() + " vagas (" + atualizado.vagasDisponiveis() + " livres).");
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /** Validacoes comuns ao cadastro e a edicao (UC01, FA01 e FA02). */
    private List<ErroValidacao> validarCampos(DadosPacote dados) {
        List<ErroValidacao> erros = new ArrayList<>();

        if (Formatadores.textoOuNulo(dados.destino()) == null) {
            erros.add(new ErroValidacao("destino", "Informe o destino do pacote."));
        }

        Optional<LocalDate> inicio = Formatadores.lerData(dados.dataInicio());
        Optional<LocalDate> fim = Formatadores.lerData(dados.dataFim());
        if (inicio.isEmpty()) {
            erros.add(new ErroValidacao("dataInicio", "Informe a data de inicio no formato DD/MM/AAAA."));
        }
        if (fim.isEmpty()) {
            erros.add(new ErroValidacao("dataFim", "Informe a data de fim no formato DD/MM/AAAA."));
        }
        if (inicio.isPresent() && fim.isPresent() && !fim.get().isAfter(inicio.get())) {
            erros.add(new ErroValidacao("dataFim", "A data de fim deve ser posterior a data de inicio."));
        }

        Optional<BigDecimal> preco = Formatadores.lerValorMonetario(dados.preco());
        if (preco.isEmpty()) {
            erros.add(new ErroValidacao("preco", "Informe o preco do pacote."));
        } else if (preco.get().compareTo(BigDecimal.ZERO) <= 0) {
            erros.add(new ErroValidacao("preco", "O preco deve ser maior que zero."));
        }

        Optional<Integer> capacidade = Formatadores.lerInteiro(dados.capacidadeTotal());
        if (capacidade.isEmpty()) {
            erros.add(new ErroValidacao("capacidadeTotal", "Informe a capacidade total como numero inteiro."));
        } else if (capacidade.get() <= 0) {
            erros.add(new ErroValidacao("capacidadeTotal", "A capacidade deve ser maior que zero."));
        }

        return erros;
    }

    private void aplicarDados(Pacote pacote, DadosPacote dados, LocalDate inicio, LocalDate fim) {
        pacote.setDestino(dados.destino().trim());
        pacote.setDataInicio(inicio);
        pacote.setDataFim(fim);
        pacote.setDescricao(Formatadores.textoOuNulo(dados.descricao()));
        pacote.setPreco(Formatadores.lerValorMonetario(dados.preco()).orElseThrow());
        pacote.setCapacidadeTotal(Formatadores.lerInteiro(dados.capacidadeTotal()).orElseThrow());
        pacote.setRoteiroPrevisto(Formatadores.textoOuNulo(dados.roteiroPrevisto()));
    }
}
