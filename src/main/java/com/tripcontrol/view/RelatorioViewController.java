package com.tripcontrol.view;

import com.tripcontrol.app.ContextoAplicacao;
import com.tripcontrol.controller.Resultado;
import com.tripcontrol.controller.StatusResultado;
import com.tripcontrol.controller.dto.FiltrosRelatorio;
import com.tripcontrol.controller.dto.Relatorio;
import com.tripcontrol.controller.dto.TotalRelatorio;
import com.tripcontrol.model.CriterioRanking;
import com.tripcontrol.model.FormaPagamento;
import com.tripcontrol.model.SituacaoFinanceira;
import com.tripcontrol.model.SituacaoPacote;
import com.tripcontrol.model.StatusReserva;
import com.tripcontrol.model.TipoRelatorio;
import com.tripcontrol.util.Formatadores;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Tela do UC08 - Gerar Relatorios ("Relatorios Operacionais e Financeiros").
 *
 * <p>A tela escolhe o tipo, mostra os filtros daquele tipo, desenha a previa e
 * dispara impressao ou exportacao. Toda a decisao — critica dos filtros, consulta,
 * ausencia de dados, falha de banco e falha de gravacao — esta em
 * {@code RelatorioController}. Ficam aqui apenas o FA06 (o botao Cancelar limpa os
 * filtros sem consultar nada) e a troca dos campos conforme o tipo.</p>
 *
 * <p>A tabela e montada em tempo de execucao a partir de
 * {@code Relatorio.colunas()}: sao cinco relatorios com formatos diferentes, e um
 * {@code TableView} de linhas de texto atende aos cinco sem cinco telas.</p>
 */
public class RelatorioViewController implements Initializable {

    @FXML private Label lblRelatoriosIndisponiveis;
    @FXML private GridPane painelSelecao;

    @FXML private RadioButton rbReservas;
    @FXML private RadioButton rbPagamentos;
    @FXML private RadioButton rbPacotesMaisProcurados;
    @FXML private RadioButton rbClientes;
    @FXML private RadioButton rbOcupacao;
    @FXML private Label lblErroTipo;

    @FXML private Label lblTituloFiltros;
    @FXML private FlowPane painelFiltros;

    @FXML private VBox grupoDataInicio;
    @FXML private VBox grupoDataFim;
    @FXML private VBox grupoSituacaoReserva;
    @FXML private VBox grupoCodigoPacote;
    @FXML private VBox grupoCpfCliente;
    @FXML private VBox grupoSituacaoFinanceira;
    @FXML private VBox grupoFormaPagamento;
    @FXML private VBox grupoPosicoesRanking;
    @FXML private VBox grupoCriterioRanking;
    @FXML private VBox grupoNomeCliente;
    @FXML private VBox grupoPreferencia;
    @FXML private VBox grupoMinimoViagens;
    @FXML private VBox grupoDestino;
    @FXML private VBox grupoSituacaoPacote;
    @FXML private VBox grupoPercentualMinimo;

    @FXML private Label lblRotuloDataInicio;
    @FXML private Label lblRotuloDataFim;
    @FXML private DatePicker dpDataInicio;
    @FXML private DatePicker dpDataFim;
    @FXML private ComboBox<StatusReserva> cboSituacaoReserva;
    @FXML private TextField txtCodigoPacote;
    @FXML private TextField txtCpfCliente;
    @FXML private ComboBox<SituacaoFinanceira> cboSituacaoFinanceira;
    @FXML private ComboBox<FormaPagamento> cboFormaPagamento;
    @FXML private TextField txtPosicoesRanking;
    @FXML private ComboBox<CriterioRanking> cboCriterioRanking;
    @FXML private TextField txtNomeCliente;
    @FXML private TextField txtPreferencia;
    @FXML private TextField txtMinimoViagens;
    @FXML private TextField txtDestino;
    @FXML private ComboBox<SituacaoPacote> cboSituacaoPacote;
    @FXML private TextField txtPercentualMinimo;

