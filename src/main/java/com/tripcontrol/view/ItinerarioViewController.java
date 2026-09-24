package com.tripcontrol.view;

import com.tripcontrol.app.ContextoAplicacao;
import com.tripcontrol.controller.Resultado;
import com.tripcontrol.controller.StatusResultado;
import com.tripcontrol.controller.dto.DadosItemItinerario;
import com.tripcontrol.controller.dto.ItemCronograma;
import com.tripcontrol.controller.dto.PacoteComVagas;
import com.tripcontrol.model.ItemItinerario;
import com.tripcontrol.model.Itinerario;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Recurso;
import com.tripcontrol.model.StatusItinerario;
import com.tripcontrol.model.TipoItemItinerario;
import com.tripcontrol.util.Formatadores;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Tela do UC06 - Montar Itinerario ("Montar Itinerario Detalhado" no prototipo).
 *
 * <p>A tela pesquisa o pacote, desenha o cronograma e coleta os dados do item; as
 * validacoes de periodo, intervalo e conflito (FA03, FA04, FA05) e a gravacao
 * (passo 11, FA06) estao em {@code ItinerarioController}. Ficam aqui apenas os dois
 * fluxos que sao de interface: a confirmacao do FA07 e a troca de campos conforme o
 * tipo de evento escolhido.</p>
 */
public class ItinerarioViewController implements Initializable {

    @FXML private TextField txtTermoPacote;
    @FXML private Button btnBuscarPacote;
    @FXML private Label lblMensagemPesquisa;

    @FXML private VBox painelResultados;
    @FXML private TableView<PacoteComVagas> tablePacotes;
    @FXML private TableColumn<PacoteComVagas, String> colPacoteCodigo;
    @FXML private TableColumn<PacoteComVagas, String> colPacoteDestino;
    @FXML private TableColumn<PacoteComVagas, String> colPacotePeriodo;
    @FXML private TableColumn<PacoteComVagas, String> colPacoteVagas;

    @FXML private VBox painelPacote;
    @FXML private Label lblCodigoPacote;
    @FXML private Label lblDestinoPacote;
    @FXML private Label lblPeriodoPacote;
    @FXML private Button btnAlterarPacote;

    @FXML private GridPane painelMontagem;
    @FXML private VBox listaCronograma;
    @FXML private Label lblCronogramaVazio;

    @FXML private ComboBox<TipoItemItinerario> cboTipoEvento;
    @FXML private ComboBox<Recurso> cboRecurso;
    @FXML private Hyperlink lnkCadastrarRecurso;
    @FXML private Label lblRotuloInicio;
    @FXML private DatePicker dpDataInicio;
    @FXML private TextField txtHoraInicio;
    @FXML private VBox painelFim;
    @FXML private Label lblRotuloFim;
    @FXML private DatePicker dpDataFim;
    @FXML private TextField txtHoraFim;
    @FXML private GridPane painelLocais;
    @FXML private VBox painelOrigem;
    @FXML private VBox painelDestino;
    @FXML private Label lblRotuloDestino;
    @FXML private TextField txtLocalOrigem;
    @FXML private TextField txtLocalDestino;
    @FXML private TextArea txtInstrucoes;
    @FXML private Button btnAdicionarItem;

    @FXML private Label lblErroTipo;
    @FXML private Label lblErroRecurso;
    @FXML private Label lblErroInicio;
    @FXML private Label lblErroFim;
    @FXML private Label lblErroLocalOrigem;
    @FXML private Label lblErroLocalDestino;

    @FXML private HBox rodapeItinerario;
    @FXML private Button btnCancelarItinerario;
    @FXML private Button btnFinalizarItinerario;

    private final ContextoAplicacao contexto;

    private Pacote pacoteSelecionado;
    private Itinerario itinerario;

    public ItinerarioViewController(ContextoAplicacao contexto) {
        this.contexto = contexto;
    }

    @Override
    public void initialize(URL local, ResourceBundle recursos) {
        configurarColunas();
        Campos.configurarData(dpDataInicio);
        Campos.configurarData(dpDataFim);

        cboTipoEvento.setItems(FXCollections.observableArrayList(TipoItemItinerario.values()));
        cboTipoEvento.valueProperty().addListener(
                (observavel, anterior, atual) -> aoTrocarTipo(atual));

        cboRecurso.setConverter(new StringConverter<>() {
            @Override
            public String toString(Recurso recurso) {
                return recurso == null ? "" : recurso.getNome();
            }

            @Override
            public Recurso fromString(String texto) {
                return null;
            }
        });

        tablePacotes.getSelectionModel().selectedItemProperty().addListener(
                (observavel, anterior, atual) -> selecionarPacote(atual));

        aoTrocarTipo(null);
        esconderMontagem();
    }

