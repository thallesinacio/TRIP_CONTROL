package com.tripcontrol.view;

import com.tripcontrol.app.ContextoAplicacao;
import com.tripcontrol.controller.Resultado;
import com.tripcontrol.controller.StatusResultado;
import com.tripcontrol.controller.dto.DadosCliente;
import com.tripcontrol.controller.dto.ResumoReserva;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.util.Formatadores;
import com.tripcontrol.util.ValidadorCpf;
import com.tripcontrol.util.ValidadorTelefone;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/** Tela do UC02 - Cadastrar Clientes, incluindo os fluxos FA01 a FA03. */
public class ClienteFormController implements Initializable {

    @FXML private Label lblSecao;
    @FXML private TextField txtNome;
    @FXML private TextField txtCpf;
    @FXML private TextField txtTelefone;
    @FXML private TextField txtEmail;
    @FXML private TextField txtEndereco;
    @FXML private FlowPane painelPreferencias;
    @FXML private Button btnAdicionarPreferencia;
    @FXML private VBox listaHistorico;
    @FXML private Button btnSalvarCliente;
    @FXML private Button btnCancelarCliente;

    @FXML private Label lblErroNome;
    @FXML private Label lblErroCpf;
    @FXML private Label lblErroTelefone;
    @FXML private Label lblErroEmail;

    private final ContextoAplicacao contexto;
    private final List<String> preferencias = new ArrayList<>();

    private Long clienteEmEdicaoId;
    /** Acao executada apos salvar, usada pelo atalho vindo da reserva (UC03, FA03). */
    private Consumer<Cliente> aoConcluir;

    public ClienteFormController(ContextoAplicacao contexto) {
        this.contexto = contexto;
    }

    @Override
    public void initialize(URL local, ResourceBundle recursos) {
        Campos.formatarCpfAoSair(txtCpf);
        Campos.formatarTelefoneAoSair(txtTelefone);
        limparErros();
        atualizarPreferencias();
        atualizarHistorico(List.of());
    }

    /** Define o retorno usado quando o cadastro foi aberto a partir de uma reserva. */
    public void configurarRetorno(Consumer<Cliente> acao) {
        this.aoConcluir = acao;
        btnSalvarCliente.setText("Salvar e voltar a reserva");
    }

    /** Carrega um cliente existente na ficha para edicao (FA02). */
    public void editar(Cliente cliente) {
        clienteEmEdicaoId = cliente.getId();
        lblSecao.setText("Editando a ficha de " + cliente.getNome());
        txtNome.setText(cliente.getNome());
        txtCpf.setText(ValidadorCpf.formatar(cliente.getCpf()));
        txtTelefone.setText(ValidadorTelefone.formatar(cliente.getTelefone()));
        txtEmail.setText(cliente.getEmail());
        txtEndereco.setText(cliente.getEndereco());
        preferencias.clear();
        preferencias.addAll(cliente.getPreferencias());
        atualizarPreferencias();
        atualizarHistorico(contexto.getClienteController().historicoDeViagens(cliente.getId()));
    }

    @FXML
    private void aoSalvarCliente(ActionEvent evento) {
        limparErros();
        DadosCliente dados = new DadosCliente(txtNome.getText(), txtCpf.getText(), txtTelefone.getText(),
                txtEmail.getText(), txtEndereco.getText(), preferencias);

        Resultado<Cliente> resultado = clienteEmEdicaoId == null
                ? contexto.getClienteController().cadastrar(dados)
                : contexto.getClienteController().atualizar(clienteEmEdicaoId, dados);

        if (resultado.isSucesso()) {
            Cliente salvo = resultado.getDado().orElseThrow();
            Alertas.sucesso(resultado.getMensagem());
            if (aoConcluir != null) {
                Consumer<Cliente> retorno = aoConcluir;
                aoConcluir = null;
                retorno.accept(salvo);
                return;
            }
            limparFormulario();
            return;
        }

        if (resultado.getStatus() == StatusResultado.DUPLICIDADE) {
            Cliente existente = resultado.getDado().orElse(null);
            if (existente != null && Alertas.confirmar("Cliente ja cadastrado", resultado.getMensagem())) {
                editar(existente);
            }
            return;
        }

        if (resultado.getStatus() == StatusResultado.ERRO_VALIDACAO) {
            Alertas.marcarErro(txtNome, lblErroNome, resultado, "nome");
            Alertas.marcarErro(txtCpf, lblErroCpf, resultado, "cpf");
            Alertas.marcarErro(txtTelefone, lblErroTelefone, resultado, "telefone");
            Alertas.marcarErro(txtEmail, lblErroEmail, resultado, "email");
            return;
        }

        Alertas.erro(resultado.mensagensConsolidadas());
    }

