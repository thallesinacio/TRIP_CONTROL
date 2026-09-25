package com.tripcontrol.view;

import com.tripcontrol.app.ContextoAplicacao;
import com.tripcontrol.controller.Resultado;
import com.tripcontrol.controller.StatusResultado;
import com.tripcontrol.controller.dto.PacoteComVagas;
import com.tripcontrol.model.SituacaoPacote;
import com.tripcontrol.util.Formatadores;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Tela do UC04 - Controlar Vagas.
 *
 * <p>A tela apenas apresenta e coleta dados: todo o cálculo de ocupação e toda a
 * validação da nova capacidade vivem no {@code PacoteController}
 * ({@code listarComVagas()} e {@code alterarCapacidade(...)}), que já trata os
 * fluxos alternativos FA03 (capacidade abaixo do ocupado) e FA04 (alteração
 * concorrente).</p>
 */
public class VagasController implements Initializable {

    @FXML private TableView<PacoteComVagas> tableVagas;
    @FXML private TableColumn<PacoteComVagas, String> colCodigo;
    @FXML private TableColumn<PacoteComVagas, String> colDestino;
    @FXML private TableColumn<PacoteComVagas, String> colInicio;
    @FXML private TableColumn<PacoteComVagas, String> colFim;
    @FXML private TableColumn<PacoteComVagas, Number> colVagasTotais;
    @FXML private TableColumn<PacoteComVagas, Number> colOcupadas;
    @FXML private TableColumn<PacoteComVagas, Number> colDisponiveis;
    @FXML private TableColumn<PacoteComVagas, SituacaoPacote> colSituacao;

    @FXML private VBox painelSemPacotes;
    @FXML private Button btnIrParaPacotes;
    @FXML private Button btnAtualizarLista;

    @FXML private VBox painelEdicao;
    @FXML private Label lblPacoteSelecionado;
    @FXML private Label lblCodigoPacote;
    @FXML private TextField txtNovaCapacidade;
    @FXML private Label lblErroCapacidade;
    @FXML private TextArea txtJustificativa;
    @FXML private Label lblErroJustificativa;
    @FXML private Label lblVagasOcupadas;
    @FXML private Label lblVagasLivresEstimadas;
    @FXML private Button btnSalvarAlteracao;
    @FXML private Button btnCancelarAlteracao;

    private final ContextoAplicacao contexto;
    private final Navegador navegador;

    /**
     * Ocupação exibida quando a tela carregou os dados do pacote selecionado.
     * É o valor comparado pelo Controller para detectar alteração concorrente (FA04).
     */
    private int ocupadasNaAberturaDaTela;

    public VagasController(ContextoAplicacao contexto, Navegador navegador) {
        this.contexto = contexto;
        this.navegador = navegador;
    }

    @Override
    public void initialize(URL local, ResourceBundle recursos) {
        configurarColunas();
        Campos.somenteNumeros(txtNovaCapacidade);

        tableVagas.getSelectionModel().selectedItemProperty()
                .addListener((observavel, anterior, atual) -> exibirNoPainel(atual));
        txtNovaCapacidade.textProperty()
                .addListener((observavel, anterior, atual) -> atualizarEstimativa());

        carregarTabela();
    }

    // ------------------------------------------------------------------
    // Carga da listagem (UC04, passos 2 a 4)
    // ------------------------------------------------------------------

    private void carregarTabela() {
        List<PacoteComVagas> linhas = contexto.getPacoteController().listarComVagas();
        tableVagas.setItems(FXCollections.observableArrayList(linhas));

        // FA01 - nenhum pacote cadastrado: a tela oferece o atalho para o UC01.
        boolean vazio = linhas.isEmpty();
        painelSemPacotes.setVisible(vazio);
        painelSemPacotes.setManaged(vazio);
        tableVagas.setVisible(!vazio);
        tableVagas.setManaged(!vazio);

        limparPainel();
    }

    /** Recarrega a listagem preservando o pacote que estava selecionado. */
    private void recarregarPreservandoSelecao(Long pacoteId) {
        carregarTabela();
        if (pacoteId == null) {
            return;
        }
        tableVagas.getItems().stream()
                .filter(linha -> pacoteId.equals(linha.pacote().getId()))
                .findFirst()
                .ifPresent(linha -> tableVagas.getSelectionModel().select(linha));
    }

    @FXML
    private void aoAtualizarLista(ActionEvent evento) {
        PacoteComVagas selecionado = tableVagas.getSelectionModel().getSelectedItem();
        recarregarPreservandoSelecao(selecionado == null ? null : selecionado.pacote().getId());
    }

    @FXML
    private void aoIrParaPacotes(ActionEvent evento) {
        navegador.abrir(Tela.PACOTES);
    }

    // ------------------------------------------------------------------
    // Painel de edição (UC04, passos 5 a 8)
    // ------------------------------------------------------------------

    private void exibirNoPainel(PacoteComVagas linha) {
        limparErros();
        if (linha == null) {
            limparPainel();
            return;
        }

        ocupadasNaAberturaDaTela = linha.vagasOcupadas();
        lblPacoteSelecionado.setText(linha.destino());
        lblCodigoPacote.setText("ID do Roteiro: " + linha.codigo());
        txtNovaCapacidade.setText(String.valueOf(linha.capacidadeTotal()));
        txtJustificativa.clear();
        lblVagasOcupadas.setText("* Vagas atualmente ocupadas: " + linha.vagasOcupadas());
        painelEdicao.setDisable(false);
        atualizarEstimativa();
    }

