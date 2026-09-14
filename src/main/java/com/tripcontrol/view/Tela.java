package com.tripcontrol.view;

/**
 * Catalogo das telas do sistema: arquivo FXML, titulo e subtitulo exibidos
 * no cabecalho, seguindo os textos do prototipo.
 */
public enum Tela {

    LOGIN("/fxml/login.fxml", "Entre na sua conta",
            "Use suas credenciais para acessar o painel TripControl."),

    PACOTES("/fxml/pacote-form.fxml", "Cadastrar Novo Pacote de Viagem",
            "Crie e publique novos roteiros no catalogo geral"),

    CLIENTES("/fxml/cliente-form.fxml", "Cadastrar Cliente",
            "Gerencie as fichas cadastrais e preferencias de viajantes"),

    RESERVAS("/fxml/reserva-form.fxml", "Registrar Nova Reserva",
            "Emita reservas de pacotes turisticos para clientes cadastrados"),

    VAGAS("/fxml/em-construcao.fxml", "Controlar Ocupacao de Vagas",
            "Monitore e altere a capacidade operacional de cada pacote de viagem"),

    PAGAMENTOS("/fxml/em-construcao.fxml", "Gerenciamento Financeiro",
            "Controle e de baixa em parcelas de pagamentos de clientes"),

    ITINERARIOS("/fxml/em-construcao.fxml", "Montar Itinerario Detalhado",
            "Estruture o cronograma dia a dia para o pacote selecionado"),

    CANCELAMENTOS("/fxml/em-construcao.fxml", "Gestao de Cancelamentos",
            "Cancele e reembolse reservas ativas conforme politicas vigentes"),

    RELATORIOS("/fxml/em-construcao.fxml", "Relatorios Operacionais e Financeiros",
            "Gere listagens consolidadas e relatorios estatisticos para exportacao");

    private final String arquivoFxml;
    private final String titulo;
    private final String subtitulo;

    Tela(String arquivoFxml, String titulo, String subtitulo) {
        this.arquivoFxml = arquivoFxml;
        this.titulo = titulo;
        this.subtitulo = subtitulo;
    }

    public String getArquivoFxml() {
        return arquivoFxml;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getSubtitulo() {
        return subtitulo;
    }
}
