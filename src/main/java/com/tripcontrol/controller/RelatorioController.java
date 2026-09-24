package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.FiltrosRelatorio;
import com.tripcontrol.controller.dto.Relatorio;
import com.tripcontrol.controller.dto.TotalRelatorio;
import com.tripcontrol.model.CriterioRanking;
import com.tripcontrol.model.TipoRelatorio;
import com.tripcontrol.repository.relatorio.DadosRelatorio;
import com.tripcontrol.repository.relatorio.FiltrosValidados;
import com.tripcontrol.repository.relatorio.RelatorioRepository;
import com.tripcontrol.util.ExportadorCsv;
import com.tripcontrol.util.ExportadorPdf;
import com.tripcontrol.util.Formatadores;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Regras de aplicacao do UC08 - Gerar Relatorios.
 *
 * <p>A divisao de trabalho e clara: aqui ficam a critica dos filtros (FA02), a
 * montagem do relatorio e o tratamento de falha; o SQL fica em
 * {@link RelatorioRepository}; e a gravacao dos arquivos em
 * {@link ExportadorCsv} e {@link ExportadorPdf}. Nenhuma consulta e executada antes
 * de os filtros passarem — e exatamente o que o passo 5.2 do FA02 exige.</p>
 */
public class RelatorioController {

    private final RelatorioRepository relatorioRepository;
    private final Clock relogio;

    public RelatorioController(RelatorioRepository relatorioRepository) {
        this(relatorioRepository, Clock.systemDefaultZone());
    }

    /** Construtor com relogio injetavel: os testes fixam a data de geracao. */
    public RelatorioController(RelatorioRepository relatorioRepository, Clock relogio) {
        this.relatorioRepository = relatorioRepository;
        this.relogio = relogio;
    }

    public List<TipoRelatorio> tiposDisponiveis() {
        return List.of(TipoRelatorio.values());
    }

    /**
     * @return {@code false} no modo memoria, em que a pre-condicao do UC08
     *         ("o banco de dados relacional local deve estar disponivel") nao vale
     */
    public boolean isDisponivel() {
        return relatorioRepository.disponivel();
    }

    public String motivoDeIndisponibilidade() {
        return "Os relatórios consultam o banco de dados e não estão disponíveis no modo "
                + "memória. Ajuste \"repositorio.tipo=jdbc\" em config/aplicacao.properties "
                + "e reinicie o sistema para gerá-los.";
    }

    // ------------------------------------------------------------------
    // Geracao (UC08, passos 5 a 8)
    // ------------------------------------------------------------------

    /**
     * Valida os filtros, consulta o banco e monta o relatorio.
     *
     * @return SUCESSO com o relatorio; ERRO_VALIDACAO no FA01 (tipo nao escolhido) e
     *         no FA02 (filtros invalidos); NAO_ENCONTRADO no FA03;
     *         OPERACAO_BLOQUEADA no FA04 (falha na consulta) e no modo memoria
     */
    public Resultado<Relatorio> gerar(TipoRelatorio tipo, FiltrosRelatorio filtros) {
        // FA01 - o funcionario clicou em Gerar sem escolher o tipo.
        if (tipo == null) {
            return Resultado.erroValidacao("tipo", "Selecione o tipo de relatório.");
        }
        if (!relatorioRepository.disponivel()) {
            return Resultado.operacaoBloqueada(motivoDeIndisponibilidade());
        }

        FiltrosRelatorio informados = filtros == null ? FiltrosRelatorio.vazios() : filtros;

        // FA02 - nenhuma consulta roda antes daqui.
        List<ErroValidacao> erros = validarFiltros(tipo, informados);
        if (!erros.isEmpty()) {
            return Resultado.erroValidacao(erros);
        }

        FiltrosValidados validados = converter(tipo, informados);

        DadosRelatorio dados;
        try {
            dados = consultar(tipo, validados);
        } catch (RuntimeException falha) {
            // FA04 - a tela mantem os filtros e nao exibe resultado incompleto.
            return Resultado.operacaoBloqueada(
                    "Não foi possível consultar o banco de dados para gerar o relatório de "
                            + tipo.getDescricao() + ". Os filtros foram mantidos: tente novamente.");
        }

        Relatorio relatorio = new Relatorio(tipo, tipo.getColunas(), dados.linhas(),
                converterTotais(dados.totais()), LocalDateTime.now(relogio),
                descreverFiltros(tipo, informados));

        // FA03 - nenhum registro para os criterios informados.
        if (relatorio.isVazio()) {
            return Resultado.naoEncontrado("Nenhum dado encontrado para os filtros informados. "
                    + "Ajuste o período e os critérios, ou escolha outro relatório.");
        }

        return Resultado.sucesso(relatorio, relatorio.quantidadeDeRegistros()
                + " registro(s) encontrados em " + Formatadores.formatarDataHora(relatorio.geradoEm()) + ".");
    }

