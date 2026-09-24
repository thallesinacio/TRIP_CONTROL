package com.tripcontrol.model;

import java.util.List;

/**
 * Os cinco relatorios do UC08 (passo 2).
 *
 * <p>Os nomes seguem o documento de requisitos, e nao os rotulos do prototipo
 * ("Fluxo de Caixa e Recebimentos", "Ocupacao Media de Vagas"): manter o nome do
 * requisito e o que torna a rastreabilidade requisito -&gt; codigo -&gt; teste
 * direta na matriz da Etapa 10.</p>
 */
public enum TipoRelatorio {

    RESERVAS("Reservas",
            List.of("Reserva", "Cliente", "Pacote", "Período", "Viajantes", "Situação")),

    PAGAMENTOS("Pagamentos",
            List.of("Reserva", "Valor Total", "Total Pago", "Saldo Pendente",
                    "Vencimentos", "Situação Financeira")),

    PACOTES_MAIS_PROCURADOS("Pacotes Mais Procurados",
            List.of("Pacote", "Destino", "Reservas", "Viajantes")),

    CLIENTES("Clientes",
            List.of("Nome", "CPF", "Contato", "Preferências", "Viagens Concluídas")),

    OCUPACAO_DOS_PACOTES("Ocupação dos Pacotes",
            List.of("Pacote", "Capacidade Total", "Vagas Ocupadas", "Vagas Disponíveis",
                    "% de Ocupação", "Situação"));

    private final String descricao;
    private final List<String> colunas;

    TipoRelatorio(String descricao, List<String> colunas) {
        this.descricao = descricao;
        this.colunas = colunas;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Cabecalho da tabela, na ordem do passo 7 do UC08. */
    public List<String> getColunas() {
        return colunas;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
