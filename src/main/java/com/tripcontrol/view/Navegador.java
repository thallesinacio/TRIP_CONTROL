package com.tripcontrol.view;

import com.tripcontrol.app.ContextoAplicacao;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.function.Consumer;

/**
 * Responsavel por carregar telas FXML e trocar o conteudo exibido.
 *
 * <p>Funciona tambem como fabrica de controllers de tela: cada controller
 * recebe o {@link ContextoAplicacao} (e o proprio navegador, quando precisa
 * mudar de tela) pelo construtor, em vez de acessar variaveis globais.</p>
 */
public class Navegador {

    private static final String FOLHA_DE_ESTILO = "/css/app.css";

    private final ContextoAplicacao contexto;
    private final Stage janelaPrincipal;
    private PrincipalController principalController;

    public Navegador(ContextoAplicacao contexto, Stage janelaPrincipal) {
        this.contexto = contexto;
        this.janelaPrincipal = janelaPrincipal;
    }

    /** Exibe a tela de login ocupando toda a janela. */
    public void mostrarLogin() {
        this.principalController = null;
        Parent raiz = carregar(Tela.LOGIN, null);
        trocarCena(raiz, 1100, 680);
    }

    /** Exibe a area logada (menu lateral + conteudo) abrindo a tela de pacotes. */
    public void mostrarAreaLogada() {
        FXMLLoader carregador = criarCarregador("/fxml/principal.fxml");
        try {
            Parent raiz = carregador.load();
            this.principalController = carregador.getController();
            trocarCena(raiz, 1280, 760);
            abrir(Tela.PACOTES);
        } catch (IOException excecao) {
            throw new UncheckedIOException("Falha ao carregar a tela principal.", excecao);
        }
    }

    /** Troca o conteudo central da area logada. */
    public void abrir(Tela tela) {
        abrir(tela, null);
    }

    /**
     * Troca o conteudo central permitindo preparar o controller da tela destino,
     * usado por exemplo no atalho "cadastrar cliente" durante uma reserva (UC03, FA03).
     *
     * @param preparador acao aplicada ao controller recem-criado; pode ser {@code null}
     * @param <C>        tipo do controller da tela destino
     */
    public <C> void abrir(Tela tela, Consumer<C> preparador) {
        Parent conteudo = carregar(tela, preparador);
        if (principalController != null) {
            principalController.exibir(tela, conteudo);
        }
    }

    public void sair() {
        contexto.getAutenticacaoController().encerrarSessao();
        mostrarLogin();
    }

    public ContextoAplicacao getContexto() {
        return contexto;
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private <C> Parent carregar(Tela tela, Consumer<C> preparador) {
        FXMLLoader carregador = criarCarregador(tela.getArquivoFxml());
        try {
            Parent raiz = carregador.load();
            if (preparador != null) {
                @SuppressWarnings("unchecked")
                C controller = (C) carregador.getController();
                preparador.accept(controller);
            }
            return raiz;
        } catch (IOException excecao) {
            throw new UncheckedIOException("Falha ao carregar " + tela.getArquivoFxml(), excecao);
        }
    }

    private FXMLLoader criarCarregador(String caminhoFxml) {
        URL recurso = Navegador.class.getResource(caminhoFxml);
        if (recurso == null) {
            throw new IllegalStateException("Arquivo FXML nao encontrado: " + caminhoFxml);
        }
        FXMLLoader carregador = new FXMLLoader(recurso);
        carregador.setControllerFactory(this::instanciarController);
        return carregador;
    }

    /**
     * Cria o controller da tela injetando as dependencias disponiveis.
     * Ordem tentada: (ContextoAplicacao, Navegador), (ContextoAplicacao), ().
     */
    private Object instanciarController(Class<?> tipo) {
        try {
            for (Constructor<?> construtor : tipo.getConstructors()) {
                Class<?>[] parametros = construtor.getParameterTypes();
                if (parametros.length == 2
                        && parametros[0] == ContextoAplicacao.class
                        && parametros[1] == Navegador.class) {
                    return construtor.newInstance(contexto, this);
                }
            }
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

    private void trocarCena(Parent raiz, double largura, double altura) {
        Scene cena = janelaPrincipal.getScene();
        if (cena == null) {
            cena = new Scene(raiz, largura, altura);
            aplicarEstilo(cena);
            janelaPrincipal.setScene(cena);
        } else {
            cena.setRoot(raiz);
        }
        janelaPrincipal.setWidth(largura);
        janelaPrincipal.setHeight(altura);
        janelaPrincipal.centerOnScreen();
    }

    private void aplicarEstilo(Scene cena) {
        URL folha = Navegador.class.getResource(FOLHA_DE_ESTILO);
        if (folha != null) {
            cena.getStylesheets().add(folha.toExternalForm());
        }
    }
}
