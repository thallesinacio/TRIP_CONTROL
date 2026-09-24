package com.tripcontrol.app;

import com.tripcontrol.controller.AutenticacaoController;
import com.tripcontrol.controller.CalculadoraFinanceira;
import com.tripcontrol.controller.ClienteController;
import com.tripcontrol.controller.ItinerarioController;
import com.tripcontrol.controller.PacoteController;
import com.tripcontrol.controller.PagamentoController;
import com.tripcontrol.controller.RelatorioController;
import com.tripcontrol.controller.ReservaController;
import com.tripcontrol.repository.ClienteRepository;
import com.tripcontrol.repository.ItinerarioRepository;
import com.tripcontrol.repository.PacoteRepository;
import com.tripcontrol.repository.PagamentoRepository;
import com.tripcontrol.repository.ParcelaRepository;
import com.tripcontrol.repository.RecursoRepository;
import com.tripcontrol.repository.ReservaRepository;
import com.tripcontrol.repository.UsuarioRepository;
import com.tripcontrol.repository.memory.InMemoryClienteRepository;
import com.tripcontrol.repository.memory.InMemoryItinerarioRepository;
import com.tripcontrol.repository.memory.InMemoryPacoteRepository;
import com.tripcontrol.repository.memory.InMemoryPagamentoRepository;
import com.tripcontrol.repository.memory.InMemoryParcelaRepository;
import com.tripcontrol.repository.memory.InMemoryRecursoRepository;
import com.tripcontrol.repository.memory.InMemoryReservaRepository;
import com.tripcontrol.repository.memory.InMemoryUsuarioRepository;
import com.tripcontrol.repository.jdbc.JdbcClienteRepository;
import com.tripcontrol.repository.jdbc.JdbcItinerarioRepository;
import com.tripcontrol.repository.jdbc.JdbcPacoteRepository;
import com.tripcontrol.repository.jdbc.JdbcPagamentoRepository;
import com.tripcontrol.repository.jdbc.JdbcParcelaRepository;
import com.tripcontrol.repository.jdbc.JdbcRecursoRepository;
import com.tripcontrol.repository.jdbc.JdbcReservaRepository;
import com.tripcontrol.repository.jdbc.JdbcUsuarioRepository;
import com.tripcontrol.repository.relatorio.JdbcRelatorioRepository;
import com.tripcontrol.repository.relatorio.RelatorioIndisponivel;
import com.tripcontrol.repository.relatorio.RelatorioRepository;
import com.tripcontrol.util.ConexaoIndisponivelException;
import com.tripcontrol.util.ConfiguracaoAplicacao;
import com.tripcontrol.util.ConnectionFactory;
import com.tripcontrol.util.Migracoes;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Ponto unico de montagem das dependencias da aplicacao (injecao manual).
 *
 * <p><strong>Este e o unico arquivo que sabe qual persistencia esta em uso.</strong>
 * A troca entre memoria e PostgreSQL acontece aqui, em {@link #criar()}, guiada pela
 * chave {@code repositorio.tipo}: Controllers e Views dependem somente das interfaces
 * de repositorio e nao mudaram uma linha por causa do banco.</p>
 */
public class ContextoAplicacao {

    private final PacoteRepository pacoteRepository;
    private final ClienteRepository clienteRepository;
    private final ReservaRepository reservaRepository;
    private final PagamentoRepository pagamentoRepository;
    private final ParcelaRepository parcelaRepository;
    private final ItinerarioRepository itinerarioRepository;
    private final RecursoRepository recursoRepository;
    private final UsuarioRepository usuarioRepository;
    private final RelatorioRepository relatorioRepository;

    private final CalculadoraFinanceira calculadoraFinanceira;
    private final PacoteController pacoteController;
    private final ClienteController clienteController;
    private final ReservaController reservaController;
    private final PagamentoController pagamentoController;
    private final ItinerarioController itinerarioController;
    private final RelatorioController relatorioController;
    private final AutenticacaoController autenticacaoController;

    /**
     * Monta o contexto conforme a chave {@code repositorio.tipo}.
     *
     * <p>Com {@code jdbc} — o padrao —, aplica as migracoes e usa os repositorios
     * JDBC de <strong>todas</strong> as entidades. Com {@code memoria}, tudo fica em
     * memoria e o PostgreSQL nem e procurado: e o plano B da apresentacao e o que
     * permite a suite de testes rodar sem banco instalado.</p>
     */
    public static ContextoAplicacao criar() {
        if (ConfiguracaoAplicacao.tipoDeRepositorio() != ConfiguracaoAplicacao.TipoRepositorio.JDBC) {
            return new ContextoAplicacao();
        }
        try {
            Migracoes.aplicar(ConnectionFactory.getDataSource());
        } catch (RuntimeException falha) {
            throw new ConexaoIndisponivelException(
                    "Nao foi possivel preparar o banco de dados: " + falha.getMessage(), falha);
        }
        return new ContextoAplicacao(
                new JdbcPacoteRepository(),
                new JdbcClienteRepository(),
                new JdbcReservaRepository(),
                new JdbcPagamentoRepository(),
                new JdbcParcelaRepository(),
                new JdbcItinerarioRepository(),
                new JdbcRecursoRepository(),
                new JdbcUsuarioRepository());
    }

    /** @return {@code true} quando os dados estao no PostgreSQL, nao em memoria. */
    public boolean isPersistenciaEmBanco() {
        return !emMemoria(pacoteRepository);
    }

    /** Estatico de proposito: o construtor precisa decidir isto antes de terminar. */
    private static boolean emMemoria(PacoteRepository repositorio) {
        return repositorio instanceof com.tripcontrol.repository.memory.InMemoryPacoteRepository;
    }

    /** Estado exibido no selo do menu lateral. */
    public enum EstadoDaPersistencia {
        CONECTADO("Conectado ao Banco"),
        SEM_CONEXAO("Sem conexão com o banco"),
        MEMORIA("Modo memória (plano B)");

        private final String descricao;

        EstadoDaPersistencia(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }
    }

    /**
     * Confere de verdade se o banco responde, em vez de apenas olhar a configuracao.
     *
     * <p>O selo do prototipo diz "Conectado ao Banco"; para isso ser uma informacao
     * e nao um enfeite, ele precisa distinguir tres situacoes: rodando sobre o
     * PostgreSQL, rodando em memoria por escolha, e configurado para o banco mas sem
     * conseguir falar com ele (o servico caiu depois que a aplicacao abriu).</p>
     */
    public EstadoDaPersistencia estadoDaPersistencia() {
        if (!isPersistenciaEmBanco()) {
            return EstadoDaPersistencia.MEMORIA;
        }
        try (Connection conexao = ConnectionFactory.abrirConexao()) {
            return conexao.isValid(2)
                    ? EstadoDaPersistencia.CONECTADO : EstadoDaPersistencia.SEM_CONEXAO;
        } catch (SQLException | RuntimeException falha) {
            return EstadoDaPersistencia.SEM_CONEXAO;
        }
    }

    /** Monta o contexto com as implementacoes em memoria (fase atual do projeto). */
    public ContextoAplicacao() {
        this(new InMemoryPacoteRepository(),
                new InMemoryClienteRepository(),
                new InMemoryReservaRepository(),
                new InMemoryPagamentoRepository(),
                new InMemoryParcelaRepository(),
                new InMemoryItinerarioRepository(),
                new InMemoryRecursoRepository(),
                new InMemoryUsuarioRepository());
    }

    /**
     * Monta o contexto com repositorios arbitrarios.
     * E por aqui que as implementacoes JDBC entrarao na proxima fase, e e o que
     * permite aos testes usarem duplos de teste sem subir a interface grafica.
     */
    public ContextoAplicacao(PacoteRepository pacoteRepository,
                             ClienteRepository clienteRepository,
                             ReservaRepository reservaRepository,
                             PagamentoRepository pagamentoRepository,
                             ParcelaRepository parcelaRepository,
                             ItinerarioRepository itinerarioRepository,
                             RecursoRepository recursoRepository,
                             UsuarioRepository usuarioRepository) {
        this.pacoteRepository = pacoteRepository;
        this.clienteRepository = clienteRepository;
        this.reservaRepository = reservaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.parcelaRepository = parcelaRepository;
        this.itinerarioRepository = itinerarioRepository;
        this.recursoRepository = recursoRepository;
        this.usuarioRepository = usuarioRepository;

        this.calculadoraFinanceira = new CalculadoraFinanceira(parcelaRepository, pagamentoRepository);
        this.pacoteController = new PacoteController(pacoteRepository, reservaRepository);
        this.clienteController = new ClienteController(clienteRepository, reservaRepository, pacoteRepository);
        this.reservaController = new ReservaController(reservaRepository, pacoteRepository,
                clienteRepository, calculadoraFinanceira, pacoteController);
        this.pagamentoController = new PagamentoController(reservaController, reservaRepository,
                parcelaRepository, pagamentoRepository, clienteRepository, pacoteRepository,
                calculadoraFinanceira);
        this.itinerarioController = new ItinerarioController(itinerarioRepository,
                recursoRepository, pacoteRepository, pacoteController);

        // O UC08 exige banco disponivel. Em memoria, a implementacao "indisponivel"
        // faz a tela explicar isso em vez de duplicar as cinco consultas agregadas.
        this.relatorioRepository = emMemoria(pacoteRepository)
                ? new RelatorioIndisponivel() : new JdbcRelatorioRepository();
        this.relatorioController = new RelatorioController(relatorioRepository);
        this.autenticacaoController = new AutenticacaoController(usuarioRepository);
    }

    public PacoteController getPacoteController() {
        return pacoteController;
    }

    public ClienteController getClienteController() {
        return clienteController;
    }

    public ReservaController getReservaController() {
        return reservaController;
    }

    public PagamentoController getPagamentoController() {
        return pagamentoController;
    }

    public ItinerarioController getItinerarioController() {
        return itinerarioController;
    }

    public RelatorioController getRelatorioController() {
        return relatorioController;
    }

    public RelatorioRepository getRelatorioRepository() {
        return relatorioRepository;
    }

    public CalculadoraFinanceira getCalculadoraFinanceira() {
        return calculadoraFinanceira;
    }

    public AutenticacaoController getAutenticacaoController() {
        return autenticacaoController;
    }

    public PacoteRepository getPacoteRepository() {
        return pacoteRepository;
    }

    public ClienteRepository getClienteRepository() {
        return clienteRepository;
    }

    public ReservaRepository getReservaRepository() {
        return reservaRepository;
    }

    public PagamentoRepository getPagamentoRepository() {
        return pagamentoRepository;
    }

    public ParcelaRepository getParcelaRepository() {
        return parcelaRepository;
    }

    public ItinerarioRepository getItinerarioRepository() {
        return itinerarioRepository;
    }

    public RecursoRepository getRecursoRepository() {
        return recursoRepository;
    }

    public UsuarioRepository getUsuarioRepository() {
        return usuarioRepository;
    }
}
