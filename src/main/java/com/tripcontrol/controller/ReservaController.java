package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.DadosReserva;
import com.tripcontrol.controller.dto.ResumoReserva;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.repository.ClienteRepository;
import com.tripcontrol.repository.PacoteRepository;
import com.tripcontrol.repository.ReservaRepository;
import com.tripcontrol.util.Formatadores;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Regras de aplicacao do UC03 (Registrar Reservas).
 *
 * <p>A disponibilidade e sempre recalculada no momento da gravacao a partir das
 * reservas ativas (UC04), e nao de um contador guardado no pacote: assim duas
 * reservas concorrentes nunca ultrapassam a capacidade.</p>
 */
public class ReservaController {

    private final ReservaRepository reservaRepository;
    private final PacoteRepository pacoteRepository;
    private final ClienteRepository clienteRepository;
    private final PacoteController pacoteController;

    public ReservaController(ReservaRepository reservaRepository,
                             PacoteRepository pacoteRepository,
                             ClienteRepository clienteRepository,
                             PacoteController pacoteController) {
        this.reservaRepository = reservaRepository;
        this.pacoteRepository = pacoteRepository;
        this.clienteRepository = clienteRepository;
        this.pacoteController = pacoteController;
    }

    /**
     * Registra a reserva (UC03, passos 4 a 6).
     *
     * @return SUCESSO com o resumo usado no comprovante; ERRO_VALIDACAO nas
     *         situacoes FA01 (vagas insuficientes) e FA02 (periodo incompativel);
     *         NAO_ENCONTRADO quando cliente ou pacote nao existem mais
     */
    public Resultado<ResumoReserva> registrar(DadosReserva dados) {
        List<ErroValidacao> erros = new ArrayList<>();

        if (dados.clienteId() == null) {
            erros.add(new ErroValidacao("cliente", "Selecione o cliente da reserva."));
        }
        if (dados.pacoteId() == null) {
            erros.add(new ErroValidacao("pacote", "Selecione o pacote de viagem."));
        }

        Optional<Integer> quantidade = Formatadores.lerInteiro(dados.quantidadeViajantes());
        if (quantidade.isEmpty()) {
            erros.add(new ErroValidacao("quantidadeViajantes",
                    "Informe a quantidade de viajantes como numero inteiro."));
        } else if (quantidade.get() <= 0) {
            erros.add(new ErroValidacao("quantidadeViajantes", "A quantidade de viajantes deve ser maior que zero."));
        }

        Optional<LocalDate> inicio = Formatadores.lerData(dados.dataInicio());
        Optional<LocalDate> fim = Formatadores.lerData(dados.dataFim());
        if (inicio.isEmpty()) {
            erros.add(new ErroValidacao("dataInicio", "Informe a data de inicio da viagem."));
        }
        if (fim.isEmpty()) {
            erros.add(new ErroValidacao("dataFim", "Informe a data de fim da viagem."));
        }
        if (inicio.isPresent() && fim.isPresent() && fim.get().isBefore(inicio.get())) {
            erros.add(new ErroValidacao("dataFim", "A data de fim deve ser posterior a data de inicio."));
        }

        if (!erros.isEmpty()) {
            return Resultado.erroValidacao(erros);
        }

        Optional<Cliente> cliente = clienteRepository.buscarPorId(dados.clienteId());
        if (cliente.isEmpty()) {
            return Resultado.naoEncontrado("Cliente nao localizado. Atualize a lista e tente novamente.");
        }
        Optional<Pacote> pacoteEncontrado = pacoteRepository.buscarPorId(dados.pacoteId());
        if (pacoteEncontrado.isEmpty()) {
            return Resultado.naoEncontrado("Pacote nao localizado. Atualize a lista e tente novamente.");
        }
        Pacote pacote = pacoteEncontrado.get();

        // FA02 - periodo fora da vigencia do pacote.
        if (!pacote.contemPeriodo(inicio.get(), fim.get())) {
            return Resultado.erroValidacao("dataInicio",
                    "Periodo incompativel com o pacote. Datas validas: "
                            + Formatadores.formatarData(pacote.getDataInicio()) + " a "
                            + Formatadores.formatarData(pacote.getDataFim()) + ".");
        }

        // FA01 - vagas insuficientes (recalculadas agora, nao no carregamento da tela).
        int ocupadas = pacoteController.vagasOcupadas(pacote.getId());
        int disponiveis = pacote.vagasDisponiveis(ocupadas);
        if (quantidade.get() > disponiveis) {
            return Resultado.erroValidacao("quantidadeViajantes",
                    "Vagas insuficientes para o pacote selecionado (Vagas disponiveis: " + disponiveis + ").");
        }

        Reserva reserva = new Reserva(cliente.get().getId(), pacote.getId(), quantidade.get(),
                inicio.get(), fim.get(), Formatadores.textoOuNulo(dados.observacoes()));
        reserva.setCodigo(reservaRepository.proximoCodigo());
        reserva.setValorTotal(calcularValorTotal(pacote, quantidade.get()));
        reservaRepository.salvar(reserva);

        ResumoReserva resumo = new ResumoReserva(reserva, cliente.get(), pacote);
        return Resultado.sucesso(resumo,
                "Reserva " + reserva.getCodigo() + " confirmada para " + cliente.get().getNome()
                        + " - " + quantidade.get() + " viajante(s), total "
                        + Formatadores.formatarMoeda(reserva.getValorTotal()) + ".");
    }

    /** @return valor total da reserva: preco unitario do pacote x quantidade de viajantes. */
    public BigDecimal calcularValorTotal(Pacote pacote, int quantidadeViajantes) {
        BigDecimal preco = pacote.getPreco() == null ? BigDecimal.ZERO : pacote.getPreco();
        return preco.multiply(BigDecimal.valueOf(quantidadeViajantes));
    }

    public List<ResumoReserva> listar() {
        return reservaRepository.buscarTodos().stream()
                .sorted(Comparator.comparing(Reserva::getDataRegistro,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::montarResumo)
                .toList();
    }

    public Optional<ResumoReserva> buscarPorCodigo(String codigo) {
        return reservaRepository.buscarPorCodigo(codigo).map(this::montarResumo);
    }

    public List<ResumoReserva> buscarPorPacote(Long pacoteId) {
        return reservaRepository.buscarPorPacote(pacoteId).stream().map(this::montarResumo).toList();
    }

    private ResumoReserva montarResumo(Reserva reserva) {
        Cliente cliente = clienteRepository.buscarPorId(reserva.getClienteId()).orElse(null);
        Pacote pacote = pacoteRepository.buscarPorId(reserva.getPacoteId()).orElse(null);
        return new ResumoReserva(reserva, cliente, pacote);
    }
}
