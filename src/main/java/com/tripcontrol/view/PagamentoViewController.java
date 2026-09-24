package com.tripcontrol.view;

import com.tripcontrol.app.ContextoAplicacao;
import com.tripcontrol.controller.Resultado;
import com.tripcontrol.controller.StatusResultado;
import com.tripcontrol.controller.dto.ComprovantePagamento;
import com.tripcontrol.controller.dto.DadosPagamento;
import com.tripcontrol.controller.dto.DadosParcelamento;
import com.tripcontrol.controller.dto.LinhaParcela;
import com.tripcontrol.controller.dto.ResumoReserva;
import com.tripcontrol.controller.dto.SituacaoPagamentos;
import com.tripcontrol.model.FormaPagamento;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.util.Formatadores;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Tela do UC05 - Gerenciar Pagamentos ("Gerenciamento Financeiro" no prototipo).
 *
 * <p>A tela pesquisa, mostra o painel financeiro e coleta os dados do recebimento;
 * toda a decisao (FA01 a FA05, plano de parcelas e recalculo da situacao) esta em
 * {@code PagamentoController}. O unico fluxo que vive aqui e o FA06: o botao
 * "Cancelar" limpa o formulario e recarrega o painel, sem chamar o Controller.</p>
 */
public class PagamentoViewController implements Initializable {

    @FXML private TextField txtCodigoReserva;
    @FXML private TextField txtCpfCliente;
    @FXML private TextField txtNomeCliente;
    @FXML private TextField txtCodigoPacote;
    @FXML private Button btnFiltrar;
    @FXML private Label lblMensagemPesquisa;

    @FXML private VBox painelResultados;
    @FXML private TableView<ResumoReserva> tableReservas;
    @FXML private TableColumn<ResumoReserva, String> colResultadoCodigo;
    @FXML private TableColumn<ResumoReserva, String> colResultadoCliente;
    @FXML private TableColumn<ResumoReserva, String> colResultadoPacote;
    @FXML private TableColumn<ResumoReserva, String> colResultadoTotal;
    @FXML private TableColumn<ResumoReserva, String> colResultadoSituacao;

    @FXML private Label lblCodigoReserva;
    @FXML private Label lblSituacaoFinanceira;
    @FXML private Label lblCliente;
    @FXML private Label lblPacote;
    @FXML private Label lblValorTotal;
    @FXML private Label lblTotalPago;
    @FXML private Label lblSaldoPendente;
    @FXML private Label lblAvisoReservaCancelada;

    @FXML private TableView<LinhaParcela> tableParcelas;
    @FXML private TableColumn<LinhaParcela, String> colParcela;
    @FXML private TableColumn<LinhaParcela, String> colVencimento;
    @FXML private TableColumn<LinhaParcela, String> colValor;
    @FXML private TableColumn<LinhaParcela, String> colRecebimento;
    @FXML private TableColumn<LinhaParcela, String> colForma;
    @FXML private TableColumn<LinhaParcela, String> colIdentificador;
    @FXML private TableColumn<LinhaParcela, String> colStatus;

    @FXML private VBox painelParcelamento;
    @FXML private TextField txtQuantidadeParcelas;
    @FXML private javafx.scene.control.DatePicker dpPrimeiroVencimento;
    @FXML private Button btnDefinirParcelamento;
    @FXML private Label lblErroQuantidadeParcelas;
    @FXML private Label lblErroPrimeiroVencimento;

    @FXML private VBox painelRegistro;
    @FXML private TextField txtValorRecebido;
    @FXML private javafx.scene.control.DatePicker dpDataRecebimento;
    @FXML private ComboBox<FormaPagamento> cboFormaPagamento;
    @FXML private ComboBox<LinhaParcela> cboNumeroParcela;
    @FXML private TextField txtIdentificadorTransacao;
    @FXML private TextArea txtObservacao;
    @FXML private Label lblVencimentoParcela;
    @FXML private Label lblErroValorRecebido;
    @FXML private Label lblErroDataRecebimento;
    @FXML private Label lblErroFormaPagamento;
    @FXML private Label lblErroNumeroParcela;
    @FXML private Button btnConfirmarPagamento;
    @FXML private Button btnCancelarRegistro;

