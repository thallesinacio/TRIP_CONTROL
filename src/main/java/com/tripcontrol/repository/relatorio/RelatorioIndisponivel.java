package com.tripcontrol.repository.relatorio;

/**
 * Implementacao usada no modo memoria, em que os relatorios nao existem.
 *
 * <p>A pre-condicao do UC08 exige o banco disponivel. Em vez de duplicar as cinco
 * consultas agregadas em {@code Stream} — o que criaria duas fontes de verdade
 * para os mesmos numeros —, o modo memoria responde que os relatorios estao
 * indisponiveis e a tela explica isso ao funcionario. Nenhuma tela quebra.</p>
 */
public class RelatorioIndisponivel implements RelatorioRepository {

    @Override
    public boolean disponivel() {
        return false;
    }

    @Override
    public DadosRelatorio reservas(FiltrosValidados filtros) {
        throw naoDisponivel();
    }

    @Override
    public DadosRelatorio pagamentos(FiltrosValidados filtros) {
        throw naoDisponivel();
    }

    @Override
    public DadosRelatorio pacotesMaisProcurados(FiltrosValidados filtros) {
        throw naoDisponivel();
    }

    @Override
    public DadosRelatorio clientes(FiltrosValidados filtros) {
        throw naoDisponivel();
    }

    @Override
    public DadosRelatorio ocupacaoDosPacotes(FiltrosValidados filtros) {
        throw naoDisponivel();
    }

    private static IllegalStateException naoDisponivel() {
        return new IllegalStateException(
                "Relatorios exigem o banco de dados: o sistema esta no modo memoria.");
    }
}
