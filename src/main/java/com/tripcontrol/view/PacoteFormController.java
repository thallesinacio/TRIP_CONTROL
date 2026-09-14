package com.tripcontrol.view;

import com.tripcontrol.app.ContextoAplicacao;
import com.tripcontrol.controller.Resultado;
import com.tripcontrol.controller.StatusResultado;
import com.tripcontrol.controller.dto.DadosPacote;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.util.Formatadores;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

/** Tela do UC01 - Cadastrar Pacotes, incluindo os fluxos FA01 a FA04. */
public class PacoteFormController implements Initializable {

    @FXML private Label lblSecao;
    @FXML private TextField txtDestino;
    @FXML private DatePicker dpDataInicio;
    @FXML private DatePicker dpDataFim;
    @FXML private TextArea txtDescricao;
    @FXML private TextField txtPreco;
    @FXML private TextField txtCapacidadeTotal;
    @FXML private TextArea txtRoteiroPrevisto;
    @FXML private Button btnSalvarPacote;
    @FXML private Button btnCancelarPacote;

    @FXML private Label lblErroDestino;
    @FXML private Label lblErroDataInicio;
    @FXML private Label lblErroDataFim;
    @FXML private Label lblErroPreco;
    @FXML private Label lblErroCapacidade;

    private final ContextoAplicacao contexto;

    /** Preenchido quando a tela esta editando um pacote existente (FA03). */
    private Long pacoteEmEdicaoId;

    public PacoteFormController(ContextoAplicacao contexto) {
        this.contexto = contexto;
    }

    @Override
    public void initialize(URL local, ResourceBundle recursos) {
        Campos.configurarData(dpDataInicio);
        Campos.configurarData(dpDataFim);
        Campos.somenteNumeros(txtCapacidadeTotal);
        limparErros();
    }

    /** Carrega um pacote no formulario para edicao. */
    public void editar(Pacote pacote) {
        pacoteEmEdicaoId = pacote.getId();
        lblSecao.setText("Editando o pacote " + pacote.getCodigo());
        btnSalvarPacote.setText("Salvar Alteracoes");
        txtDestino.setText(pacote.getDestino());
        dpDataInicio.setValue(pacote.getDataInicio());
        dpDataFim.setValue(pacote.getDataFim());
        txtDescricao.setText(pacote.getDescricao());
        txtPreco.setText(Formatadores.formatarMoeda(pacote.getPreco()));
        txtCapacidadeTotal.setText(String.valueOf(pacote.getCapacidadeTotal()));
        txtRoteiroPrevisto.setText(pacote.getRoteiroPrevisto());
    }

    @FXML
    private void aoSalvarPacote(ActionEvent evento) {
        limparErros();
        DadosPacote dados = coletarDados();

        Resultado<Pacote> resultado = pacoteEmEdicaoId == null
                ? contexto.getPacoteController().cadastrar(dados)
                : contexto.getPacoteController().atualizar(pacoteEmEdicaoId, dados);

        if (resultado.isSucesso()) {
            Alertas.sucesso(resultado.getMensagem());
            limparFormulario();
            return;
        }

        if (resultado.getStatus() == StatusResultado.DUPLICIDADE) {
            tratarDuplicidade(resultado);
            return;
        }

        if (resultado.getStatus() == StatusResultado.ERRO_VALIDACAO) {
            destacarCampos(resultado);
            return;
        }

        Alertas.erro(resultado.mensagensConsolidadas());
    }

    /** FA04 - descarta o que foi digitado sem gravar nada. */
    @FXML
    private void aoCancelar(ActionEvent evento) {
        if (Alertas.confirmar("Cancelar cadastro",
                "Os dados digitados serao descartados. Deseja continuar?")) {
            limparFormulario();
        }
    }

    /** FA03 - pacote com mesmo destino e periodo: oferece abrir o existente. */
    private void tratarDuplicidade(Resultado<Pacote> resultado) {
        Pacote existente = resultado.getDado().orElse(null);
        if (existente == null) {
            Alertas.aviso(resultado.getMensagem());
            return;
        }
        if (Alertas.confirmar("Pacote ja cadastrado", resultado.getMensagem())) {
            editar(existente);
        }
    }

    private void destacarCampos(Resultado<Pacote> resultado) {
        Alertas.marcarErro(txtDestino, lblErroDestino, resultado, "destino");
        Alertas.marcarErro(dpDataInicio, lblErroDataInicio, resultado, "dataInicio");
        Alertas.marcarErro(dpDataFim, lblErroDataFim, resultado, "dataFim");
        Alertas.marcarErro(txtPreco, lblErroPreco, resultado, "preco");
        Alertas.marcarErro(txtCapacidadeTotal, lblErroCapacidade, resultado, "capacidadeTotal");
    }

    private DadosPacote coletarDados() {
        return new DadosPacote(
                txtDestino.getText(),
                Campos.textoDaData(dpDataInicio),
                Campos.textoDaData(dpDataFim),
                txtDescricao.getText(),
                txtPreco.getText(),
                txtCapacidadeTotal.getText(),
                txtRoteiroPrevisto.getText());
    }

    private void limparFormulario() {
        pacoteEmEdicaoId = null;
        lblSecao.setText("Informacoes Principais");
        btnSalvarPacote.setText("Salvar Pacote");
        txtDestino.clear();
        dpDataInicio.setValue(null);
        dpDataInicio.getEditor().clear();
        dpDataFim.setValue(null);
        dpDataFim.getEditor().clear();
        txtDescricao.clear();
        txtPreco.clear();
        txtCapacidadeTotal.clear();
        txtRoteiroPrevisto.clear();
        limparErros();
        txtDestino.requestFocus();
    }

    private void limparErros() {
        Alertas.limparErro(txtDestino, lblErroDestino);
        Alertas.limparErro(dpDataInicio, lblErroDataInicio);
        Alertas.limparErro(dpDataFim, lblErroDataFim);
        Alertas.limparErro(txtPreco, lblErroPreco);
        Alertas.limparErro(txtCapacidadeTotal, lblErroCapacidade);
    }
}
