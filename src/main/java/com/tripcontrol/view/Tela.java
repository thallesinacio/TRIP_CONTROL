package com.tripcontrol.view;

/**
 * Catalogo das telas do sistema: arquivo FXML, titulo e subtitulo exibidos
 * no cabecalho, seguindo os textos do prototipo.
 */
public enum Tela {

    LOGIN("/fxml/login.fxml", "Entre na sua conta",
            "Use suas credenciais para acessar o painel TripControl."),

    PACOTES("/fxml/pacote-form.fxml", "Cadastrar Novo Pacote de Viagem",
            "Crie e publique novos roteiros no catálogo geral"),

    CLIENTES("/fxml/cliente-form.fxml", "Cadastrar Cliente",
            "Gerencie as fichas cadastrais e preferências de viajantes"),

    RESERVAS("/fxml/reserva-form.fxml", "Registrar Nova Reserva",
            "Emita reservas de pacotes turísticos para clientes cadastrados"),

    VAGAS("/fxml/vagas-view.fxml", "Controlar Ocupação de Vagas",
            "Monitore e altere a capacidade operacional de cada pacote de viagem"),

    PAGAMENTOS("/fxml/pagamento-view.fxml", "Gerenciamento Financeiro",
            "Controle e dê baixa em parcelas de pagamentos de clientes"),

    ITINERARIOS("/fxml/itinerario-view.fxml", "Montar Itinerário Detalhado",
            "Estruture o cronograma dia a dia para o pacote selecionado"),

    CANCELAMENTOS("/fxml/cancelamento-view.fxml", "Gestão de Cancelamentos",
            "Cancele reservas ativas conforme as políticas vigentes"),

    RELATORIOS("/fxml/em-construcao.fxml", "Relatórios Operacionais e Financeiros",
            "Gere listagens consolidadas e relatórios estatísticos para exportação");

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
