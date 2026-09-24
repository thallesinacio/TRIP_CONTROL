package com.tripcontrol.repository.relatorio;

/**
 * Consultas do UC08 - Gerar Relatorios.
 *
 * <p>E uma camada propria, separada dos repositorios de entidade, por dois
 * motivos: relatorio e leitura agregada que atravessa varias tabelas (nao pertence
 * a nenhuma entidade em particular), e e exatamente onde a diferenca entre filtrar
 * em memoria e filtrar com SQL mais aparece — somas, contagens, ranking e
 * percentuais saem do banco prontos.</p>
 *
 * <p>Tres das cinco consultas reaproveitam as views que a equipe escreveu no
 * {@code script.sql} ({@code SituacaoFinanceiraReserva} e {@code SituacaoPacote}),
 * em vez de repetir a mesma regra em SQL novo.</p>
 */
public interface RelatorioRepository {

    /**
     * @return {@code false} quando o sistema roda em modo memoria, em que a
     *         pre-condicao do UC08 ("o banco de dados relacional local deve estar
     *         disponivel") nao e satisfeita
     */
    boolean disponivel();

    DadosRelatorio reservas(FiltrosValidados filtros);

    DadosRelatorio pagamentos(FiltrosValidados filtros);

    DadosRelatorio pacotesMaisProcurados(FiltrosValidados filtros);

    DadosRelatorio clientes(FiltrosValidados filtros);

    DadosRelatorio ocupacaoDosPacotes(FiltrosValidados filtros);
}