    // ------------------------------------------------------------------
    // Pesquisa do pacote (UC06, passos 2 e 3)
    // ------------------------------------------------------------------

    @FXML
    private void aoBuscarPacote(ActionEvent evento) {
        esconderMensagemPesquisa();

        Resultado<List<PacoteComVagas>> resultado =
                contexto.getItinerarioController().buscarPacotes(txtTermoPacote.getText());

        if (!resultado.isSucesso()) {
            // FA01 - nenhum pacote corresponde; a mensagem aponta o cadastro (UC01).
            tablePacotes.getItems().clear();
            painelResultados.setVisible(false);
            painelResultados.setManaged(false);
            esconderMontagem();
            exibirMensagemPesquisa(resultado.mensagensConsolidadas());
            return;
        }

        List<PacoteComVagas> encontrados = resultado.getDado().orElse(List.of());
        tablePacotes.setItems(FXCollections.observableArrayList(encontrados));
        boolean precisaEscolher = encontrados.size() > 1;
        painelResultados.setVisible(precisaEscolher);
        painelResultados.setManaged(precisaEscolher);
        tablePacotes.getSelectionModel().selectFirst();
    }

    /** FA01, passo 2.2: volta a pesquisa mantendo o itinerario gravado intacto. */
    @FXML
    private void aoAlterarPacote(ActionEvent evento) {
        esconderMontagem();
        tablePacotes.getSelectionModel().clearSelection();
        txtTermoPacote.clear();
        txtTermoPacote.requestFocus();
    }

    private void selecionarPacote(PacoteComVagas linha) {
        if (linha == null) {
            esconderMontagem();
            return;
        }

        pacoteSelecionado = linha.pacote();
        Resultado<Itinerario> aberto =
                contexto.getItinerarioController().abrir(pacoteSelecionado.getId());
        if (!aberto.isSucesso()) {
            esconderMontagem();
            exibirMensagemPesquisa(aberto.mensagensConsolidadas());
            return;
        }
        itinerario = aberto.getDado().orElseThrow();

        lblCodigoPacote.setText(linha.codigo());
        lblDestinoPacote.setText(linha.destino());
        lblPeriodoPacote.setText("Período: "
                + contexto.getItinerarioController().periodoDoPacote(pacoteSelecionado)
                + " | Vagas Restantes: " + linha.vagasDisponiveis()
                + " de " + linha.capacidadeTotal());

        exibirMontagem();
        limparFormulario();
        desenharCronograma();
    }

    // ------------------------------------------------------------------
    // Cronograma (UC06, passo 4)
    // ------------------------------------------------------------------

    private void desenharCronograma() {
        List<ItemCronograma> itens = contexto.getItinerarioController().cronograma(itinerario);
        listaCronograma.getChildren().clear();
        itens.forEach(item -> listaCronograma.getChildren().add(montarCartao(item)));

        boolean vazio = itens.isEmpty();
        lblCronogramaVazio.setVisible(vazio);
        lblCronogramaVazio.setManaged(vazio);
    }

    /** Cartao do item: tag colorida por tipo, titulo, detalhes e marca de rascunho. */
    private Node montarCartao(ItemCronograma item) {
        Label tag = new Label(item.tag());
        tag.getStyleClass().addAll("tag-tipo", "tag-tipo-" + item.tag().toLowerCase());

        Label titulo = new Label(item.titulo());
        titulo.getStyleClass().add("item-titulo");

        HBox cabecalho = new HBox(10.0, tag, titulo);
        cabecalho.setAlignment(Pos.CENTER_LEFT);

        if (item.rascunho()) {
            Label rascunho = new Label("Rascunho");
            rascunho.getStyleClass().add("tag-rascunho");
            cabecalho.getChildren().addAll(new Region(), rascunho);
            HBox.setHgrow(cabecalho.getChildren().get(2), javafx.scene.layout.Priority.ALWAYS);
        }

        Label detalhes = new Label(item.detalhes());
        detalhes.setWrapText(true);
        detalhes.getStyleClass().add("item-detalhes");

        VBox cartao = new VBox(6.0, cabecalho, detalhes);
        cartao.getStyleClass().add("cartao-item");
        cartao.setPadding(new Insets(14.0, 16.0, 14.0, 16.0));

        if (item.item().getInstrucoesOperacionais() != null) {
            Label instrucoes = new Label(item.item().getInstrucoesOperacionais());
            instrucoes.setWrapText(true);
            instrucoes.getStyleClass().add("nota-calculo");
            cartao.getChildren().add(instrucoes);
        }

        Button remover = new Button("Remover do cronograma");
        remover.getStyleClass().add("botao-link");
        remover.setOnAction(evento -> removerItem(item.item()));
        cartao.getChildren().add(remover);

        return cartao;
    }

