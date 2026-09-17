package com.tripcontrol.app;

import com.tripcontrol.util.ConexaoIndisponivelException;
import com.tripcontrol.util.ConnectionFactory;
import com.tripcontrol.view.Navegador;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

/**
 * Aplicacao JavaFX: monta o contexto (em memoria ou sobre o PostgreSQL, conforme
 * a chave {@code repositorio.tipo}), aplica as migracoes quando ha banco e abre a
 * tela de login.
 */
public class TripControlApp extends Application {

    private ContextoAplicacao contexto;

    @Override
    public void start(Stage janelaPrincipal) {
        try {
            contexto = ContextoAplicacao.criar();
        } catch (ConexaoIndisponivelException falha) {
            avisarBancoIndisponivel(falha);
            Platform.exit();
            return;
        }

        // A carga de demonstracao so faz sentido em memoria: com banco, os dados
        // ficam gravados e o usuario inicial vem da migracao R__carga_inicial.
        if (!contexto.isPersistenciaEmBanco()) {
            DadosDemonstracao.carregar(contexto);
        }

        janelaPrincipal.setTitle("TripControl - Sistema local de gestao de agencia");
        janelaPrincipal.setMinWidth(1024);
        janelaPrincipal.setMinHeight(640);

        new Navegador(contexto, janelaPrincipal).mostrarLogin();
        janelaPrincipal.show();
    }

    /** Fecha o pool de conexoes ao encerrar a aplicacao. */
    @Override
    public void stop() {
        ConnectionFactory.encerrar();
    }

    private void avisarBancoIndisponivel(ConexaoIndisponivelException falha) {
        Alert alerta = new Alert(Alert.AlertType.ERROR,
                falha.getMessage()
                        + "\n\nVerifique se o PostgreSQL esta rodando e se os dados de "
                        + "config/database.properties estao corretos.\n\n"
                        + "Para abrir o sistema sem banco, use repositorio.tipo=memoria "
                        + "em config/aplicacao.properties.",
                ButtonType.OK);
        alerta.setTitle("Banco de dados indisponivel");
        alerta.setHeaderText("Nao foi possivel iniciar o TripControl");
        alerta.getDialogPane().setMinWidth(520);
        alerta.showAndWait();
    }

    public static void main(String[] argumentos) {
        launch(argumentos);
    }
}
