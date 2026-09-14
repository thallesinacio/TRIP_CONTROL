package com.tripcontrol.view;

import com.tripcontrol.app.ContextoAplicacao;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Estrutura da area logada: menu lateral fixo, cabecalho com titulo/subtitulo
 * e area central que recebe cada tela, conforme o prototipo.
 */
public class PrincipalController implements Initializable {

    @FXML private ToggleButton btnMenuPacotes;
    @FXML private ToggleButton btnMenuClientes;
    @FXML private ToggleButton btnMenuReservas;
    @FXML private ToggleButton btnMenuVagas;
    @FXML private ToggleButton btnMenuPagamentos;
    @FXML private ToggleButton btnMenuItinerarios;
    @FXML private ToggleButton btnMenuCancelamentos;
    @FXML private ToggleButton btnMenuRelatorios;
    @FXML private Label lblTituloTela;
    @FXML private Label lblSubtituloTela;
    @FXML private Label lblUsuarioLogado;
    @FXML private StackPane areaConteudo;

    private final ContextoAplicacao contexto;
    private final Navegador navegador;
    private final Map<Tela, ToggleButton> botoesPorTela = new LinkedHashMap<>();
    private final ToggleGroup grupoMenu = new ToggleGroup();

    public PrincipalController(ContextoAplicacao contexto, Navegador navegador) {
        this.contexto = contexto;
        this.navegador = navegador;
    }

    @Override
    public void initialize(URL local, ResourceBundle recursos) {
        botoesPorTela.put(Tela.PACOTES, btnMenuPacotes);
        botoesPorTela.put(Tela.CLIENTES, btnMenuClientes);
        botoesPorTela.put(Tela.RESERVAS, btnMenuReservas);
        botoesPorTela.put(Tela.VAGAS, btnMenuVagas);
        botoesPorTela.put(Tela.PAGAMENTOS, btnMenuPagamentos);
        botoesPorTela.put(Tela.ITINERARIOS, btnMenuItinerarios);
        botoesPorTela.put(Tela.CANCELAMENTOS, btnMenuCancelamentos);
        botoesPorTela.put(Tela.RELATORIOS, btnMenuRelatorios);

        botoesPorTela.forEach((tela, botao) -> {
            botao.setToggleGroup(grupoMenu);
            botao.setOnAction(evento -> navegador.abrir(tela));
        });

        lblUsuarioLogado.setText(contexto.getAutenticacaoController().nomeDoUsuarioAutenticado());
    }

    /** Coloca a tela no centro e atualiza cabecalho e item selecionado do menu. */
    public void exibir(Tela tela, Parent conteudo) {
        lblTituloTela.setText(tela.getTitulo());
        lblSubtituloTela.setText(tela.getSubtitulo());
        areaConteudo.getChildren().setAll(conteudo);

        ToggleButton botao = botoesPorTela.get(tela);
        if (botao != null) {
            botao.setSelected(true);
        }
    }

    @FXML
    private void aoSair(ActionEvent evento) {
        if (Alertas.confirmar("Encerrar sessao", "Deseja sair do TripControl?")) {
            navegador.sair();
        }
    }
}