    private DadosRelatorio consultar(TipoRelatorio tipo, FiltrosValidados filtros) {
        return switch (tipo) {
            case RESERVAS -> relatorioRepository.reservas(filtros);
            case PAGAMENTOS -> relatorioRepository.pagamentos(filtros);
            case PACOTES_MAIS_PROCURADOS -> relatorioRepository.pacotesMaisProcurados(filtros);
            case CLIENTES -> relatorioRepository.clientes(filtros);
            case OCUPACAO_DOS_PACOTES -> relatorioRepository.ocupacaoDosPacotes(filtros);
        };
    }

    // ------------------------------------------------------------------
    // Exportacao e impressao (UC08, passos 9 e 10)
    // ------------------------------------------------------------------

    /**
     * Grava o relatorio em CSV.
     *
     * @return SUCESSO com o caminho usado; OPERACAO_BLOQUEADA no FA05, com a causa
     */
    public Resultado<Path> exportarCsv(Relatorio relatorio, Path destino) {
        return exportar(relatorio, destino, "csv",
                () -> ExportadorCsv.exportar(relatorio, destino));
    }

    /** Grava o relatorio em PDF; mesmos desfechos de {@link #exportarCsv}. */
    public Resultado<Path> exportarPdf(Relatorio relatorio, Path destino) {
        return exportar(relatorio, destino, "pdf",
                () -> ExportadorPdf.exportar(relatorio, destino));
    }

    /** Operacao de escrita que pode falhar por causa do sistema de arquivos. */
    @FunctionalInterface
    private interface Gravacao {
        void executar() throws IOException;
    }

    private Resultado<Path> exportar(Relatorio relatorio, Path destino,
                                     String formato, Gravacao gravacao) {
        if (relatorio == null || relatorio.isVazio()) {
            return Resultado.operacaoBloqueada("Gere um relatório com dados antes de exportar.");
        }
        if (destino == null) {
            return Resultado.erroValidacao("destino", "Escolha a pasta e o nome do arquivo.");
        }

        try {
            gravacao.executar();
        } catch (IOException | RuntimeException falha) {
            // FA05 - pasta inexistente, sem permissao de escrita, disco cheio.
            return Resultado.operacaoBloqueada("Não foi possível gravar o arquivo "
                    + formato.toUpperCase(Formatadores.BRASIL) + " em " + destino
                    + ": " + causaLegivel(falha)
                    + ". A prévia e os filtros do relatório foram mantidos.");
        }

        return Resultado.sucesso(destino, "Relatório de " + relatorio.tipo().getDescricao()
                + " exportado em " + formato.toUpperCase(Formatadores.BRASIL) + " para " + destino + ".");
    }

    /** Traduz a excecao do sistema de arquivos para algo que o funcionario entenda. */
    private static String causaLegivel(Exception falha) {
        String mensagem = falha.getMessage() == null ? "" : falha.getMessage().toLowerCase();
        if (falha instanceof java.nio.file.AccessDeniedException || mensagem.contains("permission")
                || mensagem.contains("denied") || mensagem.contains("acesso")) {
            return "sem permissão de escrita na pasta escolhida";
        }
        if (falha instanceof java.nio.file.NoSuchFileException
                || mensagem.contains("no such file") || mensagem.contains("não encontrado")) {
            return "a pasta informada não existe";
        }
        if (mensagem.contains("no space") || mensagem.contains("espaço")) {
            return "espaço insuficiente no disco";
        }
        if (falha instanceof java.nio.file.FileSystemException
                && mensagem.contains("read-only")) {
            return "a pasta escolhida é somente leitura";
        }
        return falha.getMessage() == null ? falha.getClass().getSimpleName() : falha.getMessage();
    }

    // ------------------------------------------------------------------
    // FA02 - critica dos filtros
    // ------------------------------------------------------------------