    @FXML private Label lblErroDataInicio;
    @FXML private Label lblErroDataFim;
    @FXML private Label lblErroPosicoesRanking;
    @FXML private Label lblErroCriterioRanking;
    @FXML private Label lblErroMinimoViagens;
    @FXML private Label lblErroPercentualMinimo;
    @FXML private Label lblMensagemGeracao;

    @FXML private Button btnGerarRelatorio;
    @FXML private Button btnCancelar;

    @FXML private VBox painelPrevia;
    @FXML private HBox painelTotais;
    @FXML private Label lblResumoGeracao;
    @FXML private Label lblFiltrosAplicados;
    @FXML private TableView<List<String>> tableRelatorio;
    @FXML private Button btnImprimir;
    @FXML private MenuButton btnExportar;

    private final ContextoAplicacao contexto;
    private final ToggleGroup grupoDeTipos = new ToggleGroup();
    private final Map<TipoRelatorio, List<VBox>> filtrosPorTipo = new LinkedHashMap<>();

    /** Relatorio atualmente na previa, base da impressao e da exportacao. */
    private Relatorio relatorioGerado;

    public RelatorioViewController(ContextoAplicacao contexto) {
        this.contexto = contexto;
    }

    @Override
    public void initialize(URL local, ResourceBundle recursos) {
        rbReservas.setUserData(TipoRelatorio.RESERVAS);
        rbPagamentos.setUserData(TipoRelatorio.PAGAMENTOS);
        rbPacotesMaisProcurados.setUserData(TipoRelatorio.PACOTES_MAIS_PROCURADOS);
        rbClientes.setUserData(TipoRelatorio.CLIENTES);
        rbOcupacao.setUserData(TipoRelatorio.OCUPACAO_DOS_PACOTES);
        List.of(rbReservas, rbPagamentos, rbPacotesMaisProcurados, rbClientes, rbOcupacao)
                .forEach(botao -> botao.setToggleGroup(grupoDeTipos));

        cboSituacaoReserva.setItems(FXCollections.observableArrayList(StatusReserva.values()));
        cboSituacaoFinanceira.setItems(FXCollections.observableArrayList(SituacaoFinanceira.values()));
        cboFormaPagamento.setItems(FXCollections.observableArrayList(FormaPagamento.values()));
        cboCriterioRanking.setItems(FXCollections.observableArrayList(CriterioRanking.values()));
        cboSituacaoPacote.setItems(FXCollections.observableArrayList(SituacaoPacote.values()));

        Campos.configurarData(dpDataInicio);
        Campos.configurarData(dpDataFim);
        Campos.formatarCpfAoSair(txtCpfCliente);
        Campos.somenteNumeros(txtPosicoesRanking);
        Campos.somenteNumeros(txtMinimoViagens);

        montarMapaDeFiltros();
        grupoDeTipos.selectedToggleProperty().addListener(
                (observavel, anterior, atual) -> aoTrocarTipo(tipoDe(atual)));

        // Modo memoria: a pre-condicao do UC08 nao vale, e a tela explica isso em vez
        // de oferecer botoes que nao funcionariam.
        if (!contexto.getRelatorioController().isDisponivel()) {
            lblRelatoriosIndisponiveis.setText(
                    contexto.getRelatorioController().motivoDeIndisponibilidade());
            exibir(lblRelatoriosIndisponiveis, true);
            painelSelecao.setDisable(true);
        }

        aoTrocarTipo(null);
        esconderPrevia();
    }