    private final ContextoAplicacao contexto;

    /** Reserva atualmente aberta no painel, e a marca de concorrencia do FA05. */
    private SituacaoPagamentos situacaoAtual;

    public PagamentoViewController(ContextoAplicacao contexto) {
        this.contexto = contexto;
    }

    @Override
    public void initialize(URL local, ResourceBundle recursos) {
        configurarColunas();
        Campos.formatarCpfAoSair(txtCpfCliente);
        Campos.configurarData(dpDataRecebimento);
        Campos.configurarData(dpPrimeiroVencimento);
        Campos.somenteNumeros(txtQuantidadeParcelas);

        cboFormaPagamento.setItems(FXCollections.observableArrayList(FormaPagamento.values()));
        cboNumeroParcela.setConverter(new StringConverter<>() {
            @Override
            public String toString(LinhaParcela linha) {
                return linha == null ? "" : linha.numeroFormatado();
            }

            @Override
            public LinhaParcela fromString(String texto) {
                return null;
            }
        });
        // Passo 6: escolher a parcela preenche o valor em aberto e mostra o vencimento.
        cboNumeroParcela.valueProperty().addListener(
                (observavel, anterior, atual) -> aoTrocarParcela(atual));

        tableReservas.getSelectionModel().selectedItemProperty().addListener(
                (observavel, anterior, atual) -> abrirReserva(atual));

        limparPainel();
    }

    // ------------------------------------------------------------------
    // Pesquisa (UC05, passos 2 e 3)
    // ------------------------------------------------------------------

    @FXML
    private void aoFiltrar(ActionEvent evento) {
        esconderMensagemPesquisa();

        Resultado<List<ResumoReserva>> resultado = contexto.getPagamentoController().buscar(
                txtCodigoReserva.getText(),
                txtCpfCliente.getText(),
                txtNomeCliente.getText(),
                txtCodigoPacote.getText());

        if (!resultado.isSucesso()) {
            // ERRO_VALIDACAO: nenhum criterio informado. NAO_ENCONTRADO: FA01.
            esconderResultados();
            limparPainel();
            exibirMensagemPesquisa(resultado.mensagensConsolidadas());
            return;
        }

        List<ResumoReserva> encontradas = resultado.getDado().orElse(List.of());
        tableReservas.setItems(FXCollections.observableArrayList(encontradas));

        // O prototipo nao tem lista de resultados; ela so aparece quando o passo 3
        // realmente exige escolher entre varias reservas.
        boolean precisaEscolher = encontradas.size() > 1;
        painelResultados.setVisible(precisaEscolher);
        painelResultados.setManaged(precisaEscolher);
        tableReservas.getSelectionModel().selectFirst();
    }

    /** Recarrega o painel da reserva aberta, preservando a selecao (passos 4 e 8). */
    private void recarregarPainel() {
        if (situacaoAtual == null) {
            return;
        }
        abrirReserva(situacaoAtual.reserva());
    }

    // ------------------------------------------------------------------
    // Painel financeiro (UC05, passo 4)
    // ------------------------------------------------------------------

    private void abrirReserva(ResumoReserva resumo) {
        limparErros();
        if (resumo == null) {
            limparPainel();
            return;
        }

        Resultado<SituacaoPagamentos> resultado =
                contexto.getPagamentoController().abrir(resumo.reserva().getId());
        if (!resultado.isSucesso()) {
            // FA01 - a reserva desapareceu entre a pesquisa e a selecao.
            limparPainel();
            exibirMensagemPesquisa(resultado.mensagensConsolidadas());
            return;
        }

        situacaoAtual = resultado.getDado().orElseThrow();
        exibirSituacao(situacaoAtual);
    }