    private List<ErroValidacao> validarFiltros(TipoRelatorio tipo, FiltrosRelatorio filtros) {
        List<ErroValidacao> erros = new ArrayList<>();

        Optional<LocalDate> inicio = lerDataOpcional(filtros.dataInicio(), "dataInicio", erros);
        Optional<LocalDate> fim = lerDataOpcional(filtros.dataFim(), "dataFim", erros);
        if (inicio.isPresent() && fim.isPresent() && fim.get().isBefore(inicio.get())) {
            erros.add(new ErroValidacao("dataFim",
                    "A data final não pode ser anterior à data inicial."));
        }

        if (tipo == TipoRelatorio.PACOTES_MAIS_PROCURADOS) {
            // Unico relatorio com filtro obrigatorio: sem tamanho e sem critério nao
            // existe ranking a montar.
            Optional<Integer> posicoes = Formatadores.lerInteiro(filtros.posicoesRanking());
            if (Formatadores.textoOuNulo(filtros.posicoesRanking()) == null) {
                erros.add(new ErroValidacao("posicoesRanking",
                        "Informe quantas posições do ranking devem aparecer."));
            } else if (posicoes.isEmpty()) {
                erros.add(new ErroValidacao("posicoesRanking",
                        "As posições do ranking devem ser um número inteiro."));
            } else if (posicoes.get() < 1) {
                erros.add(new ErroValidacao("posicoesRanking",
                        "O ranking deve ter ao menos uma posição."));
            }
            if (filtros.criterioRanking() == null) {
                erros.add(new ErroValidacao("criterioRanking",
                        "Escolha o critério do ranking: número de reservas ou de viajantes."));
            }
        }

        if (tipo == TipoRelatorio.OCUPACAO_DOS_PACOTES
                && Formatadores.textoOuNulo(filtros.percentualMinimoOcupacao()) != null) {
            Optional<BigDecimal> percentual =
                    Formatadores.lerValorMonetario(filtros.percentualMinimoOcupacao());
            if (percentual.isEmpty()) {
                erros.add(new ErroValidacao("percentualMinimoOcupacao",
                        "O percentual mínimo deve ser um número."));
            } else if (percentual.get().compareTo(BigDecimal.ZERO) < 0
                    || percentual.get().compareTo(new BigDecimal("100")) > 0) {
                erros.add(new ErroValidacao("percentualMinimoOcupacao",
                        "O percentual mínimo de ocupação deve estar entre 0 e 100."));
            }
        }

        if (tipo == TipoRelatorio.CLIENTES
                && Formatadores.textoOuNulo(filtros.minimoViagensConcluidas()) != null) {
            Optional<Integer> minimo = Formatadores.lerInteiro(filtros.minimoViagensConcluidas());
            if (minimo.isEmpty()) {
                erros.add(new ErroValidacao("minimoViagensConcluidas",
                        "A quantidade mínima de viagens concluídas deve ser um número inteiro."));
            } else if (minimo.get() < 0) {
                erros.add(new ErroValidacao("minimoViagensConcluidas",
                        "A quantidade mínima de viagens concluídas não pode ser negativa."));
            }
        }

        return erros;
    }

    /** Data em branco significa "sem filtro"; data preenchida tem de ser valida. */
    private static Optional<LocalDate> lerDataOpcional(String texto, String campo,
                                                       List<ErroValidacao> erros) {
        if (Formatadores.textoOuNulo(texto) == null) {
            return Optional.empty();
        }
        Optional<LocalDate> data = Formatadores.lerData(texto);
        if (data.isEmpty()) {
            erros.add(new ErroValidacao(campo, "Informe a data no formato DD/MM/AAAA."));
        }
        return data;
    }

    // ------------------------------------------------------------------
    // Conversao e apoio
    // ------------------------------------------------------------------

    private FiltrosValidados converter(TipoRelatorio tipo, FiltrosRelatorio filtros) {
        return new FiltrosValidados(
                Formatadores.lerData(filtros.dataInicio()).orElse(null),
                Formatadores.lerData(filtros.dataFim()).orElse(null),
                filtros.situacaoReserva() == null ? null : filtros.situacaoReserva().getDescricao(),
                Formatadores.textoOuNulo(filtros.codigoPacote()),
                soDigitosOuNulo(filtros.cpfCliente()),
                filtros.situacaoFinanceira() == null
                        ? null : filtros.situacaoFinanceira().getDescricao(),
                filtros.formaPagamento() == null ? null : filtros.formaPagamento().name(),
                Formatadores.lerInteiro(filtros.posicoesRanking()).orElse(null),
                filtros.criterioRanking() == null
                        ? CriterioRanking.NUMERO_DE_RESERVAS : filtros.criterioRanking(),
                Formatadores.textoOuNulo(filtros.nomeCliente()),
                Formatadores.textoOuNulo(filtros.preferencia()),
                Formatadores.lerInteiro(filtros.minimoViagensConcluidas()).orElse(null),
                Formatadores.textoOuNulo(filtros.destino()),
                filtros.situacaoPacote() == null ? null : filtros.situacaoPacote().getDescricao(),
                Formatadores.lerValorMonetario(filtros.percentualMinimoOcupacao()).orElse(null));
    }