    private void removerItem(ItemItinerario item) {
        if (!Alertas.confirmar("Remover item",
                "Remover este item do cronograma? A alteração só vale depois de finalizar o itinerário.")) {
            return;
        }
        contexto.getItinerarioController().removerItem(itinerario, item);
        desenharCronograma();
    }

    // ------------------------------------------------------------------
    // Adicionar item (UC06, passos 5 a 8)
    // ------------------------------------------------------------------

    /** FA02, passo 5.2: abre o cadastro e ja deixa o novo registro selecionado. */
    @FXML
    private void aoCadastrarRecurso(ActionEvent evento) {
        TipoItemItinerario tipo = cboTipoEvento.getValue();
        Optional<Recurso> cadastrado = Dialogos.cadastrarRecurso(contexto, tipo,
                lnkCadastrarRecurso.getScene() == null ? null : lnkCadastrarRecurso.getScene().getWindow());

        if (cadastrado.isEmpty()) {
            return;
        }
        // 5.3 - o tipo e mantido; se o cadastro foi de outro tipo, a tela acompanha.
        cboTipoEvento.setValue(cadastrado.get().getTipo());
        recarregarRecursos(cadastrado.get().getTipo());
        cboRecurso.setValue(cadastrado.get());
    }

    @FXML
    private void aoAdicionarItem(ActionEvent evento) {
        limparErros();
        if (itinerario == null || pacoteSelecionado == null) {
            Alertas.aviso("Pesquise e selecione o pacote antes de montar o cronograma.");
            return;
        }

        Recurso recurso = cboRecurso.getValue();
        DadosItemItinerario dados = new DadosItemItinerario(
                cboTipoEvento.getValue(),
                recurso == null ? null : recurso.getId(),
                Campos.textoDaData(dpDataInicio),
                txtHoraInicio.getText(),
                Campos.textoDaData(dpDataFim),
                txtHoraFim.getText(),
                txtLocalOrigem.getText(),
                txtLocalDestino.getText(),
                txtInstrucoes.getText());

        Resultado<ItemItinerario> resultado = contexto.getItinerarioController()
                .adicionarItem(itinerario, pacoteSelecionado, dados);

        if (resultado.isSucesso()) {
            desenharCronograma();
            limparFormulario();
            return;
        }

        if (resultado.getStatus() == StatusResultado.ERRO_VALIDACAO) {
            // FA03, FA04 e FA05 chegam como erro de campo, cada um no seu lugar.
            Alertas.marcarErro(cboTipoEvento, lblErroTipo, resultado, "tipo");
            Alertas.marcarErro(cboRecurso, lblErroRecurso, resultado, "recurso");
            Alertas.marcarErro(dpDataInicio, lblErroInicio, resultado, "inicio");
            Alertas.marcarErro(dpDataFim, lblErroFim, resultado, "fim");
            Alertas.marcarErro(txtLocalOrigem, lblErroLocalOrigem, resultado, "localOrigem");
            Alertas.marcarErro(txtLocalDestino, lblErroLocalDestino, resultado, "localDestino");
            return;
        }

        Alertas.aviso(resultado.mensagensConsolidadas());
    }

    // ------------------------------------------------------------------
    // Finalizar e cancelar (UC06, passos 10 e 11; FA06 e FA07)
    // ------------------------------------------------------------------

    @FXML
    private void aoFinalizarItinerario(ActionEvent evento) {
        if (itinerario == null) {
            Alertas.aviso("Pesquise e selecione o pacote antes de finalizar o itinerário.");
            return;
        }

        Resultado<Itinerario> resultado = contexto.getItinerarioController().finalizar(itinerario);
        if (resultado.isSucesso()) {
            Alertas.sucesso(resultado.getMensagem());
            desenharCronograma();
            return;
        }
        // FA06 - itinerario sem itens.
        Alertas.aviso(resultado.mensagensConsolidadas());
    }

