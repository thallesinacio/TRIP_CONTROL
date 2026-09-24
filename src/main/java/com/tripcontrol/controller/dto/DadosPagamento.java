package com.tripcontrol.controller.dto;

import com.tripcontrol.model.FormaPagamento;

/**
 * Dados digitados no painel "Registrar Recebimento" (UC05, passo 6).
 *
 * <p>Os campos chegam como texto, exatamente como o funcionario digitou: a
 * conversao e a critica ficam no Controller, o que permite testar o FA03 sem
 * subir a interface grafica. A forma de pagamento e a excecao porque vem de um
 * combo de valores fixos.</p>
 *
 * <p>Nao existe campo de data de vencimento: pela decisao A da equipe, cada
 * pagamento quita exatamente uma parcela, entao o vencimento e o da parcela
 * escolhida e a tela o exibe somente para leitura.</p>
 */
public record DadosPagamento(String valorRecebido,
                             String dataRecebimento,
                             FormaPagamento formaPagamento,
                             String numeroParcela,
                             String identificadorTransacao,
                             String observacao) {
}