    /** Mostra quantas vagas ficariam livres com a capacidade digitada. */
    private void atualizarEstimativa() {
        Optional<Integer> novaCapacidade = Formatadores.lerInteiro(txtNovaCapacidade.getText());
        if (novaCapacidade.isEmpty()) {
            lblVagasLivresEstimadas.setText("* Novas vagas livres estimadas: -");
            return;
        }
        int estimativa = Math.max(0, novaCapacidade.get() - ocupadasNaAberturaDaTela);
        lblVagasLivresEstimadas.setText("* Novas vagas livres estimadas: " + estimativa);
    }

    @FXML
    private void aoSalvarAlteracao(ActionEvent evento) {
        limparErros();
        PacoteComVagas selecionado = tableVagas.getSelectionModel().getSelectedItem();
        if (selecionado == null) {
            Alertas.aviso("Selecione um pacote na tabela para alterar a capacidade.");
            return;
        }

        Long pacoteId = selecionado.pacote().getId();
        Resultado<PacoteComVagas> resultado = contexto.getPacoteController().alterarCapacidade(
                pacoteId,
                txtNovaCapacidade.getText(),
                txtJustificativa.getText(),
                contexto.getAutenticacaoController().nomeDoUsuarioAutenticado(),
                ocupadasNaAberturaDaTela);

        if (resultado.isSucesso()) {
            Alertas.sucesso(resultado.getMensagem());
            recarregarPreservandoSelecao(pacoteId);
            return;
        }

        if (resultado.getStatus() == StatusResultado.ERRO_VALIDACAO) {
            // FA03 entra aqui: a mensagem já traz o valor mínimo permitido.
            Alertas.marcarErro(txtNovaCapacidade, lblErroCapacidade, resultado, "novaCapacidade");
            Alertas.marcarErro(txtJustificativa, lblErroJustificativa, resultado, "justificativa");
            return;
        }

        if (resultado.getStatus() == StatusResultado.CONFLITO_CONCORRENCIA) {
            // FA04 - nada foi gravado: recarrega os dados atuais para nova conferência.
            Alertas.aviso(resultado.getMensagem());
            recarregarPreservandoSelecao(pacoteId);
            return;
        }

        Alertas.erro(resultado.mensagensConsolidadas());
        recarregarPreservandoSelecao(pacoteId);
    }

    /** FA05 - descarta a nova capacidade e a justificativa sem modificar o pacote. */
    @FXML
    private void aoCancelarAlteracao(ActionEvent evento) {
        exibirNoPainel(tableVagas.getSelectionModel().getSelectedItem());
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private void configurarColunas() {
        colCodigo.setCellValueFactory(celula -> texto(celula.getValue().codigo()));
        colDestino.setCellValueFactory(celula -> texto(celula.getValue().destino()));
        colInicio.setCellValueFactory(celula ->
                texto(Formatadores.formatarData(celula.getValue().pacote().getDataInicio())));
        colFim.setCellValueFactory(celula ->
                texto(Formatadores.formatarData(celula.getValue().pacote().getDataFim())));
        colVagasTotais.setCellValueFactory(celula ->
                new SimpleIntegerProperty(celula.getValue().capacidadeTotal()));
        colOcupadas.setCellValueFactory(celula ->
                new SimpleIntegerProperty(celula.getValue().vagasOcupadas()));
        colDisponiveis.setCellValueFactory(celula ->
                new SimpleIntegerProperty(celula.getValue().vagasDisponiveis()));
        colSituacao.setCellValueFactory(celula ->
                new SimpleObjectProperty<>(celula.getValue().situacao()));
        colSituacao.setCellFactory(coluna -> new TableCell<>() {
            @Override
            protected void updateItem(SituacaoPacote situacao, boolean vazio) {
                super.updateItem(situacao, vazio);
                getStyleClass().removeAll("chip-disponivel", "chip-lotado", "chip-encerrado");
                if (vazio || situacao == null) {
                    setText(null);
                    return;
                }
                setText(situacao.getDescricao());
                getStyleClass().add(switch (situacao) {
                    case DISPONIVEL -> "chip-disponivel";
                    case LOTADO -> "chip-lotado";
                    case ENCERRADO -> "chip-encerrado";
                });
            }
        });
    }

    private static ObservableValue<String> texto(String valor) {
        return new SimpleStringProperty(valor == null ? "" : valor);
    }

    private void limparPainel() {
        ocupadasNaAberturaDaTela = 0;
        lblPacoteSelecionado.setText("Selecione um pacote na tabela");
        lblCodigoPacote.setText("");
        txtNovaCapacidade.clear();
        txtJustificativa.clear();
        lblVagasOcupadas.setText("* Vagas atualmente ocupadas: -");
        lblVagasLivresEstimadas.setText("* Novas vagas livres estimadas: -");
        painelEdicao.setDisable(true);
        limparErros();
    }

    private void limparErros() {
        Alertas.limparErro(txtNovaCapacidade, lblErroCapacidade);
        Alertas.limparErro(txtJustificativa, lblErroJustificativa);
    }
}
