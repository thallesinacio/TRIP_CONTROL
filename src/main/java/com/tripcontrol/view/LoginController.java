package com.tripcontrol.view;

import com.tripcontrol.app.ContextoAplicacao;
import com.tripcontrol.app.DadosDemonstracao;
import com.tripcontrol.controller.Resultado;
import com.tripcontrol.model.Usuario;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

/** Tela de autenticacao (RNF04) -- pre-condicao de todos os casos de uso. */
public class LoginController implements Initializable {

    @FXML private TextField txtEmail;
    @FXML private PasswordField txtSenha;
    @FXML private CheckBox chkLembrarDispositivo;
    @FXML private Button btnEntrar;
    @FXML private Label lblMensagemErro;
    @FXML private Label lblDicaDemonstracao;

    private final ContextoAplicacao contexto;
    private final Navegador navegador;

    public LoginController(ContextoAplicacao contexto, Navegador navegador) {
        this.contexto = contexto;
        this.navegador = navegador;
    }

    @Override
    public void initialize(URL local, ResourceBundle recursos) {
        esconderErro();
        txtEmail.setText(DadosDemonstracao.EMAIL_DEMONSTRACAO);
        lblDicaDemonstracao.setText("Acesso de demonstracao: " + DadosDemonstracao.EMAIL_DEMONSTRACAO
                + " / " + DadosDemonstracao.SENHA_DEMONSTRACAO);
        txtSenha.setOnAction(this::aoEntrar);
    }

    @FXML
    private void aoEntrar(ActionEvent evento) {
        esconderErro();
        Resultado<Usuario> resultado = contexto.getAutenticacaoController()
                .autenticar(txtEmail.getText(), txtSenha.getText());

        if (!resultado.isSucesso()) {
            exibirErro(resultado.mensagensConsolidadas());
            txtSenha.clear();
            txtSenha.requestFocus();
            return;
        }
        navegador.mostrarAreaLogada();
    }

    @FXML
    private void aoEsquecerSenha(ActionEvent evento) {
        Alertas.aviso("Nesta versao a redefinicao de senha e feita pelo administrador da agencia.");
    }

    private void exibirErro(String mensagem) {
        lblMensagemErro.setText(mensagem);
        lblMensagemErro.setVisible(true);
        lblMensagemErro.setManaged(true);
    }

    private void esconderErro() {
        lblMensagemErro.setText("");
        lblMensagemErro.setVisible(false);
        lblMensagemErro.setManaged(false);
    }
}
