package com.tripcontrol.view;

import com.tripcontrol.app.ContextoAplicacao;
import com.tripcontrol.controller.Resultado;
import com.tripcontrol.controller.StatusResultado;
import com.tripcontrol.controller.dto.ResumoFinanceiro;
import com.tripcontrol.controller.dto.ResumoReserva;
import com.tripcontrol.model.MotivoCancelamento;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.util.Formatadores;
import com.tripcontrol.util.ValidadorCpf;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Tela do UC07 - Cancelar Reservas.
 *
 * <p>A tela pesquisa, apresenta a reserva escolhida e coleta o motivo; a decisao
 * sobre cancelar ou nao (incluindo os fluxos FA01, FA02, FA03, FA05 e FA06) esta
 * em {@code ReservaController.cancelar(...)}. O unico fluxo que vive aqui e o
 * FA04: o funcionario desistir na mensagem de confirmacao.</p>
 */
public class CancelamentoController implements Initializable {

    @FXML private TextField txtCodigoReserva;
    @FXML private TextField txtCpfCliente;
    @FXML private TextField txtNomeCliente;
    @FXML private TextField txtCodigoPacote;
    @FXML private Button btnConsultar;
    @FXML private Label lblMensagemPesquisa;

    @FXML private TableView<ResumoReserva> tableReservas;
    @FXML private TableColumn<ResumoReserva, String> colCodigo;
    @FXML private TableColumn<ResumoReserva, String> colCliente;
    @FXML private TableColumn<ResumoReserva, String> colPacote;
    @FXML private TableColumn<ResumoReserva, String> colPeriodo;
    @FXML private TableColumn<ResumoReserva, String> colViajantes;
    @FXML private TableColumn<ResumoReserva, String> colSituacao;

    @FXML private Label lblSituacaoReserva;
    @FXML private Label lblCodigoReserva;
    @FXML private Label lblClienteTitular;
    @FXML private Label lblCpfCliente;
    @FXML private Label lblPacoteViagem;
    @FXML private Label lblPeriodo;
    @FXML private Label lblQuantidadeViajantes;
    @FXML private Label lblTotalPacote;
    @FXML private Label lblTotalPago;
    @FXML private Label lblSaldoPendente;
    @FXML private Label lblCancelamentoExistente;

    @FXML private VBox painelCancelamento;
    @FXML private ComboBox<MotivoCancelamento> cboMotivo;
    @FXML private Label lblRotuloDescricao;
    @FXML private TextArea txtDescricaoMotivo;
    @FXML private Label lblErroMotivo;
    @FXML private Label lblErroDescricao;
    @FXML private Label lblAvisoPoliticas;
    @FXML private Button btnConfirmarCancelamento;
    @FXML private Button btnNaoCancelar;

    private final ContextoAplicacao contexto;

    /** Versao da reserva no momento em que a tela a carregou (base do FA05). */
    private long versaoConhecida;

    public CancelamentoController(ContextoAplicacao contexto) {
        this.contexto = contexto;
    }

    @Override
    public void initialize(URL local, ResourceBundle recursos) {
        configurarColunas();
        Campos.formatarCpfAoSair(txtCpfCliente);

        cboMotivo.setItems(FXCollections.observableArrayList(MotivoCancelamento.values()));
        cboMotivo.valueProperty().addListener((observavel, anterior, atual) -> aoTrocarMotivo(atual));

        tableReservas.getSelectionModel().selectedItemProperty()
                .addListener((observavel, anterior, atual) -> exibirDetalhes(atual));

        // TODO: o protótipo do Figma exibe aqui um bloco "Atenção sobre Multas Operacionais"
        //  (multa de 20% para cancelamentos a menos de 30 dias da partida e reembolso
        //  estimado). Essa regra não consta em nenhum fluxo do documento de requisitos do
        //  UC07, então nada de multa/reembolso é calculado nesta versão. A equipe precisa
        //  decidir com o professor se a política vira requisito formal antes de implementar.
        lblAvisoPoliticas.setText("O cancelamento devolve as vagas ao pacote e preserva o "
                + "histórico de pagamentos da reserva.");

        limparDetalhes();
    }

    // ------------------------------------------------------------------
    // Pesquisa (UC07, passos 2 e 3)
    // ------------------------------------------------------------------

    @FXML
    private void aoConsultar(ActionEvent evento) {
        esconderMensagemPesquisa();

        Resultado<List<ResumoReserva>> resultado = contexto.getReservaController().buscar(
                txtCodigoReserva.getText(),
                txtCpfCliente.getText(),
                txtNomeCliente.getText(),
                txtCodigoPacote.getText());

        if (!resultado.isSucesso()) {
            // ERRO_VALIDACAO: nenhum critério informado. NAO_ENCONTRADO: FA01.
            tableReservas.getItems().clear();
            limparDetalhes();
            exibirMensagemPesquisa(resultado.mensagensConsolidadas());
            return;
        }

        List<ResumoReserva> encontradas = resultado.getDado().orElse(List.of());
        tableReservas.setItems(FXCollections.observableArrayList(encontradas));
        tableReservas.getSelectionModel().selectFirst();
    }