    /**
     * CPF sem mascara, ou nulo quando o campo esta vazio.
     *
     * <p>Importa a diferenca: {@code ValidadorCpf.apenasDigitos(null)} devolve string
     * vazia, e uma string vazia como filtro nao casaria com nenhum CPF do banco — o
     * relatorio viria vazio em vez de vir sem filtro de cliente.</p>
     */
    private static String soDigitosOuNulo(String cpf) {
        String limpo = Formatadores.textoOuNulo(cpf);
        return limpo == null ? null : com.tripcontrol.util.ValidadorCpf.apenasDigitos(limpo);
    }

    private static List<TotalRelatorio> converterTotais(Map<String, String> totais) {
        return totais.entrySet().stream()
                .map(entrada -> new TotalRelatorio(entrada.getKey(), entrada.getValue()))
                .toList();
    }

    /** Resumo textual dos filtros, impresso no cabecalho do relatorio e do arquivo. */
    private String descreverFiltros(TipoRelatorio tipo, FiltrosRelatorio filtros) {
        List<String> partes = new ArrayList<>();
        acrescentar(partes, rotuloDoPeriodo(tipo), periodo(filtros));
        acrescentar(partes, "Situação", filtros.situacaoReserva() == null
                ? null : filtros.situacaoReserva().getDescricao());
        acrescentar(partes, "Pacote", Formatadores.textoOuNulo(filtros.codigoPacote()));
        acrescentar(partes, "CPF", Formatadores.textoOuNulo(filtros.cpfCliente()));
        acrescentar(partes, "Situação financeira", filtros.situacaoFinanceira() == null
                ? null : filtros.situacaoFinanceira().getDescricao());
        acrescentar(partes, "Forma de pagamento", filtros.formaPagamento() == null
                ? null : filtros.formaPagamento().getDescricao());
        acrescentar(partes, "Posições", Formatadores.textoOuNulo(filtros.posicoesRanking()));
        acrescentar(partes, "Critério", filtros.criterioRanking() == null
                ? null : filtros.criterioRanking().getDescricao());
        acrescentar(partes, "Nome", Formatadores.textoOuNulo(filtros.nomeCliente()));
        acrescentar(partes, "Preferência", Formatadores.textoOuNulo(filtros.preferencia()));
        acrescentar(partes, "Mínimo de viagens concluídas",
                Formatadores.textoOuNulo(filtros.minimoViagensConcluidas()));
        acrescentar(partes, "Destino", Formatadores.textoOuNulo(filtros.destino()));
        acrescentar(partes, "Situação do pacote", filtros.situacaoPacote() == null
                ? null : filtros.situacaoPacote().getDescricao());
        acrescentar(partes, "Ocupação mínima",
                Formatadores.textoOuNulo(filtros.percentualMinimoOcupacao()));

        return partes.isEmpty() ? "nenhum filtro aplicado" : String.join(" · ", partes);
    }

    private static void acrescentar(List<String> partes, String rotulo, String valor) {
        if (valor != null && !valor.isBlank()) {
            partes.add(rotulo + ": " + valor);
        }
    }

    private static String periodo(FiltrosRelatorio filtros) {
        String inicio = Formatadores.textoOuNulo(filtros.dataInicio());
        String fim = Formatadores.textoOuNulo(filtros.dataFim());
        if (inicio == null && fim == null) {
            return null;
        }
        if (inicio != null && fim != null) {
            return inicio + " a " + fim;
        }
        return inicio == null ? "até " + fim : "a partir de " + inicio;
    }

    /** Cada relatorio filtra por um periodo diferente (UC08, passo 4). */
    private static String rotuloDoPeriodo(TipoRelatorio tipo) {
        return switch (tipo) {
            case RESERVAS -> "Período da viagem";
            case PAGAMENTOS -> "Período de recebimento";
            case PACOTES_MAIS_PROCURADOS -> "Período de registro das reservas";
            case CLIENTES -> "Período";
            case OCUPACAO_DOS_PACOTES -> "Período de início dos pacotes";
        };
    }
}
