package com.tripcontrol.view;

import com.tripcontrol.util.Formatadores;
import com.tripcontrol.util.ValidadorCpf;
import com.tripcontrol.util.ValidadorTelefone;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.time.LocalDate;

/**
 * Ajustes reutilizaveis de componentes de formulario: mascara de data no padrao
 * brasileiro, campos exclusivamente numericos e formatacao de CPF/telefone ao
 * sair do campo. Mantem as telas consistentes (RNF01) sem repetir codigo.
 */
public final class Campos {

    private Campos() {
    }

    /** Faz o {@link DatePicker} aceitar e exibir datas no formato dd/MM/aaaa. */
    public static void configurarData(DatePicker campo) {
        campo.setPromptText("DD/MM/AAAA");
        campo.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate data) {
                return Formatadores.formatarData(data);
            }

            @Override
            public LocalDate fromString(String texto) {
                return Formatadores.lerData(texto).orElse(null);
            }
        });
    }

    /** @return o texto digitado no campo de data, ou vazio quando nada foi informado. */
    public static String textoDaData(DatePicker campo) {
        if (campo.getValue() != null) {
            return Formatadores.formatarData(campo.getValue());
        }
        String digitado = campo.getEditor().getText();
        return digitado == null ? "" : digitado.trim();
    }

    /** Restringe a digitacao a numeros inteiros. */
    public static void somenteNumeros(TextField campo) {
        campo.textProperty().addListener((observavel, anterior, atual) -> {
            if (atual != null && !atual.matches("\\d*")) {
                campo.setText(atual.replaceAll("\\D", ""));
            }
        });
    }

    /** Aplica a mascara de CPF quando o campo perde o foco. */
    public static void formatarCpfAoSair(TextField campo) {
        campo.focusedProperty().addListener((observavel, tinhaFoco, temFoco) -> {
            if (!temFoco) {
                campo.setText(ValidadorCpf.formatar(campo.getText()));
            }
        });
    }

    /** Aplica a mascara de telefone quando o campo perde o foco. */
    public static void formatarTelefoneAoSair(TextField campo) {
        campo.focusedProperty().addListener((observavel, tinhaFoco, temFoco) -> {
            if (!temFoco) {
                campo.setText(ValidadorTelefone.formatar(campo.getText()));
            }
        });
    }
}