    /** Quais filtros cada relatorio mostra, conforme o passo 4 do UC08. */
    private void montarMapaDeFiltros() {
        filtrosPorTipo.put(TipoRelatorio.RESERVAS, List.of(grupoDataInicio, grupoDataFim,
                grupoSituacaoReserva, grupoCodigoPacote, grupoCpfCliente));
        filtrosPorTipo.put(TipoRelatorio.PAGAMENTOS, List.of(grupoDataInicio, grupoDataFim,
                grupoSituacaoFinanceira, grupoFormaPagamento, grupoCodigoPacote));
        filtrosPorTipo.put(TipoRelatorio.PACOTES_MAIS_PROCURADOS, List.of(grupoDataInicio,
                grupoDataFim, grupoPosicoesRanking, grupoCriterioRanking));
        filtrosPorTipo.put(TipoRelatorio.CLIENTES, List.of(grupoNomeCliente, grupoCpfCliente,
                grupoPreferencia, grupoMinimoViagens));
        filtrosPorTipo.put(TipoRelatorio.OCUPACAO_DOS_PACOTES, List.of(grupoDataInicio,
                grupoDataFim, grupoDestino, grupoSituacaoPacote, grupoPercentualMinimo));
    }

    // ------------------------------------------------------------------
    // Geracao (UC08, passos 5 a 8)
    // ------------------------------------------------------------------

    @FXML
    private void aoGerarRelatorio(ActionEvent evento) {
        limparErros();
        TipoRelatorio tipo = tipoSelecionado();

        Resultado<Relatorio> resultado =
                contexto.getRelatorioController().gerar(tipo, coletarFiltros());

        if (resultado.isSucesso()) {
            relatorioGerado = resultado.getDado().orElseThrow();
            exibirPrevia(relatorioGerado);
            return;
        }

        esconderPrevia();
        if (resultado.getStatus() == StatusResultado.ERRO_VALIDACAO) {
            // FA01 (tipo nao escolhido) e FA02 (filtros invalidos): cada campo destacado.
            Alertas.marcarErro(null, lblErroTipo, resultado, "tipo");
            Alertas.marcarErro(dpDataInicio, lblErroDataInicio, resultado, "dataInicio");
            Alertas.marcarErro(dpDataFim, lblErroDataFim, resultado, "dataFim");
            Alertas.marcarErro(txtPosicoesRanking, lblErroPosicoesRanking,
                    resultado, "posicoesRanking");
            Alertas.marcarErro(cboCriterioRanking, lblErroCriterioRanking,
                    resultado, "criterioRanking");
            Alertas.marcarErro(txtMinimoViagens, lblErroMinimoViagens,
                    resultado, "minimoViagensConcluidas");
            Alertas.marcarErro(txtPercentualMinimo, lblErroPercentualMinimo,
                    resultado, "percentualMinimoOcupacao");
            return;
        }

        // FA03 (nenhum dado) e FA04 (falha na consulta): os filtros ficam como estao.
        exibirMensagem(resultado.mensagensConsolidadas());
    }

    /** FA06 - limpa os filtros sem consultar o banco nem criar arquivo. */
    @FXML
    private void aoCancelar(ActionEvent evento) {
        limparErros();
        esconderMensagem();
        esconderPrevia();
        grupoDeTipos.selectToggle(null);
        limparFiltros();
    }

    private void exibirPrevia(Relatorio relatorio) {
        esconderMensagem();
        montarColunas(relatorio);
        tableRelatorio.setItems(FXCollections.observableArrayList(relatorio.linhas()));

        lblResumoGeracao.setText("Gerado em: "
                + Formatadores.formatarDataHora(relatorio.geradoEm())
                + " | Registros Totais: " + relatorio.quantidadeDeRegistros());
        lblFiltrosAplicados.setText("Filtros: " + relatorio.filtrosAplicados());

        painelTotais.getChildren().clear();
        for (TotalRelatorio total : relatorio.totais()) {
            Label chip = new Label(total.toString());
            chip.getStyleClass().add("chip-situacao");
            painelTotais.getChildren().add(chip);
        }

        exibir(painelPrevia, true);
    }

