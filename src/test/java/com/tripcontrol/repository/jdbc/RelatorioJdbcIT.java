package com.tripcontrol.repository.jdbc;

import com.tripcontrol.controller.RelatorioController;
import com.tripcontrol.controller.Resultado;
import com.tripcontrol.controller.StatusResultado;
import com.tripcontrol.controller.dto.FiltrosRelatorio;
import com.tripcontrol.controller.dto.Relatorio;
import com.tripcontrol.controller.dto.TotalRelatorio;
import com.tripcontrol.model.Cancelamento;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.CriterioRanking;
import com.tripcontrol.model.FormaPagamento;
import com.tripcontrol.model.MotivoCancelamento;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Pagamento;
import com.tripcontrol.model.Parcela;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.model.SituacaoFinanceira;
import com.tripcontrol.model.SituacaoPacote;
import com.tripcontrol.model.StatusReserva;
import com.tripcontrol.model.TipoRelatorio;
import com.tripcontrol.repository.relatorio.JdbcRelatorioRepository;
import com.tripcontrol.util.Formatadores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As cinco consultas do UC08 contra o PostgreSQL, com massa controlada.
 *
 * <p>Cada teste roda numa transacao desfeita no fim, mas os filtros ainda usam uma
 * marca aleatoria (destinos, CPFs e nomes com sufixo unico) para que as assercoes
 * valham mesmo se o banco de teste tiver dados de outras execucoes.</p>
 */
class RelatorioJdbcIT extends RepositorioJdbcIT {

    private final JdbcPacoteRepository pacotes = new JdbcPacoteRepository();
    private final JdbcClienteRepository clientes = new JdbcClienteRepository();
    private final JdbcReservaRepository reservas = new JdbcReservaRepository();
    private final JdbcParcelaRepository parcelas = new JdbcParcelaRepository();
    private final JdbcPagamentoRepository pagamentos = new JdbcPagamentoRepository();

    private RelatorioController controller;

    private String marca;
    private Pacote pacoteFuturo;
    private Pacote pacotePassado;
    private Cliente carlos;
    private Cliente mariana;
    private Reserva reservaAtiva;
    private Reserva reservaCancelada;
    private Reserva reservaConcluida;

    @BeforeEach
    void prepararMassa() {
        controller = new RelatorioController(new JdbcRelatorioRepository());
        marca = "M" + (System.nanoTime() % 1_000_000);

        LocalDate hoje = LocalDate.now();
        pacoteFuturo = salvarPacote("Gramado " + marca, hoje.plusDays(60), hoje.plusDays(67),
                new BigDecimal("1000.00"), 10);
        pacotePassado = salvarPacote("Machu " + marca, hoje.minusDays(60), hoje.minusDays(53),
                new BigDecimal("500.00"), 4);

        carlos = clientes.salvar(clienteCom("Carlos " + marca, List.of("Praia", "Ecoturismo")));
        mariana = clientes.salvar(clienteCom("Mariana " + marca, List.of()));

        // 3 viajantes no pacote futuro (ativa) e 2 no pacote passado (concluida)
        reservaAtiva = salvarReserva(carlos, pacoteFuturo, 3, new BigDecimal("3000.00"),
                hoje.plusDays(60), hoje.plusDays(67));
        reservaConcluida = salvarReserva(carlos, pacotePassado, 2, new BigDecimal("1000.00"),
                hoje.minusDays(60), hoje.minusDays(53));
        reservaCancelada = salvarReserva(mariana, pacoteFuturo, 2, new BigDecimal("2000.00"),
                hoje.plusDays(60), hoje.plusDays(67));
        reservaCancelada.cancelar(new Cancelamento(MotivoCancelamento.DESISTENCIA_CLIENTE,
                null, "Ana Silva", 2));
        reservas.atualizar(reservaCancelada);

        // plano de 2 parcelas na reserva ativa, com a primeira recebida hoje via PIX
        parcelas.salvar(new Parcela(reservaAtiva.getId(), 1, 2,
                new BigDecimal("1500.00"), hoje));
        parcelas.salvar(new Parcela(reservaAtiva.getId(), 2, 2,
                new BigDecimal("1500.00"), hoje.plusMonths(1)));
        Pagamento pagamento = new Pagamento(reservaAtiva.getId(), 1,
                new BigDecimal("1500.00"), hoje, FormaPagamento.PIX);
        pagamento.setNumeroRecibo(pagamentos.proximoNumeroRecibo());
        pagamentos.salvar(pagamento);
    }

