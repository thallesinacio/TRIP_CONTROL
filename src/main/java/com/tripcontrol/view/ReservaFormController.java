package com.tripcontrol.view;

import com.tripcontrol.app.ContextoAplicacao;
import com.tripcontrol.controller.Resultado;
import com.tripcontrol.controller.StatusResultado;
import com.tripcontrol.controller.dto.DadosReserva;
import com.tripcontrol.controller.dto.PacoteComVagas;
import com.tripcontrol.controller.dto.ResumoReserva;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.util.Formatadores;
import com.tripcontrol.util.ValidadorCpf;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.ResourceBundle;

/** Tela do UC03 - Registrar Reservas, incluindo os fluxos FA01 a FA04. */
public class ReservaFormController implements Initializable {

    @FXML private ComboBox<Cliente> cboCliente;
    @FXML private ComboBox<Pacote> cboPacote;
    @FXML private TextField txtQuantidadeViajantes;
    @FXML private DatePicker dpDataInicio;
    @FXML private DatePicker dpDataFim;
    @FXML private TextArea txtObservacoes;
    @FXML private Button btnNovoCliente;
    @FXML private Button btnConfirmarReserva;
    @FXML private Button btnCancelarReserva;

    @FXML private Label lblErroCliente;
    @FXML private Label lblErroPacote;
    @FXML private Label lblErroQuantidade;
    @FXML private Label lblErroDataInicio;
    @FXML private Label lblErroDataFim;

    @FXML private Label lblResumoDestino;
    @FXML private Label lblResumoPreco;
    @FXML private Label lblResumoVagas;
    @FXML private Label lblResumoRoteiro;
    @FXML private Label lblResumoTotal;

    private final ContextoAplicacao contexto;
    private final Navegador navegador;

    public ReservaFormController(ContextoAplicacao contexto, Navegador navegador) {
        this.contexto = contexto;
        this.navegador = navegador;
    }

    @Override
    public void initialize(URL local, ResourceBundle recursos) {
        Campos.configurarData(dpDataInicio);
        Campos.configurarData(dpDataFim);
        Campos.somenteNumeros(txtQuantidadeViajantes);
        configurarCombos();
        recarregarListas();
        limparErros();
        limparResumo();

        cboPacote.valueProperty().addListener((observavel, anterior, atual) -> aoSelecionarPacote(atual));
        txtQuantidadeViajantes.textProperty().addListener((observavel, anterior, atual) -> atualizarTotal());
    }

    /** Usado pelo retorno do cadastro rapido de cliente (FA03). */
    public void preSelecionarCliente(Long clienteId) {
        recarregarListas();
        contexto.getClienteController().buscarPorId(clienteId)
                .ifPresent(cliente -> cboCliente.getSelectionModel().select(cliente));
    }

    @FXML
    private void aoConfirmarReserva(ActionEvent evento) {
        limparErros();
        DadosReserva dados = new DadosReserva(
                cboCliente.getValue() == null ? null : cboCliente.getValue().getId(),
                cboPacote.getValue() == null ? null : cboPacote.getValue().getId(),
                txtQuantidadeViajantes.getText(),
                Campos.textoDaData(dpDataInicio),
                Campos.textoDaData(dpDataFim),
                txtObservacoes.getText());

        Resultado<ResumoReserva> resultado = contexto.getReservaController().registrar(dados);

        if (resultado.isSucesso()) {
            Alertas.sucesso(resultado.getMensagem());
            limparFormulario();
            return;
        }

        if (resultado.getStatus() == StatusResultado.ERRO_VALIDACAO) {
            Alertas.marcarErro(cboCliente, lblErroCliente, resultado, "cliente");
            Alertas.marcarErro(cboPacote, lblErroPacote, resultado, "pacote");
            Alertas.marcarErro(txtQuantidadeViajantes, lblErroQuantidade, resultado, "quantidadeViajantes");
            Alertas.marcarErro(dpDataInicio, lblErroDataInicio, resultado, "dataInicio");
            Alertas.marcarErro(dpDataFim, lblErroDataFim, resultado, "dataFim");
            aoSelecionarPacote(cboPacote.getValue());
            return;
        }

        Alertas.erro(resultado.mensagensConsolidadas());
        recarregarListas();
    }

    /** FA03 - cliente ainda nao cadastrado: atalho para o UC02 com retorno a reserva. */
    @FXML
    private void aoCadastrarNovoCliente(ActionEvent evento) {
        navegador.<ClienteFormController>abrir(Tela.CLIENTES, controllerCliente ->
                controllerCliente.configurarRetorno(clienteSalvo ->
                        navegador.<ReservaFormController>abrir(Tela.RESERVAS, controllerReserva ->
                                controllerReserva.preSelecionarCliente(clienteSalvo.getId()))));
    }

