package com.tripcontrol.repository;

/**
 * Sinaliza que o registro foi alterado por outro processo entre a leitura e a
 * gravacao: o {@code UPDATE ... WHERE id = ? AND versao = ?} nao afetou nenhuma
 * linha.
 *
 * <p>E o que sustenta o FA05 do UC07 e o FA04 do UC04 quando duas instancias da
 * aplicacao apontam para o mesmo banco.</p>
 */
public class ConflitoDeConcorrenciaException extends RepositorioException {

    private static final long serialVersionUID = 1L;

    public ConflitoDeConcorrenciaException(String mensagem) {
        super(mensagem, null);
    }
}