    private Pacote salvarPacote(String destino, LocalDate inicio, LocalDate fim,
                                BigDecimal preco, int capacidade) {
        Pacote pacote = new Pacote(destino, inicio, fim, "Massa de relatorio", preco, capacidade, null);
        pacote.setCodigo(pacotes.proximoCodigo());
        return pacotes.salvar(pacote);
    }

    private Cliente clienteCom(String nome, List<String> preferencias) {
        Cliente cliente = new Cliente(nome, cpfValido(), "87999990000");
        cliente.definirPreferencias(preferencias);
        return cliente;
    }

    private Reserva salvarReserva(Cliente cliente, Pacote pacote, int viajantes,
                                  BigDecimal valorTotal, LocalDate inicio, LocalDate fim) {
        Reserva reserva = new Reserva(cliente.getId(), pacote.getId(), viajantes, inicio, fim, null);
        reserva.setCodigo(reservas.proximoCodigo());
        reserva.setValorTotal(valorTotal);
        return reservas.salvar(reserva);
    }

    private Relatorio gerar(TipoRelatorio tipo, FiltrosRelatorio filtros) {
        Resultado<Relatorio> resultado = controller.gerar(tipo, filtros);
        assertTrue(resultado.isSucesso(), "esperava relatorio com dados: " + resultado.getMensagem());
        return resultado.getDado().orElseThrow();
    }

    private static String total(Relatorio relatorio, String rotulo) {
        return relatorio.totais().stream()
                .filter(t -> t.rotulo().equals(rotulo))
                .map(TotalRelatorio::valor)
                .findFirst().orElseThrow(() -> new AssertionError("total ausente: " + rotulo));
    }

    private Optional<List<String>> linhaCom(Relatorio relatorio, String trecho) {
        return relatorio.linhas().stream().filter(linha -> linha.get(0).contains(trecho)).findFirst();
    }

    // ------------------------------------------------------------------
    // Reservas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Reservas: filtro por CPF traz as reservas do cliente com totais")
    void reservasPorCliente() {
        Relatorio relatorio = gerar(TipoRelatorio.RESERVAS, filtros()
                .cpf(carlos.getCpf()).construir());

        assertEquals(2, relatorio.quantidadeDeRegistros());
        assertEquals("2", total(relatorio, "Reservas"));
        assertEquals("5", total(relatorio, "Passageiros"), "3 + 2 viajantes");
        assertEquals(TipoRelatorio.RESERVAS.getColunas(), relatorio.colunas());
    }

    @Test
    @DisplayName("Reservas: a situacao Concluida e derivada do fim do pacote")
    void reservasConcluidaEDerivada() {
        Relatorio concluidas = gerar(TipoRelatorio.RESERVAS, filtros()
                .cpf(carlos.getCpf()).situacaoReserva(StatusReserva.CONCLUIDA).construir());

        assertEquals(1, concluidas.quantidadeDeRegistros());
        assertEquals(reservaConcluida.getCodigo(), concluidas.linhas().get(0).get(0));
        assertEquals("Concluida", concluidas.linhas().get(0).get(5));

        Relatorio ativas = gerar(TipoRelatorio.RESERVAS, filtros()
                .cpf(carlos.getCpf()).situacaoReserva(StatusReserva.ATIVA).construir());
        assertEquals(reservaAtiva.getCodigo(), ativas.linhas().get(0).get(0));
    }

    @Test
    @DisplayName("Reservas: filtro por pacote separa a cancelada da ativa")
    void reservasPorPacoteESituacao() {
        Relatorio todas = gerar(TipoRelatorio.RESERVAS, filtros()
                .pacote(pacoteFuturo.getCodigo()).construir());
        assertEquals(2, todas.quantidadeDeRegistros());

        Relatorio canceladas = gerar(TipoRelatorio.RESERVAS, filtros()
                .pacote(pacoteFuturo.getCodigo())
                .situacaoReserva(StatusReserva.CANCELADA).construir());
        assertEquals(1, canceladas.quantidadeDeRegistros());
        assertEquals(reservaCancelada.getCodigo(), canceladas.linhas().get(0).get(0));
    }