    /** FA04 - encerra o formulario sem salvar e sem alterar o saldo de vagas. */
    @FXML
    private void aoCancelar(ActionEvent evento) {
        if (Alertas.confirmar("Cancelar reserva",
                "O formulario sera limpo e nenhuma vaga sera reservada. Deseja continuar?")) {
            limparFormulario();
        }
    }

    private void aoSelecionarPacote(Pacote pacote) {
        if (pacote == null) {
            limparResumo();
            return;
        }
        PacoteComVagas linha = contexto.getPacoteController().comVagas(pacote);
        lblResumoDestino.setText(pacote.getDestino());
        lblResumoPreco.setText(Formatadores.formatarMoeda(pacote.getPreco()) + " / Pessoa");
        lblResumoVagas.setText(linha.vagasDisponiveis() + " vagas de " + linha.capacidadeTotal() + " totais");
        lblResumoRoteiro.setText(pacote.getRoteiroPrevisto() == null
                ? "Roteiro ainda nao detalhado." : pacote.getRoteiroPrevisto());

        if (dpDataInicio.getValue() == null) {
            dpDataInicio.setValue(pacote.getDataInicio());
        }
        if (dpDataFim.getValue() == null) {
            dpDataFim.setValue(pacote.getDataFim());
        }
        atualizarTotal();
    }

    private void atualizarTotal() {
        Pacote pacote = cboPacote.getValue();
        int quantidade = Formatadores.lerInteiro(txtQuantidadeViajantes.getText()).orElse(0);
        if (pacote == null || quantidade <= 0) {
            lblResumoTotal.setText(Formatadores.formatarMoeda(null));
            return;
        }
        lblResumoTotal.setText(Formatadores.formatarMoeda(
                contexto.getReservaController().calcularValorTotal(pacote, quantidade)));
    }

    private void configurarCombos() {
        cboCliente.setConverter(new StringConverter<>() {
            @Override
            public String toString(Cliente cliente) {
                return cliente == null ? "" : cliente.getNome()
                        + " (CPF: " + ValidadorCpf.formatar(cliente.getCpf()) + ")";
            }

            @Override
            public Cliente fromString(String texto) {
                return null;
            }
        });

        cboPacote.setConverter(new StringConverter<>() {
            @Override
            public String toString(Pacote pacote) {
                return pacote == null ? "" : pacote.getCodigo() + " - " + pacote.getDestino()
                        + " (" + Formatadores.formatarData(pacote.getDataInicio()) + ")";
            }

            @Override
            public Pacote fromString(String texto) {
                return null;
            }
        });
    }

    private void recarregarListas() {
        Cliente clienteSelecionado = cboCliente.getValue();
        Pacote pacoteSelecionado = cboPacote.getValue();
        cboCliente.setItems(FXCollections.observableArrayList(contexto.getClienteController().listar()));
        cboPacote.setItems(FXCollections.observableArrayList(contexto.getPacoteController().listar()));
        if (clienteSelecionado != null) {
            cboCliente.getSelectionModel().select(clienteSelecionado);
        }
        if (pacoteSelecionado != null) {
            cboPacote.getSelectionModel().select(pacoteSelecionado);
        }
    }

    private void limparFormulario() {
        cboCliente.getSelectionModel().clearSelection();
        cboPacote.getSelectionModel().clearSelection();
        txtQuantidadeViajantes.clear();
        dpDataInicio.setValue(null);
        dpDataInicio.getEditor().clear();
        dpDataFim.setValue(null);
        dpDataFim.getEditor().clear();
        txtObservacoes.clear();
        recarregarListas();
        limparErros();
        limparResumo();
    }

    private void limparResumo() {
        lblResumoDestino.setText("Selecione um pacote");
        lblResumoPreco.setText("-");
        lblResumoVagas.setText("-");
        lblResumoRoteiro.setText("O resumo do pacote aparece aqui apos a selecao.");
        lblResumoTotal.setText(Formatadores.formatarMoeda(null));
    }

    private void limparErros() {
        Alertas.limparErro(cboCliente, lblErroCliente);
        Alertas.limparErro(cboPacote, lblErroPacote);
        Alertas.limparErro(txtQuantidadeViajantes, lblErroQuantidade);
        Alertas.limparErro(dpDataInicio, lblErroDataInicio);
        Alertas.limparErro(dpDataFim, lblErroDataFim);
    }
}
