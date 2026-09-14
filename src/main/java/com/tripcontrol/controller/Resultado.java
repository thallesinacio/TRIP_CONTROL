package com.tripcontrol.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resposta padronizada dos Controllers para as Views.
 *
 * <p>Em vez de lancar excecoes para situacoes previstas nos casos de uso
 * (campo invalido, duplicidade, concorrencia), o Controller devolve um
 * {@code Resultado}: a tela consulta o {@link StatusResultado} e decide se
 * exibe confirmacao, destaca campos ou oferece abrir o registro existente.</p>
 *
 * @param <T> tipo do dado devolvido em caso de sucesso
 */
public final class Resultado<T> {

    private final StatusResultado status;
    private final String mensagem;
    private final T dado;
    private final List<ErroValidacao> erros;

    private Resultado(StatusResultado status, String mensagem, T dado, List<ErroValidacao> erros) {
        this.status = status;
        this.mensagem = mensagem;
        this.dado = dado;
        this.erros = erros == null ? List.of() : List.copyOf(erros);
    }

    public static <T> Resultado<T> sucesso(T dado, String mensagem) {
        return new Resultado<>(StatusResultado.SUCESSO, mensagem, dado, List.of());
    }

    public static <T> Resultado<T> erroValidacao(List<ErroValidacao> erros) {
        return new Resultado<>(StatusResultado.ERRO_VALIDACAO,
                "Corrija os campos destacados para continuar.", null, erros);
    }

    public static <T> Resultado<T> erroValidacao(String campo, String mensagem) {
        List<ErroValidacao> lista = new ArrayList<>();
        lista.add(new ErroValidacao(campo, mensagem));
        return erroValidacao(lista);
    }

    /** Registro equivalente ja cadastrado; {@code existente} volta para a tela oferecer edicao. */
    public static <T> Resultado<T> duplicidade(T existente, String mensagem) {
        return new Resultado<>(StatusResultado.DUPLICIDADE, mensagem, existente, List.of());
    }

    public static <T> Resultado<T> naoEncontrado(String mensagem) {
        return new Resultado<>(StatusResultado.NAO_ENCONTRADO, mensagem, null, List.of());
    }

    public static <T> Resultado<T> conflitoConcorrencia(T dadoAtualizado, String mensagem) {
        return new Resultado<>(StatusResultado.CONFLITO_CONCORRENCIA, mensagem, dadoAtualizado, List.of());
    }

    public static <T> Resultado<T> operacaoBloqueada(String mensagem) {
        return new Resultado<>(StatusResultado.OPERACAO_BLOQUEADA, mensagem, null, List.of());
    }

    public boolean isSucesso() {
        return status == StatusResultado.SUCESSO;
    }

    public StatusResultado getStatus() {
        return status;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Optional<T> getDado() {
        return Optional.ofNullable(dado);
    }

    public List<ErroValidacao> getErros() {
        return Collections.unmodifiableList(erros);
    }

    /** @return mensagem de erro do campo informado, se houver. */
    public Optional<String> mensagemDoCampo(String campo) {
        return erros.stream()
                .filter(erro -> erro.campo().equals(campo))
                .map(ErroValidacao::mensagem)
                .findFirst();
    }

    /** @return todas as mensagens de erro em uma unica string, uma por linha. */
    public String mensagensConsolidadas() {
        if (erros.isEmpty()) {
            return mensagem == null ? "" : mensagem;
        }
        return erros.stream()
                .map(erro -> "• " + erro.mensagem())
                .collect(Collectors.joining("\n"));
    }

    @Override
    public String toString() {
        return "Resultado{" + status + ", mensagem='" + mensagem + "', erros=" + erros.size() + '}';
    }
}