    /** As colunas mudam a cada relatorio, entao sao criadas na hora. */
    private void montarColunas(Relatorio relatorio) {
        tableRelatorio.getColumns().clear();
        List<String> colunas = relatorio.colunas();
        for (int i = 0; i < colunas.size(); i++) {
            int indice = i;
            TableColumn<List<String>, String> coluna = new TableColumn<>(colunas.get(i));
            coluna.setCellValueFactory(celula -> new SimpleStringProperty(
                    indice < celula.getValue().size() ? celula.getValue().get(indice) : ""));
            coluna.setMinWidth(90.0);
            tableRelatorio.getColumns().add(coluna);
        }
    }

    // ------------------------------------------------------------------
    // Impressao e exportacao (UC08, passos 9 e 10)
    // ------------------------------------------------------------------

    @FXML
    private void aoExportarPdf(ActionEvent evento) {
        exportar("pdf", "Documento PDF");
    }

    @FXML
    private void aoExportarCsv(ActionEvent evento) {
        exportar("csv", "Planilha CSV (Excel)");
    }

    /** O funcionario escolhe a pasta e o nome do arquivo, como pede o passo 9. */
    private void exportar(String extensao, String descricaoDoFormato) {
        if (relatorioGerado == null) {
            Alertas.aviso("Gere o relatório antes de exportar.");
            return;
        }

        FileChooser seletor = new FileChooser();
        seletor.setTitle("Exportar relatório como " + extensao.toUpperCase(Formatadores.BRASIL));
        seletor.setInitialFileName(relatorioGerado.nomeSugerido() + "." + extensao);
        seletor.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(descricaoDoFormato, "*." + extensao));

        java.io.File escolhido = seletor.showSaveDialog(janela());
        if (escolhido == null) {
            return;
        }

        Path destino = escolhido.toPath();
        Resultado<Path> resultado = "pdf".equals(extensao)
                ? contexto.getRelatorioController().exportarPdf(relatorioGerado, destino)
                : contexto.getRelatorioController().exportarCsv(relatorioGerado, destino);

