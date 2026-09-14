package com.tripcontrol.app;

import com.tripcontrol.controller.AutenticacaoController;
import com.tripcontrol.controller.ClienteController;
import com.tripcontrol.controller.PacoteController;
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

/**
 * Ponto unico de montagem das dependencias da aplicacao (injecao manual).
 *
 * <p><strong>Este e o unico arquivo que precisara mudar quando o banco entrar.</strong>
 * Trocar {@code new InMemoryPacoteRepository()} por {@code new JdbcPacoteRepository(...)}
 * -- e assim por diante -- basta para migrar a persistencia: Controllers e Views
 * dependem somente das interfaces de repositorio.</p>
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

    private final PacoteController pacoteController;
    private final ClienteController clienteController;
    private final ReservaController reservaController;
    private final AutenticacaoController autenticacaoController;

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

        this.pacoteController = new PacoteController(pacoteRepository, reservaRepository);
        this.clienteController = new ClienteController(clienteRepository, reservaRepository, pacoteRepository);
        this.reservaController = new ReservaController(reservaRepository, pacoteRepository,
                clienteRepository, pacoteController);
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