    @Test
    @DisplayName("FA03: periodo sem reservas informa que nao ha dados")
    void reservasFa03SemDados() {
        Resultado<Relatorio> resultado = controller.gerar(TipoRelatorio.RESERVAS, filtros()
                .cpf(carlos.getCpf()).periodo("01/01/2000", "31/12/2000").construir());

        assertEquals(StatusResultado.NAO_ENCONTRADO, resultado.getStatus());
    }

    // ------------------------------------------------------------------
    // Pagamentos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Pagamentos: valores vem da view do banco, com vencimentos e situacao")
    void pagamentosDoPacote() {
        Relatorio relatorio = gerar(TipoRelatorio.PAGAMENTOS, filtros()
                .pacote(pacoteFuturo.getCodigo()).construir());

        assertEquals(2, relatorio.quantidadeDeRegistros());
        List<String> ativa = linhaCom(relatorio, reservaAtiva.getCodigo()).orElseThrow();
        assertEquals(Formatadores.formatarMoeda(new BigDecimal("3000.00")), ativa.get(1));
        assertEquals(Formatadores.formatarMoeda(new BigDecimal("1500.00")), ativa.get(2));
        assertEquals(Formatadores.formatarMoeda(new BigDecimal("1500.00")), ativa.get(3));
        assertTrue(ativa.get(4).contains(Formatadores.formatarData(LocalDate.now())),
                "a coluna de vencimentos lista as datas das parcelas");
        assertEquals(SituacaoFinanceira.PARCIALMENTE_PAGA.getDescricao(), ativa.get(5));

        assertEquals(Formatadores.formatarMoeda(new BigDecimal("1500.00")),
                total(relatorio, "Total Recebido"));
    }

    @Test
    @DisplayName("Pagamentos: filtro por forma de pagamento e por periodo de recebimento")
    void pagamentosPorFormaEPeriodo() {
        String hoje = Formatadores.formatarData(LocalDate.now());

        Relatorio porPix = gerar(TipoRelatorio.PAGAMENTOS, filtros()
                .pacote(pacoteFuturo.getCodigo())
                .forma(FormaPagamento.PIX).construir());
        assertEquals(1, porPix.quantidadeDeRegistros());
        assertEquals(reservaAtiva.getCodigo(), porPix.linhas().get(0).get(0));

        Relatorio noPeriodo = gerar(TipoRelatorio.PAGAMENTOS, filtros()
                .pacote(pacoteFuturo.getCodigo()).periodo(hoje, hoje).construir());
        assertEquals(1, noPeriodo.quantidadeDeRegistros());

        // dinheiro nao foi usado em nenhum recebimento desta massa
        assertEquals(StatusResultado.NAO_ENCONTRADO, controller.gerar(TipoRelatorio.PAGAMENTOS,
                filtros().pacote(pacoteFuturo.getCodigo())
                        .forma(FormaPagamento.DINHEIRO).construir()).getStatus());
    }

    @Test
    @DisplayName("Pagamentos: filtro por situacao financeira usa a view da equipe")
    void pagamentosPorSituacaoFinanceira() {
        Relatorio parciais = gerar(TipoRelatorio.PAGAMENTOS, filtros()
                .pacote(pacoteFuturo.getCodigo())
                .situacaoFinanceira(SituacaoFinanceira.PARCIALMENTE_PAGA).construir());

        assertEquals(1, parciais.quantidadeDeRegistros());
        assertEquals(reservaAtiva.getCodigo(), parciais.linhas().get(0).get(0));
    }