        if (resultado.isSucesso()) {
            Alertas.sucesso(resultado.getMensagem());
        } else {
            // FA05 - a previa e os filtros continuam na tela.
            Alertas.erro(resultado.mensagensConsolidadas());
        }
    }

    @FXML
    private void aoImprimir(ActionEvent evento) {
        if (relatorioGerado == null) {
            Alertas.aviso("Gere o relatório antes de imprimir.");
            return;
        }

        PrinterJob trabalho = PrinterJob.createPrinterJob();
        // FA05 - nenhuma impressora disponivel no sistema.
        if (trabalho == null) {
            Alertas.erro("Nenhuma impressora disponível no sistema. "
                    + "A prévia do relatório foi mantida: você pode exportar em PDF.");
            return;
        }
        if (!trabalho.showPrintDialog(janela())) {
            return;
        }
        if (trabalho.printPage(painelPrevia) && trabalho.endJob()) {
            Alertas.sucesso("Relatório de " + relatorioGerado.tipo().getDescricao()
                    + " enviado para a impressora.");
        } else {
            Alertas.erro("A impressão não pôde ser concluída. "
                    + "A prévia do relatório foi mantida.");
        }
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private FiltrosRelatorio coletarFiltros() {
        return new FiltrosRelatorio(
                Campos.textoDaData(dpDataInicio),
                Campos.textoDaData(dpDataFim),
                cboSituacaoReserva.getValue(),
                txtCodigoPacote.getText(),
                txtCpfCliente.getText(),
                cboSituacaoFinanceira.getValue(),
                cboFormaPagamento.getValue(),
                txtPosicoesRanking.getText(),
                cboCriterioRanking.getValue(),
                txtNomeCliente.getText(),
                txtPreferencia.getText(),
                txtMinimoViagens.getText(),
                txtDestino.getText(),
                cboSituacaoPacote.getValue(),
                txtPercentualMinimo.getText());
    }

    /** Mostra apenas os filtros do tipo escolhido e ajusta o rotulo do periodo. */
    private void aoTrocarTipo(TipoRelatorio tipo) {
        limparErros();
        esconderMensagem();
        esconderPrevia();

        filtrosPorTipo.values().forEach(grupos -> grupos.forEach(grupo -> exibir(grupo, false)));
        if (tipo == null) {
            lblTituloFiltros.setText("Filtros Dinâmicos");
            exibir(painelFiltros, false);
            return;
        }

        exibir(painelFiltros, true);
        lblTituloFiltros.setText("Filtros Dinâmicos (Relatório de " + tipo.getDescricao() + ")");
        filtrosPorTipo.getOrDefault(tipo, List.of()).forEach(grupo -> exibir(grupo, true));

        lblRotuloDataInicio.setText(rotuloDoPeriodo(tipo) + " (Início)");
        lblRotuloDataFim.setText(rotuloDoPeriodo(tipo) + " (Fim)");
    }

    private static String rotuloDoPeriodo(TipoRelatorio tipo) {
        return switch (tipo) {
            case RESERVAS -> "Período da Viagem";
            case PAGAMENTOS -> "Período de Recebimento";
            case PACOTES_MAIS_PROCURADOS -> "Período das Reservas";
            case OCUPACAO_DOS_PACOTES -> "Início dos Pacotes";
            case CLIENTES -> "Período";
        };
    }

    private TipoRelatorio tipoSelecionado() {
        return tipoDe(grupoDeTipos.getSelectedToggle());
    }

    private static TipoRelatorio tipoDe(Toggle escolhido) {
        return escolhido == null ? null : (TipoRelatorio) escolhido.getUserData();
    }

    private javafx.stage.Window janela() {
        return painelPrevia.getScene() == null ? null : painelPrevia.getScene().getWindow();
    }

    private void limparFiltros() {
        dpDataInicio.setValue(null);
        dpDataFim.setValue(null);
        cboSituacaoReserva.getSelectionModel().clearSelection();
        cboSituacaoFinanceira.getSelectionModel().clearSelection();
        cboFormaPagamento.getSelectionModel().clearSelection();
        cboCriterioRanking.getSelectionModel().clearSelection();
        cboSituacaoPacote.getSelectionModel().clearSelection();
        txtCodigoPacote.clear();
        txtCpfCliente.clear();
        txtPosicoesRanking.clear();
        txtNomeCliente.clear();
        txtPreferencia.clear();
        txtMinimoViagens.clear();
        txtDestino.clear();
        txtPercentualMinimo.clear();
    }

    private void esconderPrevia() {
        relatorioGerado = null;
        tableRelatorio.getItems().clear();
        tableRelatorio.getColumns().clear();
        painelTotais.getChildren().clear();
        exibir(painelPrevia, false);
    }

    private void exibirMensagem(String mensagem) {
        lblMensagemGeracao.setText(mensagem);
        exibir(lblMensagemGeracao, true);
    }

    private void esconderMensagem() {
        lblMensagemGeracao.setText("");
        exibir(lblMensagemGeracao, false);
    }

    private static void exibir(Node componente, boolean visivel) {
        componente.setVisible(visivel);
        componente.setManaged(visivel);
    }

    private void limparErros() {
        Alertas.limparErro(null, lblErroTipo);
        Alertas.limparErro(dpDataInicio, lblErroDataInicio);
        Alertas.limparErro(dpDataFim, lblErroDataFim);
        Alertas.limparErro(txtPosicoesRanking, lblErroPosicoesRanking);
        Alertas.limparErro(cboCriterioRanking, lblErroCriterioRanking);
        Alertas.limparErro(txtMinimoViagens, lblErroMinimoViagens);
        Alertas.limparErro(txtPercentualMinimo, lblErroPercentualMinimo);
    }
}
