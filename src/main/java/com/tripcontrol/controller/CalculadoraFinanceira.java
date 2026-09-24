package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.LinhaParcela;
import com.tripcontrol.controller.dto.ResumoFinanceiro;
import com.tripcontrol.model.Pagamento;
import com.tripcontrol.model.Parcela;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.model.SituacaoFinanceira;
import com.tripcontrol.model.StatusParcela;
import com.tripcontrol.repository.PagamentoRepository;
import com.tripcontrol.repository.ParcelaRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consolidacao financeira de uma reserva a partir das parcelas e dos pagamentos.
 *
 * <p>Existe para que o UC05 (que registra os recebimentos) e o UC07 (que mostra
 * total pago e saldo pendente antes de cancelar) cheguem sempre ao mesmo numero,
 * sem duplicar a regra em dois Controllers. <strong>Nada aqui e gravado:</strong>
 * total pago, saldo, status de parcela e situacao financeira sao todos derivados
 * no momento da consulta, como exige a Etapa 4 (reserva no PostgreSQL, pagamentos
 * ainda em memoria).</p>
 */
public class CalculadoraFinanceira {

    private final ParcelaRepository parcelaRepository;
    private final PagamentoRepository pagamentoRepository;
    private final Clock relogio;

    public CalculadoraFinanceira(ParcelaRepository parcelaRepository,
                                 PagamentoRepository pagamentoRepository) {
        this(parcelaRepository, pagamentoRepository, Clock.systemDefaultZone());
    }

    /** Construtor com relogio injetavel: usado pelos testes para fixar "hoje". */
    public CalculadoraFinanceira(ParcelaRepository parcelaRepository,
                                 PagamentoRepository pagamentoRepository,
                                 Clock relogio) {
        this.parcelaRepository = parcelaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.relogio = relogio;
    }

    public LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    /**
     * Plano de parcelas da reserva em ordem, com o pagamento vinculado e o status
     * ja calculado (UC05, passo 4).
     */
    public List<LinhaParcela> parcelasDe(Long reservaId) {
        Map<Integer, Pagamento> pagamentosPorParcela = pagamentoRepository.buscarPorReserva(reservaId).stream()
                .filter(pagamento -> pagamento.getNumeroParcela() != null)
                .collect(Collectors.toMap(Pagamento::getNumeroParcela, Function.identity(),
                        (primeiro, segundo) -> primeiro));

        LocalDate referencia = hoje();
        return parcelaRepository.buscarPorReserva(reservaId).stream()
                .sorted(Comparator.comparingInt(Parcela::getNumero))
                .map(parcela -> new LinhaParcela(parcela,
                        pagamentosPorParcela.get(parcela.getNumero()),
                        statusDe(parcela, referencia)))
                .toList();
    }

    /**
     * Status da parcela derivado das datas, e nao do campo gravado: uma parcela
     * pendente vira "Atrasada" sozinha quando o vencimento passa, sem ninguem
     * precisar reescrever a linha.
     */
    public StatusParcela statusDe(Parcela parcela, LocalDate referencia) {
        if (parcela.getDataPagamento() != null || parcela.getStatus() == StatusParcela.QUITADA) {
            return StatusParcela.QUITADA;
        }
        return parcela.estaVencida(referencia) ? StatusParcela.ATRASADA : StatusParcela.PENDENTE;
    }

    /** Soma dos recebimentos ja registrados para a reserva. */
    public BigDecimal totalPago(Long reservaId) {
        return pagamentoRepository.buscarPorReserva(reservaId).stream()
                .map(Pagamento::getValor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** @return {@code true} se existe parcela vencida e ainda nao quitada (situacao Atrasada). */
    public boolean existeParcelaVencidaEmAberto(Long reservaId) {
        LocalDate referencia = hoje();
        return parcelaRepository.buscarPorReserva(reservaId).stream()
                .anyMatch(parcela -> statusDe(parcela, referencia) == StatusParcela.ATRASADA);
    }

    /**
     * Total pago, saldo pendente e situacao financeira da reserva
     * (UC05 passo 9; UC07 passo 4).
     */
    public ResumoFinanceiro resumoDe(Reserva reserva) {
        BigDecimal valorTotal = reserva.getValorTotal() == null ? BigDecimal.ZERO : reserva.getValorTotal();
        BigDecimal totalPago = totalPago(reserva.getId());
        BigDecimal saldo = valorTotal.subtract(totalPago).max(BigDecimal.ZERO);
        SituacaoFinanceira situacao = SituacaoFinanceira.calcular(valorTotal, totalPago,
                existeParcelaVencidaEmAberto(reserva.getId()));
        return new ResumoFinanceiro(valorTotal, totalPago, saldo, situacao);
    }

    /**
     * Marca de concorrencia do FA05 do UC05: quantos pagamentos a reserva ja tem.
     *
     * <p>A tela guarda este numero ao carregar e o devolve na gravacao. Se outro
     * processo registrar um pagamento no meio do caminho, o numero muda e a
     * gravacao e recusada — o mesmo papel que a coluna {@code versao} cumpre no
     * UC04 e no UC07, adaptado ao repositorio em memoria.</p>
     */
    public long versaoFinanceira(Long reservaId) {
        return pagamentoRepository.buscarPorReserva(reservaId).size();
    }
}