    /** Refaz a pesquisa atual mantendo a reserva em foco, quando ela ainda aparecer. */
    private void recarregarPesquisa(String codigoEmFoco) {
        aoConsultar(null);
        if (codigoEmFoco == null) {
            return;
        }
        tableReservas.getItems().stream()
                .filter(resumo -> codigoEmFoco.equals(resumo.codigo()))
                .findFirst()
                .ifPresent(resumo -> tableReservas.getSelectionModel().select(resumo));
    }

    // ------------------------------------------------------------------
    // Detalhes da reserva (UC07, passo 4)
    // ------------------------------------------------------------------

    private void exibirDetalhes(ResumoReserva resumo) {
        limparErros();
        if (resumo == null) {
            limparDetalhes();
            return;
        }

        Reserva reserva = resumo.reserva();
        versaoConhecida = reserva.getVersao();

        lblSituacaoReserva.setText(reserva.getStatus().getDescricao().toUpperCase());
        lblCodigoReserva.setText(resumo.codigo());
        lblClienteTitular.setText(resumo.nomeCliente());
        lblCpfCliente.setText(resumo.cliente() == null
                ? "-" : ValidadorCpf.formatar(resumo.cliente().getCpf()));
        lblPacoteViagem.setText(resumo.pacote() == null
                ? "-" : resumo.destino() + " (" + resumo.pacote().getCodigo() + ")");
        lblPeriodo.setText(Formatadores.formatarData(reserva.getDataInicio())
                + " a " + Formatadores.formatarData(reserva.getDataFim()));
        lblQuantidadeViajantes.setText(reserva.getQuantidadeViajantes() + " passageiro(s)");

        ResumoFinanceiro financeiro = contexto.getReservaController().resumoFinanceiro(reserva);
        lblTotalPacote.setText(Formatadores.formatarMoeda(financeiro.valorTotal()));
        lblTotalPago.setText(Formatadores.formatarMoeda(financeiro.totalPago()));
        lblSaldoPendente.setText(Formatadores.formatarMoeda(financeiro.saldoPendente()));

        // FA02 antecipado: reserva já cancelada não permite novo cancelamento,
        // e a tela mostra os dados do cancelamento anterior.
        boolean cancelada = reserva.isCancelada();
        painelCancelamento.setDisable(cancelada);
        if (cancelada) {
            exibirCancelamentoExistente(reserva);
        } else {
            lblCancelamentoExistente.setVisible(false);
            lblCancelamentoExistente.setManaged(false);
            cboMotivo.getSelectionModel().clearSelection();
            txtDescricaoMotivo.clear();
        }
    }

    private void exibirCancelamentoExistente(Reserva reserva) {
        StringBuilder texto = new StringBuilder("Reserva já cancelada em ")
                .append(Formatadores.formatarDataHora(reserva.getCancelamento() == null
                        ? null : reserva.getCancelamento().getDataHora()));
        if (reserva.getCancelamento() != null) {
            texto.append(" por ")
                    .append(reserva.getCancelamento().getUsuarioResponsavel())
                    .append(". Motivo: ")
                    .append(reserva.getCancelamento().getMotivo() == null
                            ? "não informado" : reserva.getCancelamento().getMotivo().getDescricao());
            if (reserva.getCancelamento().getDescricao() != null) {
                texto.append(" - ").append(reserva.getCancelamento().getDescricao());
            }
            texto.append(". Vagas devolvidas: ")
                    .append(reserva.getCancelamento().getVagasDevolvidas())
                    .append(".");
        }
        lblCancelamentoExistente.setText(texto.toString());
        lblCancelamentoExistente.setVisible(true);
        lblCancelamentoExistente.setManaged(true);
    }

    // ------------------------------------------------------------------
    // Cancelamento (UC07, passos 5 a 10)
    // ------------------------------------------------------------------