    // ------------------------------------------------------------------
    // Pacotes Mais Procurados
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Pacotes Mais Procurados: conta reservas e viajantes, ignorando canceladas")
    void pacotesMaisProcurados() {
        Relatorio relatorio = gerar(TipoRelatorio.PACOTES_MAIS_PROCURADOS, filtros()
                .ranking("50", CriterioRanking.NUMERO_DE_VIAJANTES).construir());

        List<String> futuro = linhaCom(relatorio, pacoteFuturo.getCodigo()).orElseThrow();
        assertEquals("1", futuro.get(2), "a reserva cancelada nao conta como procura");
        assertEquals("3", futuro.get(3));

        List<String> passado = linhaCom(relatorio, pacotePassado.getCodigo()).orElseThrow();
        assertEquals("1", passado.get(2));
        assertEquals("2", passado.get(3));
    }

    @Test
    @DisplayName("Pacotes Mais Procurados: o ranking respeita o tamanho pedido")
    void rankingRespeitaOTamanho() {
        Relatorio relatorio = gerar(TipoRelatorio.PACOTES_MAIS_PROCURADOS, filtros()
                .ranking("1", CriterioRanking.NUMERO_DE_VIAJANTES).construir());

        assertEquals(1, relatorio.quantidadeDeRegistros());
    }

    // ------------------------------------------------------------------
    // Clientes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Clientes: viagens concluidas contadas pelo fim do pacote")
    void clientesComViagensConcluidas() {
        Relatorio relatorio = gerar(TipoRelatorio.CLIENTES, filtros()
                .nome("Carlos " + marca).construir());

        assertEquals(1, relatorio.quantidadeDeRegistros());
        List<String> linha = relatorio.linhas().get(0);
        assertEquals("Carlos " + marca, linha.get(0));
        assertTrue(linha.get(1).matches("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}"), "CPF formatado");
        assertTrue(linha.get(3).contains("Praia") && linha.get(3).contains("Ecoturismo"));
        assertEquals("1", linha.get(4), "so a reserva do pacote ja encerrado conta");
    }

    @Test
    @DisplayName("Clientes: filtros de preferencia e de minimo de viagens concluidas")
    void clientesPorPreferenciaEMinimo() {
        Relatorio porPreferencia = gerar(TipoRelatorio.CLIENTES, filtros()
                .nome(marca).preferencia("praia").construir());
        assertEquals(1, porPreferencia.quantidadeDeRegistros());
        assertEquals("Carlos " + marca, porPreferencia.linhas().get(0).get(0));

        Relatorio comViagem = gerar(TipoRelatorio.CLIENTES, filtros()
                .nome(marca).minimoViagens("1").construir());
        assertEquals(1, comViagem.quantidadeDeRegistros());

        // Mariana nao tem viagem concluida, entao o minimo 1 a exclui
        Relatorio todos = gerar(TipoRelatorio.CLIENTES, filtros().nome(marca).construir());
        assertEquals(2, todos.quantidadeDeRegistros());
    }

    // ------------------------------------------------------------------
    // Ocupacao dos Pacotes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Ocupacao: percentual e situacao vem da view SituacaoPacote")
    void ocupacaoDosPacotes() {
        Relatorio relatorio = gerar(TipoRelatorio.OCUPACAO_DOS_PACOTES, filtros()
                .destino(marca).construir());

        assertEquals(2, relatorio.quantidadeDeRegistros());

        List<String> futuro = linhaCom(relatorio, pacoteFuturo.getCodigo()).orElseThrow();
        assertEquals("10", futuro.get(1));
        assertEquals("3", futuro.get(2));
        assertEquals("7", futuro.get(3));
        assertEquals("30,0%", futuro.get(4));
        assertEquals(SituacaoPacote.DISPONIVEL.getDescricao(), futuro.get(5));

        List<String> passado = linhaCom(relatorio, pacotePassado.getCodigo()).orElseThrow();
        assertEquals("50,0%", passado.get(4));
        assertEquals(SituacaoPacote.ENCERRADO.getDescricao(), passado.get(5),
                "pacote com data de fim no passado aparece como Encerrado");

        assertEquals("35,7%", total(relatorio, "Ocupação média"), "5 ocupadas de 14 vagas");
    }

