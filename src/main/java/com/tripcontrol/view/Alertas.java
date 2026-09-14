package com.tripcontrol.view;

import com.tripcontrol.controller.Resultado;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Label;

import java.util.Optional;

/** Caixas de dialogo padronizadas e destaque visual de campos com erro. */
public final class Alertas {

    private static final String CLASSE_CAMPO_INVALIDO = "campo-invalido";

    private Alertas() {
    }

    public static void sucesso(String mensagem) {
        exibir(Alert.AlertType.INFORMATION, "Operacao concluida", mensagem);
    }

    public static void erro(String mensagem) {
        exibir(Alert.AlertType.ERROR, "Nao foi possivel concluir", mensagem);
    }

    public static void aviso(String mensagem) {
        exibir(Alert.AlertType.WARNING, "Atencao", mensagem);
    }

    /** @return {@code true} se o funcionario confirmou a acao. */
    public static boolean confirmar(String titulo, String mensagem) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, mensagem, ButtonType.YES, ButtonType.NO);
        alerta.setTitle(titulo);
        alerta.setHeaderText(titulo);
        alerta.getDialogPane().setMinWidth(460);
        Optional<ButtonType> resposta = alerta.showAndWait();
        return resposta.isPresent() && resposta.get() == ButtonType.YES;
    }

    /** Marca o campo como invalido e exibe a mensagem correspondente abaixo dele. */
    public static void marcarErro(Control campo, Label rotuloErro, Resultado<?> resultado, String nomeDoCampo) {
        Optional<String> mensagem = resultado.mensagemDoCampo(nomeDoCampo);
        if (campo != null) {
            campo.getStyleClass().remove(CLASSE_CAMPO_INVALIDO);
            if (mensagem.isPresent()) {
                campo.getStyleClass().add(CLASSE_CAMPO_INVALIDO);
            }
        }
        if (rotuloErro != null) {
            rotuloErro.setText(mensagem.orElse(""));
            rotuloErro.setVisible(mensagem.isPresent());
            rotuloErro.setManaged(mensagem.isPresent());
        }
    }

    /** Limpa o destaque de erro de um campo. */
    public static void limparErro(Control campo, Label rotuloErro) {
        if (campo != null) {
            campo.getStyleClass().remove(CLASSE_CAMPO_INVALIDO);
        }
        if (rotuloErro != null) {
            rotuloErro.setText("");
            rotuloErro.setVisible(false);
            rotuloErro.setManaged(false);
        }
    }

    private static void exibir(Alert.AlertType tipo, String titulo, String mensagem) {
        Alert alerta = new Alert(tipo, mensagem, ButtonType.OK);
        alerta.setTitle(titulo);
        alerta.setHeaderText(titulo);
        alerta.getDialogPane().setMinWidth(460);
        alerta.showAndWait();
    }
}