    private void exibirSituacao(SituacaoPagamentos situacao) {
        Reserva reserva = situacao.reserva().reserva();

        lblCodigoReserva.setText("RESERVA #" + situacao.reserva().codigo());
        aplicarChipDeSituacao(situacao.financeiro().situacao());
        lblCliente.setText("Cliente: " + situacao.reserva().nomeCliente());
        lblPacote.setText(situacao.reserva().pacote() == null
                ? "-" : situacao.reserva().destino() + " (" + situacao.reserva().pacote().getCodigo() + ")");
        lblValorTotal.setText(Formatadores.formatarMoeda(situacao.financeiro().valorTotal()));
        lblTotalPago.setText(Formatadores.formatarMoeda(situacao.financeiro().totalPago()));
        lblSaldoPendente.setText(Formatadores.formatarMoeda(situacao.financeiro().saldoPendente()));

        tableParcelas.setItems(FXCollections.observableArrayList(situacao.parcelas()));
        cboNumeroParcela.setItems(FXCollections.observableArrayList(situacao.parcelasEmAberto()));
        cboNumeroParcela.getSelectionModel().clearSelection();
        lblVencimentoParcela.setText("");

        // FA02 - reserva cancelada: historico visivel, registro bloqueado.
        boolean bloqueada = situacao.isBloqueadaParaPagamento();
        painelRegistro.setDisable(bloqueada);
        lblAvisoReservaCancelada.setText(bloqueada
                ? "Reserva cancelada: o histórico financeiro fica disponível apenas para consulta, "
                        + "e novos recebimentos estão bloqueados."
                : "");
        lblAvisoReservaCancelada.setVisible(bloqueada);
        lblAvisoReservaCancelada.setManaged(bloqueada);

        // Decisao A: a acao de parcelamento so existe enquanto nao ha plano.
        boolean semPlano = situacao.isSemPlanoDeParcelas() && !bloqueada;
        painelParcelamento.setVisible(semPlano);
        painelParcelamento.setManaged(semPlano);
        painelRegistro.setDisable(bloqueada || situacao.isSemPlanoDeParcelas());

        limparFormularioDeRecebimento();
        if (reserva.isAtiva() && !situacao.isSemPlanoDeParcelas()) {
            dpDataRecebimento.setValue(contexto.getCalculadoraFinanceira().hoje());
        }
    }

    // ------------------------------------------------------------------
    // Definir parcelamento (decisao A da equipe)
    // ------------------------------------------------------------------

    @FXML
    private void aoDefinirParcelamento(ActionEvent evento) {
        Alertas.limparErro(txtQuantidadeParcelas, lblErroQuantidadeParcelas);
        Alertas.limparErro(dpPrimeiroVencimento, lblErroPrimeiroVencimento);
        if (situacaoAtual == null) {
            Alertas.aviso("Pesquise e selecione uma reserva antes de definir o parcelamento.");
            return;
        }

        Resultado<SituacaoPagamentos> resultado = contexto.getPagamentoController().definirParcelamento(
                situacaoAtual.reserva().reserva().getId(),
                new DadosParcelamento(txtQuantidadeParcelas.getText(),
                        Campos.textoDaData(dpPrimeiroVencimento)));

        if (resultado.isSucesso()) {
            Alertas.sucesso(resultado.getMensagem());
            txtQuantidadeParcelas.clear();
            dpPrimeiroVencimento.setValue(null);
            recarregarPainel();
            return;
        }

        if (resultado.getStatus() == StatusResultado.ERRO_VALIDACAO) {
            Alertas.marcarErro(txtQuantidadeParcelas, lblErroQuantidadeParcelas,
                    resultado, "quantidadeParcelas");
            Alertas.marcarErro(dpPrimeiroVencimento, lblErroPrimeiroVencimento,
                    resultado, "dataPrimeiroVencimento");
            return;
        }

        Alertas.aviso(resultado.mensagensConsolidadas());
        recarregarPainel();
    }