    /** FA07 - descarta o rascunho depois de confirmar com o funcionario. */
    @FXML
    private void aoCancelarItinerario(ActionEvent evento) {
        if (itinerario == null) {
            return;
        }
        boolean gravado = itinerario.getStatus() == StatusItinerario.FINALIZADO;
        String aviso = gravado
                ? "Descartar os itens ainda não gravados? O itinerário finalizado do pacote é mantido."
                : "Descartar o rascunho deste itinerário? Os itens adicionados serão perdidos.";

        // 4.2 e 4.3 - so descarta com confirmacao; "Continuar Editando" nao muda nada.
        if (!Alertas.confirmar("Cancelar itinerário", aviso)) {
            return;
        }

        itinerario = contexto.getItinerarioController().descartarRascunho(pacoteSelecionado.getId());
        limparFormulario();
        desenharCronograma();
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /**
     * Troca os campos conforme o tipo escolhido, mantendo o mesmo visual do
     * prototipo (que so desenha o formulario de Transporte).
     */
    private void aoTrocarTipo(TipoItemItinerario tipo) {
        recarregarRecursos(tipo);

        boolean transporte = tipo == TipoItemItinerario.TRANSPORTE;
        boolean atividade = tipo == TipoItemItinerario.ATIVIDADE;

        lblRotuloInicio.setText(transporte ? "Data / Hora Saída"
                : atividade ? "Data / Hora Início" : "Data / Hora Check-in");
        lblRotuloFim.setText(transporte ? "Data / Hora Chegada"
                : atividade ? "Hora de Término" : "Data / Hora Check-out");

        // A atividade termina no mesmo dia: a tabela Atividade guarda uma data e
        // dois horarios, entao nao ha data de termino para digitar.
        dpDataFim.setVisible(!atividade);
        dpDataFim.setManaged(!atividade);

        exibir(painelOrigem, transporte);
        exibir(painelDestino, transporte || atividade);
        exibir(painelLocais, transporte || atividade);
        lblRotuloDestino.setText(atividade ? "Local de Encontro" : "Local de Destino");
    }

    private void recarregarRecursos(TipoItemItinerario tipo) {
        cboRecurso.setItems(FXCollections.observableArrayList(
                contexto.getItinerarioController().listarRecursos(tipo)));
        cboRecurso.getSelectionModel().clearSelection();
    }

    private void configurarColunas() {
        colPacoteCodigo.setCellValueFactory(celula -> texto(celula.getValue().codigo()));
        colPacoteDestino.setCellValueFactory(celula -> texto(celula.getValue().destino()));
        colPacotePeriodo.setCellValueFactory(celula -> texto(
                Formatadores.formatarData(celula.getValue().pacote().getDataInicio())
                        + " a " + Formatadores.formatarData(celula.getValue().pacote().getDataFim())));
        colPacoteVagas.setCellValueFactory(celula -> texto(
                celula.getValue().vagasDisponiveis() + " de " + celula.getValue().capacidadeTotal()));
    }

    private static ObservableValue<String> texto(String valor) {
        return new SimpleStringProperty(valor == null ? "" : valor);
    }

    private void exibirMontagem() {
        exibir(painelPacote, true);
        exibir(painelMontagem, true);
        exibir(rodapeItinerario, true);
    }

    private void esconderMontagem() {
        pacoteSelecionado = null;
        itinerario = null;
        listaCronograma.getChildren().clear();
        exibir(painelPacote, false);
        exibir(painelMontagem, false);
        exibir(rodapeItinerario, false);
    }

    private void limparFormulario() {
        cboRecurso.getSelectionModel().clearSelection();
        dpDataInicio.setValue(contexto.getItinerarioController().primeiroDia(pacoteSelecionado));
        dpDataFim.setValue(contexto.getItinerarioController().primeiroDia(pacoteSelecionado));
        txtHoraInicio.clear();
        txtHoraFim.clear();
        txtLocalOrigem.clear();
        txtLocalDestino.clear();
        txtInstrucoes.clear();
        limparErros();
    }

    private void exibirMensagemPesquisa(String mensagem) {
        lblMensagemPesquisa.setText(mensagem);
        exibir(lblMensagemPesquisa, true);
    }

    private void esconderMensagemPesquisa() {
        lblMensagemPesquisa.setText("");
        exibir(lblMensagemPesquisa, false);
    }

    private static void exibir(Node componente, boolean visivel) {
        componente.setVisible(visivel);
        componente.setManaged(visivel);
    }

    private void limparErros() {
        Alertas.limparErro(cboTipoEvento, lblErroTipo);
        Alertas.limparErro(cboRecurso, lblErroRecurso);
        Alertas.limparErro(dpDataInicio, lblErroInicio);
        Alertas.limparErro(dpDataFim, lblErroFim);
        Alertas.limparErro(txtLocalOrigem, lblErroLocalOrigem);
        Alertas.limparErro(txtLocalDestino, lblErroLocalDestino);
    }
}
