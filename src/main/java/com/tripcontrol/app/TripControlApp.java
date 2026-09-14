package com.tripcontrol.app;

import com.tripcontrol.view.Navegador;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Aplicacao JavaFX: monta o contexto, carrega os dados de demonstracao
 * (enquanto a persistencia e em memoria) e abre a tela de login.
 */
public class TripControlApp extends Application {

    @Override
    public void start(Stage janelaPrincipal) {
        ContextoAplicacao contexto = new ContextoAplicacao();
        DadosDemonstracao.carregar(contexto);

        janelaPrincipal.setTitle("TripControl - Sistema local de gestao de agencia");
        janelaPrincipal.setMinWidth(1024);
        janelaPrincipal.setMinHeight(640);

        new Navegador(contexto, janelaPrincipal).mostrarLogin();
        janelaPrincipal.show();
    }

    public static void main(String[] argumentos) {
        launch(argumentos);
    }
}