    // ------------------------------------------------------------------
    // Registrar recebimento (UC05, passos 5 a 10)
    // ------------------------------------------------------------------

    @FXML
    private void aoConfirmarPagamento(ActionEvent evento) {
        limparErros();
        if (situacaoAtual == null) {
            Alertas.aviso("Pesquise e selecione a reserva antes de registrar o recebimento.");
            return;
        }

        LinhaParcela parcelaEscolhida = cboNumeroParcela.getValue();
        DadosPagamento dados = new DadosPagamento(
                txtValorRecebido.getText(),
                Campos.textoDaData(dpDataRecebimento),
                cboFormaPagamento.getValue(),
                parcelaEscolhida == null ? "" : String.valueOf(parcelaEscolhida.parcela().getNumero()),
                txtIdentificadorTransacao.getText(),
                txtObservacao.getText());

        Resultado<ComprovantePagamento> resultado =
                contexto.getPagamentoController().registrarRecebimento(
                        situacaoAtual.reserva().reserva().getId(),
                        dados,
                        situacaoAtual.versaoFinanceira());

        if (resultado.isSucesso()) {
            ComprovantePagamento comprovante = resultado.getDado().orElseThrow();
            // Passo 10: recibo, valor registrado e saldo atualizado.
            Alertas.sucesso("Pagamento registrado.\n\nRecibo: " + comprovante.numeroRecibo()
                    + "\nValor registrado: " + Formatadores.formatarMoeda(comprovante.valorRegistrado())
                    + "\nSaldo pendente: " + Formatadores.formatarMoeda(comprovante.saldoPendente())
                    + "\nSituação: " + comprovante.situacao().getDescricao());
            recarregarPainel();
            return;
        }

        if (resultado.getStatus() == StatusResultado.ERRO_VALIDACAO) {
            // FA03 - cada campo destacado com a sua mensagem.
            Alertas.marcarErro(txtValorRecebido, lblErroValorRecebido, resultado, "valorRecebido");
            Alertas.marcarErro(dpDataRecebimento, lblErroDataRecebimento, resultado, "dataRecebimento");
            Alertas.marcarErro(cboFormaPagamento, lblErroFormaPagamento, resultado, "formaPagamento");
            Alertas.marcarErro(cboNumeroParcela, lblErroNumeroParcela, resultado, "numeroParcela");
            return;
        }

        // FA01, FA02, FA04 (duplicidade) e FA05 (saldo alterado): a tela avisa e
        // recarrega os valores atuais, voltando ao passo 4.
        Alertas.aviso(resultado.mensagensConsolidadas());
        recarregarPainel();
    }