    @FXML
    private void aoConfirmarCancelamento(ActionEvent evento) {
        limparErros();
        ResumoReserva selecionada = tableReservas.getSelectionModel().getSelectedItem();
        if (selecionada == null) {
            Alertas.aviso("Selecione a reserva que deseja cancelar.");
            return;
        }

        Reserva reserva = selecionada.reserva();
        int vagas = contexto.getReservaController().vagasQueSeraoDevolvidas(reserva);

        // Passo 7: confirmação informando quantas vagas voltam ao pacote.
        boolean confirmado = Alertas.confirmar("Confirmar cancelamento",
                "Cancelar a reserva " + selecionada.codigo() + " de " + selecionada.nomeCliente()
                        + "?\n\n" + vagas + " vaga(s) voltarão para o pacote "
                        + (selecionada.pacote() == null ? "" : selecionada.pacote().getCodigo())
                        + ". O histórico de pagamentos é mantido.");

        // FA04 - o funcionário desiste: nada é alterado.
        if (!confirmado) {
            return;
        }

        Resultado<ResumoReserva> resultado = contexto.getReservaController().cancelar(
                reserva.getId(),
                cboMotivo.getValue(),
                txtDescricaoMotivo.getText(),
                contexto.getAutenticacaoController().nomeDoUsuarioAutenticado(),
                versaoConhecida);

        if (resultado.isSucesso()) {
            Alertas.sucesso(resultado.getMensagem());
            recarregarPesquisa(selecionada.codigo());
            return;
        }

        if (resultado.getStatus() == StatusResultado.ERRO_VALIDACAO) {
            // FA03 - motivo não selecionado ou "Outro" sem descrição.
            Alertas.marcarErro(cboMotivo, lblErroMotivo, resultado, "motivo");
            Alertas.marcarErro(txtDescricaoMotivo, lblErroDescricao, resultado, "descricao");
            return;
        }

        // FA02 (já cancelada), FA05 (alterada por outro processo), FA06 (falha ao persistir)
        // e reserva inexistente: a tela avisa e recarrega os dados atuais.
        Alertas.aviso(resultado.mensagensConsolidadas());
        recarregarPesquisa(selecionada.codigo());
    }

    /** FA04 pelo botão: descarta o preenchimento sem alterar a reserva. */
    @FXML
    private void aoNaoCancelar(ActionEvent evento) {
        limparErros();
        cboMotivo.getSelectionModel().clearSelection();
        txtDescricaoMotivo.clear();
        tableReservas.getSelectionModel().clearSelection();
        limparDetalhes();
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private void aoTrocarMotivo(MotivoCancelamento motivo) {
        boolean obrigatoria = motivo != null && motivo.isExigeDescricao();
        lblRotuloDescricao.setText(obrigatoria
                ? "Descrição / Justificativa detalhada (obrigatória)"
                : "Descrição / Justificativa detalhada (opcional)");
    }

    private void configurarColunas() {
        colCodigo.setCellValueFactory(celula -> texto(celula.getValue().codigo()));
        colCliente.setCellValueFactory(celula -> texto(celula.getValue().nomeCliente()));
        colPacote.setCellValueFactory(celula -> texto(celula.getValue().pacote() == null
                ? "-" : celula.getValue().pacote().getCodigo() + " - " + celula.getValue().destino()));
        colPeriodo.setCellValueFactory(celula -> texto(
                Formatadores.formatarData(celula.getValue().reserva().getDataInicio())
                        + " a " + Formatadores.formatarData(celula.getValue().reserva().getDataFim())));
        colViajantes.setCellValueFactory(celula ->
                texto(String.valueOf(celula.getValue().reserva().getQuantidadeViajantes())));
        colSituacao.setCellValueFactory(celula ->
                texto(celula.getValue().reserva().getStatus().getDescricao()));
    }

    private static ObservableValue<String> texto(String valor) {
        return new SimpleStringProperty(valor == null ? "" : valor);
    }

    private void limparDetalhes() {
        versaoConhecida = 0L;
        lblSituacaoReserva.setText("-");
        lblCodigoReserva.setText("-");
        lblClienteTitular.setText("-");
        lblCpfCliente.setText("-");
        lblPacoteViagem.setText("-");
        lblPeriodo.setText("-");
        lblQuantidadeViajantes.setText("-");
        lblTotalPacote.setText("-");
        lblTotalPago.setText("-");
        lblSaldoPendente.setText("-");
        lblCancelamentoExistente.setVisible(false);
        lblCancelamentoExistente.setManaged(false);
        cboMotivo.getSelectionModel().clearSelection();
        txtDescricaoMotivo.clear();
        painelCancelamento.setDisable(true);
        limparErros();
    }

    private void exibirMensagemPesquisa(String mensagem) {
        lblMensagemPesquisa.setText(mensagem);
        lblMensagemPesquisa.setVisible(true);
        lblMensagemPesquisa.setManaged(true);
    }

    private void esconderMensagemPesquisa() {
        lblMensagemPesquisa.setText("");
        lblMensagemPesquisa.setVisible(false);
        lblMensagemPesquisa.setManaged(false);
    }

    private void limparErros() {
        Alertas.limparErro(cboMotivo, lblErroMotivo);
        Alertas.limparErro(txtDescricaoMotivo, lblErroDescricao);
    }
}