    /** FA03 - descarta o preenchimento sem gravar. */
    @FXML
    private void aoCancelar(ActionEvent evento) {
        if (Alertas.confirmar("Cancelar cadastro",
                "Os dados preenchidos serao descartados. Deseja continuar?")) {
            limparFormulario();
        }
    }

    @FXML
    private void aoAdicionarPreferencia(ActionEvent evento) {
        TextInputDialog dialogo = new TextInputDialog();
        dialogo.setTitle("Nova preferencia");
        dialogo.setHeaderText("Preferencia de viagem");
        dialogo.setContentText("Ex.: Praia, Ecoturismo, Hoteis 5 Estrelas");
        Optional<String> resposta = dialogo.showAndWait();
        resposta.map(String::trim)
                .filter(texto -> !texto.isEmpty())
                .filter(texto -> !preferencias.contains(texto))
                .ifPresent(texto -> {
                    preferencias.add(texto);
                    atualizarPreferencias();
                });
    }

    private void atualizarPreferencias() {
        painelPreferencias.getChildren().clear();
        for (String preferencia : preferencias) {
            Label texto = new Label(preferencia);
            Button remover = new Button("✕");
            remover.getStyleClass().add("botao-remover-tag");
            remover.setOnAction(evento -> {
                preferencias.remove(preferencia);
                atualizarPreferencias();
            });
            HBox tag = new HBox(6, texto, remover);
            tag.setAlignment(Pos.CENTER_LEFT);
            tag.getStyleClass().add("tag-preferencia");
            painelPreferencias.getChildren().add(tag);
        }
        painelPreferencias.getChildren().add(btnAdicionarPreferencia);
    }

    /** Historico somente leitura, derivado das reservas do cliente. */
    private void atualizarHistorico(List<ResumoReserva> historico) {
        listaHistorico.getChildren().clear();
        if (historico.isEmpty()) {
            Label vazio = new Label("Nenhuma viagem registrada para este cliente.");
            vazio.getStyleClass().add("texto-secundario");
            listaHistorico.getChildren().add(vazio);
            return;
        }
        for (ResumoReserva resumo : historico) {
            Label destino = new Label("• " + resumo.destino());
            destino.getStyleClass().add("historico-destino");
            Region espaco = new Region();
            HBox.setHgrow(espaco, Priority.ALWAYS);
            Label situacao = new Label("Reserva #" + resumo.codigo()
                    + "  |  Status: " + resumo.reserva().getStatus().getDescricao()
                    + "  |  " + Formatadores.formatarMoeda(resumo.valorTotal()));
            situacao.getStyleClass().add("historico-status");
            HBox linha = new HBox(destino, espaco, situacao);
            linha.setAlignment(Pos.CENTER_LEFT);
            linha.getStyleClass().add("historico-linha");
            listaHistorico.getChildren().add(linha);
        }
    }

    private void limparFormulario() {
        clienteEmEdicaoId = null;
        lblSecao.setText("Ficha de Cadastro do Cliente");
        btnSalvarCliente.setText("Salvar");
        txtNome.clear();
        txtCpf.clear();
        txtTelefone.clear();
        txtEmail.clear();
        txtEndereco.clear();
        preferencias.clear();
        atualizarPreferencias();
        atualizarHistorico(List.of());
        limparErros();
        txtNome.requestFocus();
    }

    private void limparErros() {
        Alertas.limparErro(txtNome, lblErroNome);
        Alertas.limparErro(txtCpf, lblErroCpf);
        Alertas.limparErro(txtTelefone, lblErroTelefone);
        Alertas.limparErro(txtEmail, lblErroEmail);
    }
}
