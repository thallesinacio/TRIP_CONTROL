package com.tripcontrol.util;

/**
 * Falha ao preparar o banco de dados no start da aplicacao (servidor fora do ar,
 * credenciais erradas, banco inexistente).
 *
 * <p>Existe para que a tela possa avisar em portugues e sugerir rodar em modo
 * memoria, em vez de mostrar uma pilha de erro do driver.</p>
 */
public class ConexaoIndisponivelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConexaoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