    /** FA06 - descarta o preenchimento sem alterar o historico financeiro. */
    @FXML
    private void aoCancelarRegistro(ActionEvent evento) {
        limparErros();
        limparFormularioDeRecebimento();
        recarregarPainel();
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private void aoTrocarParcela(LinhaParcela linha) {
        if (linha == null) {
            lblVencimentoParcela.setText("");
            return;
        }
        // O valor em aberto e a data de vencimento vem da parcela: o funcionario nao
        // digita vencimento (decisao A da equipe).
        txtValorRecebido.setText(Formatadores.formatarMoeda(linha.valorEmAberto()));
        lblVencimentoParcela.setText("Vencimento da parcela " + linha.numeroFormatado()
                + ": " + linha.vencimentoFormatado());
    }

    /** Badge de situacao financeira: verde quando quitada, vermelho quando atrasada. */
    private void aplicarChipDeSituacao(com.tripcontrol.model.SituacaoFinanceira situacao) {
        lblSituacaoFinanceira.setText(situacao.getDescricao());
        lblSituacaoFinanceira.getStyleClass().removeAll("chip-quitada", "chip-atrasada");
        if (situacao == com.tripcontrol.model.SituacaoFinanceira.QUITADA) {
            lblSituacaoFinanceira.getStyleClass().add("chip-quitada");
        } else if (situacao == com.tripcontrol.model.SituacaoFinanceira.ATRASADA) {
            lblSituacaoFinanceira.getStyleClass().add("chip-atrasada");
        }
    }

    private void configurarColunas() {
        colResultadoCodigo.setCellValueFactory(celula -> texto(celula.getValue().codigo()));
        colResultadoCliente.setCellValueFactory(celula -> texto(celula.getValue().nomeCliente()));
        colResultadoPacote.setCellValueFactory(celula -> texto(celula.getValue().pacote() == null
                ? "-" : celula.getValue().pacote().getCodigo() + " - " + celula.getValue().destino()));
        colResultadoTotal.setCellValueFactory(celula ->
                texto(Formatadores.formatarMoeda(celula.getValue().valorTotal())));
        colResultadoSituacao.setCellValueFactory(celula ->
                texto(celula.getValue().reserva().getStatus().getDescricao()));

        colParcela.setCellValueFactory(celula -> texto(celula.getValue().numeroFormatado()));
        colVencimento.setCellValueFactory(celula -> texto(celula.getValue().vencimentoFormatado()));
        colValor.setCellValueFactory(celula -> texto(celula.getValue().valorFormatado()));
        colRecebimento.setCellValueFactory(celula -> texto(celula.getValue().recebimentoFormatado()));
        colForma.setCellValueFactory(celula -> texto(celula.getValue().formaFormatada()));
        colIdentificador.setCellValueFactory(celula -> texto(celula.getValue().identificadorFormatado()));
        colStatus.setCellValueFactory(celula -> texto(celula.getValue().status().getDescricao()));
    }

    private static ObservableValue<String> texto(String valor) {
        return new SimpleStringProperty(valor == null ? "" : valor);
    }

    private void limparPainel() {
        situacaoAtual = null;
        lblCodigoReserva.setText("RESERVA");
        lblSituacaoFinanceira.setText("-");
        lblSituacaoFinanceira.getStyleClass().removeAll("chip-quitada", "chip-atrasada");
        lblCliente.setText("Cliente: -");
        lblPacote.setText("-");
        lblValorTotal.setText("-");
        lblTotalPago.setText("-");
        lblSaldoPendente.setText("-");
        tableParcelas.getItems().clear();
        cboNumeroParcela.getItems().clear();
        lblVencimentoParcela.setText("");
        lblAvisoReservaCancelada.setVisible(false);
        lblAvisoReservaCancelada.setManaged(false);
        painelParcelamento.setVisible(false);
        painelParcelamento.setManaged(false);
        painelRegistro.setDisable(true);
        limparFormularioDeRecebimento();
    }

    private void limparFormularioDeRecebimento() {
        txtValorRecebido.clear();
        dpDataRecebimento.setValue(null);
        cboFormaPagamento.getSelectionModel().clearSelection();
        cboNumeroParcela.getSelectionModel().clearSelection();
        txtIdentificadorTransacao.clear();
        txtObservacao.clear();
        lblVencimentoParcela.setText("");
    }

    private void esconderResultados() {
        tableReservas.getItems().clear();
        painelResultados.setVisible(false);
        painelResultados.setManaged(false);
    }

    private void exibirMensagemPesquisa(String mensagem) {
        lblMensagemPesquisa.setText(mensagem);
        lblMensagemPesquisa.setVisible(true);
        lblMensagemPesquisa.setManaged(true);
    }

    private void esconderMensagemPesquisa() {
        lblMensagemPesquisa.setText("");
        lblMensagemPesquisa.setVisible(false);
        lblMensagemPesquisa.setManaged(false);
    }

    private void limparErros() {
        Alertas.limparErro(txtValorRecebido, lblErroValorRecebido);
        Alertas.limparErro(dpDataRecebimento, lblErroDataRecebimento);
        Alertas.limparErro(cboFormaPagamento, lblErroFormaPagamento);
        Alertas.limparErro(cboNumeroParcela, lblErroNumeroParcela);
    }
}
