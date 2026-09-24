package com.tripcontrol.view;

import com.tripcontrol.app.ContextoAplicacao;
import com.tripcontrol.controller.Resultado;
import com.tripcontrol.controller.StatusResultado;
import com.tripcontrol.controller.dto.DadosRecurso;
import com.tripcontrol.model.Recurso;
import com.tripcontrol.model.TipoItemItinerario;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Dialogo modal de cadastro de hospedagem, transporte e atividade
 * (decisao B da equipe; atalho do FA02 do UC06).
 *
 * <p>Fecha devolvendo o recurso gravado, que a tela de itinerarios ja deixa
 * selecionado no combo — o passo 5.3 do FA02 diz para voltar ao passo 5 com o tipo
 * mantido.</p>
 */
public class RecursoFormController implements Initializable {

    @FXML private ComboBox<TipoItemItinerario> cboTipo;
    @FXML private TextField txtNome;
    @FXML private TextField txtLocal;
    @FXML private TextField txtContato;
    @FXML private TextArea txtDescricao;
    @FXML private Label lblErroTipo;
    @FXML private Label lblErroNome;
    @FXML private Button btnSalvar;
    @FXML private Button btnCancelar;

    private final ContextoAplicacao contexto;

    /** Recurso gravado, quando o funcionario conclui o cadastro. */
    private Recurso cadastrado;

    public RecursoFormController(ContextoAplicacao contexto) {
        this.contexto = contexto;
    }

    @Override
    public void initialize(URL local, ResourceBundle recursos) {
        cboTipo.setItems(FXCollections.observableArrayList(TipoItemItinerario.values()));
    }

    /** Pre-seleciona o tipo que estava escolhido na tela de itinerarios. */
    public void selecionarTipo(TipoItemItinerario tipo) {
        cboTipo.setValue(tipo);
    }

    public Optional<Recurso> getCadastrado() {
        return Optional.ofNullable(cadastrado);
    }

    @FXML
    private void aoSalvar(ActionEvent evento) {
        Alertas.limparErro(cboTipo, lblErroTipo);
        Alertas.limparErro(txtNome, lblErroNome);

        Resultado<Recurso> resultado = contexto.getItinerarioController().cadastrarRecurso(
                new DadosRecurso(cboTipo.getValue(), txtNome.getText(), txtLocal.getText(),
                        txtContato.getText(), txtDescricao.getText()));

        if (resultado.isSucesso()) {
            cadastrado = resultado.getDado().orElse(null);
            Alertas.sucesso(resultado.getMensagem());
            fechar();
            return;
        }

        if (resultado.getStatus() == StatusResultado.ERRO_VALIDACAO) {
            Alertas.marcarErro(cboTipo, lblErroTipo, resultado, "tipo");
            Alertas.marcarErro(txtNome, lblErroNome, resultado, "nome");
            return;
        }

        // DUPLICIDADE: o registro ja existe e pode ser usado como esta.
        if (resultado.getStatus() == StatusResultado.DUPLICIDADE
                && Alertas.confirmar("Registro já cadastrado",
                        resultado.getMensagem() + "\n\nDeseja usar o registro existente?")) {
            cadastrado = resultado.getDado().orElse(null);
            fechar();
            return;
        }
        Alertas.aviso(resultado.mensagensConsolidadas());
    }

    /** FA02, passo 5.3: cadastro cancelado devolve o fluxo ao passo 4. */
    @FXML
    private void aoCancelar(ActionEvent evento) {
        cadastrado = null;
        fechar();
    }

    private void fechar() {
        ((Stage) btnSalvar.getScene().getWindow()).close();
    }
}