    @Test
    @DisplayName("Ocupacao: filtros de situacao e de percentual minimo")
    void ocupacaoPorSituacaoEPercentual() {
        Relatorio encerrados = gerar(TipoRelatorio.OCUPACAO_DOS_PACOTES, filtros()
                .destino(marca).situacaoPacote(SituacaoPacote.ENCERRADO).construir());
        assertEquals(1, encerrados.quantidadeDeRegistros());
        assertTrue(encerrados.linhas().get(0).get(0).contains(pacotePassado.getCodigo()));

        Relatorio acimaDe40 = gerar(TipoRelatorio.OCUPACAO_DOS_PACOTES, filtros()
                .destino(marca).percentualMinimo("40").construir());
        assertEquals(1, acimaDe40.quantidadeDeRegistros());
        assertEquals("50,0%", acimaDe40.linhas().get(0).get(4));
    }

    // ------------------------------------------------------------------
    // Construtor de filtros, para os testes ficarem legiveis
    // ------------------------------------------------------------------

    private static ConstrutorDeFiltros filtros() {
        return new ConstrutorDeFiltros();
    }

    private static final class ConstrutorDeFiltros {
        private String dataInicio;
        private String dataFim;
        private StatusReserva situacaoReserva;
        private String codigoPacote;
        private String cpfCliente;
        private SituacaoFinanceira situacaoFinanceira;
        private FormaPagamento formaPagamento;
        private String posicoesRanking;
        private CriterioRanking criterioRanking;
        private String nomeCliente;
        private String preferencia;
        private String minimoViagens;
        private String destino;
        private SituacaoPacote situacaoPacote;
        private String percentualMinimo;

        ConstrutorDeFiltros periodo(String inicio, String fim) {
            this.dataInicio = inicio;
            this.dataFim = fim;
            return this;
        }

        ConstrutorDeFiltros situacaoReserva(StatusReserva situacao) {
            this.situacaoReserva = situacao;
            return this;
        }

        ConstrutorDeFiltros pacote(String codigo) {
            this.codigoPacote = codigo;
            return this;
        }

        ConstrutorDeFiltros cpf(String cpf) {
            this.cpfCliente = cpf;
            return this;
        }

        ConstrutorDeFiltros situacaoFinanceira(SituacaoFinanceira situacao) {
            this.situacaoFinanceira = situacao;
            return this;
        }

        ConstrutorDeFiltros forma(FormaPagamento forma) {
            this.formaPagamento = forma;
            return this;
        }

        ConstrutorDeFiltros ranking(String posicoes, CriterioRanking criterio) {
            this.posicoesRanking = posicoes;
            this.criterioRanking = criterio;
            return this;
        }

        ConstrutorDeFiltros nome(String nome) {
            this.nomeCliente = nome;
            return this;
        }

        ConstrutorDeFiltros preferencia(String preferencia) {
            this.preferencia = preferencia;
            return this;
        }

        ConstrutorDeFiltros minimoViagens(String minimo) {
            this.minimoViagens = minimo;
            return this;
        }

        ConstrutorDeFiltros destino(String destino) {
            this.destino = destino;
            return this;
        }

        ConstrutorDeFiltros situacaoPacote(SituacaoPacote situacao) {
            this.situacaoPacote = situacao;
            return this;
        }

        ConstrutorDeFiltros percentualMinimo(String percentual) {
            this.percentualMinimo = percentual;
            return this;
        }

        FiltrosRelatorio construir() {
            return new FiltrosRelatorio(dataInicio, dataFim, situacaoReserva, codigoPacote,
                    cpfCliente, situacaoFinanceira, formaPagamento, posicoesRanking,
                    criterioRanking, nomeCliente, preferencia, minimoViagens, destino,
                    situacaoPacote, percentualMinimo);
        }
    }

    /** CPF valido aleatorio: a massa nao pode colidir entre execucoes. */
    private static String cpfValido() {
        int[] digitos = new int[11];
        for (int i = 0; i < 9; i++) {
            digitos[i] = (int) (Math.random() * 10);
        }
        for (int posicao = 9; posicao < 11; posicao++) {
            int soma = 0;
            for (int i = 0; i < posicao; i++) {
                soma += digitos[i] * (posicao + 1 - i);
            }
            int resto = soma % 11;
            digitos[posicao] = resto < 2 ? 0 : 11 - resto;
        }
        StringBuilder cpf = new StringBuilder();
        for (int digito : digitos) {
            cpf.append(digito);
        }
        return cpf.toString();
    }
}
