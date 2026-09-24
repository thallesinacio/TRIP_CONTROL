package com.tripcontrol.view;

import com.tripcontrol.app.ContextoAplicacao;
import com.tripcontrol.model.Recurso;
import com.tripcontrol.model.TipoItemItinerario;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.Optional;

/**
 * Abertura de janelas auxiliares que nao ocupam a area central.
 *
 * <p>Hoje existe uma so: o cadastro de recursos do UC06. Ele e um dialogo modal, e
 * nao um item do menu lateral, porque o menu deve continuar igual ao prototipo
 * (decisao B da equipe).</p>
 */
public final class Dialogos {

    private static final String FOLHA_DE_ESTILO = "/css/app.css";

    private Dialogos() {
    }

    /**
     * Abre o cadastro de hospedagem, transporte ou atividade (FA02 do UC06).
     *
     * @param tipoSugerido tipo ja escolhido na tela de itinerarios
     * @param dono         janela que fica bloqueada enquanto o dialogo estiver aberto
     * @return o recurso cadastrado, ou vazio se o funcionario cancelou
     */
    public static Optional<Recurso> cadastrarRecurso(ContextoAplicacao contexto,
                                                     TipoItemItinerario tipoSugerido,
                                                     Window dono) {
        URL recurso = Dialogos.class.getResource("/fxml/recurso-form.fxml");
        if (recurso == null) {
            throw new IllegalStateException("Arquivo FXML nao encontrado: /fxml/recurso-form.fxml");
        }

        FXMLLoader carregador = new FXMLLoader(recurso);
        carregador.setControllerFactory(tipo -> instanciar(tipo, contexto));
        try {
            Parent raiz = carregador.load();
            RecursoFormController controlador = carregador.getController();
            controlador.selecionarTipo(tipoSugerido);

            Stage janela = new Stage();
            janela.setTitle("Cadastrar Hospedagem, Transporte ou Atividade");
            janela.initModality(Modality.APPLICATION_MODAL);
            if (dono != null) {
                janela.initOwner(dono);
            }
            Scene cena = new Scene(raiz);
            URL folha = Dialogos.class.getResource(FOLHA_DE_ESTILO);
            if (folha != null) {
                cena.getStylesheets().add(folha.toExternalForm());
            }
            janela.setScene(cena);
            janela.showAndWait();

            return controlador.getCadastrado();
        } catch (IOException excecao) {
            throw new UncheckedIOException("Falha ao carregar /fxml/recurso-form.fxml", excecao);
        }
    }

    private static Object instanciar(Class<?> tipo, ContextoAplicacao contexto) {
        try {
            for (Constructor<?> construtor : tipo.getConstructors()) {
                Class<?>[] parametros = construtor.getParameterTypes();
                if (parametros.length == 1 && parametros[0] == ContextoAplicacao.class) {
                    return construtor.newInstance(contexto);
                }
            }
            return tipo.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException excecao) {
            throw new IllegalStateException("Nao foi possivel criar o controller " + tipo.getName(), excecao);
        }
    }
}
