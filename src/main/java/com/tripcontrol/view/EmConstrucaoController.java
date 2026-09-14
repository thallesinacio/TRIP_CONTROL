package com.tripcontrol.view;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Marcador das telas cujos casos de uso entram nas proximas fases
 * (UC04 a UC08). A navegacao ja funciona de ponta a ponta.
 */
public class EmConstrucaoController implements Initializable {

    @FXML private Label lblAviso;

    @Override
    public void initialize(URL local, ResourceBundle recursos) {
        lblAviso.setText("Tela prevista para a proxima etapa do projeto.\n"
                + "As entidades, os repositorios e as regras de apoio ja estao disponiveis no dominio.");
    }
}
