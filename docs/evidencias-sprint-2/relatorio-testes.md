# Validação da Sprint 2 — TripControl

Data: 24/09/2026. Execução no Java 21, Maven 3.9.16 e PostgreSQL 16.15, em um banco de teste isolado (`tripcontrol_test`, porta 55432). As migrações Flyway foram aplicadas. Cada teste JDBC abre uma transação que é desfeita ao final.

## Resultado geral

Comando final: `TRIPCONTROL_REPOSITORIO=memoria TRIPCONTROL_TEST_DB_PORT=55432 mvn -B clean verify` (a senha do banco foi fornecida por variável de ambiente).

**186 testes passaram: 110 unitários e 76 de integração JDBC. Falhas: 0; erros: 0; ignorados: 0.** O resultado completo está em [mvn-clean-verify-final.log](mvn-clean-verify-final.log), e os nomes e resultados individuais estão em [casos-executados.csv](casos-executados.csv).

## Checklist executado

| # | Cenário | Evidência principal | Resultado |
|---|---|---|---|
| 1 | Consultar capacidade, vagas ocupadas e livres | `PacoteControllerTest.calculaVagasAPartirDasReservas`; `FluxoPagamentoJdbcIT.reservaPagamentoCancelamentoAtualizaVagas` | Passou |
| 2 | Situação Disponível, Lotado e Encerrado | `PacoteControllerTest.apresentaAsTresSituacoesDoPacote` | Passou |
| 3 | Alterar capacidade validamente | `FluxoPagamentoJdbcIT.alteraCapacidadeComValidacaoNoBanco`: alteração e histórico relidos do PostgreSQL | Passou |
| 4 | Recusar capacidade inferior às vagas ocupadas | Mesmo teste: erro no campo e capacidade/histórico preservados no banco | Passou |
| 5 | Registrar pagamento de parcela | `FluxoPagamentoJdbcIT.percorreAsSituacoesFinanceiras`; `JdbcPagamentoRepositoryIT.recebimentoQuitaAParcela` | Passou |
| 6 | Calcular total pago e saldo | `FluxoPagamentoJdbcIT.dadosSobrevivemAReleitura`; `RelatorioJdbcIT.pagamentosDoPacote` | Passou |
| 7 | Pendente, Parcialmente Paga, Quitada e Atrasada | `PagamentoControllerTest.percorreAsTresSituacoesFinanceiras`, `situacaoAtrasadaDependeDoVencimento`; `FluxoPagamentoJdbcIT.bancoEJavaConcordamSobreASituacao` | Passou |
| 8 | Pagamento inválido ou maior que saldo | `PagamentoControllerTest.fa03DadosInvalidos`, `fa03ValorForaDosLimites`, `recusaValorDiferenteDoValorDaParcela` | Passou |
| 9 | Pagamento duplicado | `FluxoPagamentoJdbcIT.fa04ParcelaDuplicada`, `fa04ComprovanteDuplicado`; `PagamentoControllerTest.fa04IdentificadorDuplicado` | Passou |
| 10 | Pagamento de reserva cancelada | `FluxoPagamentoJdbcIT.fa02ReservaCancelada` e `reservaPagamentoCancelamentoAtualizaVagas` | Passou |
| 11 | Criar itinerário válido | `FluxoItinerarioJdbcIT.montaEReabreOCronograma` | Passou |
| 12 | Ordenar itens por data e hora | `ItinerarioControllerTest.montaCronogramaCompleto`; `JdbcItinerarioRepositoryIT.gravaEReabre` | Passou |
| 13 | Recusar item fora do período | `FluxoItinerarioJdbcIT.fa03ForaDoPeriodo` | Passou |
| 14 | Recusar conflito entre itens | `FluxoItinerarioJdbcIT.fa04Conflito` | Passou |
| 15 | Recusar finalização sem itens | `FluxoItinerarioJdbcIT.fa06SemItens` | Passou |
| 16 | Cancelar reserva ativa | `ReservaCancelamentoTest.cancelaReservaAtiva`; `JdbcReservaRepositoryIT.persisteCancelamento` | Passou |
| 17 | Devolver vagas após cancelamento | `JdbcReservaRepositoryIT.cancelamentoDevolveVagas`; `FluxoPagamentoJdbcIT.reservaPagamentoCancelamentoAtualizaVagas` | Passou |
| 18 | Recusar segundo cancelamento | `ReservaCancelamentoTest.bloqueiaSegundoCancelamento` | Passou |
| 19 | Preservar reserva e vagas em falha de cancelamento | `ReservaCancelamentoTest.desfazAlteracaoQuandoPersistenciaFalha` (falha simulada no repositório) | Passou |
| 20 | Gerar os cinco relatórios | `RelatorioJdbcIT`: reservas, pagamentos, clientes, ocupação e ranking de pacotes | Passou |
| 21 | Aplicar filtros dos relatórios | `RelatorioJdbcIT`: período, pacote, cliente, situação, forma de pagamento, ranking, preferência e ocupação; `RelatorioControllerTest` valida filtros inválidos | Passou |
| 22 | Relatório sem resultados | `RelatorioJdbcIT.reservasFa03SemDados`; `RelatorioControllerTest.fa03NenhumDadoEncontrado` | Passou |
| 23 | Visualização dos relatórios | `RelatorioControllerTest.geraRelatorio` confere colunas, linhas, totais e data; exportações CSV e PDF também passaram. A renderização JavaFX não foi verificada nesta entrega, conforme pedido de remover o teste gráfico. | Parcial |
| 24 | Reserva → pagamento → cancelamento → vagas | `FluxoPagamentoJdbcIT.reservaPagamentoCancelamentoAtualizaVagas`: vagas 8 → 5 → 8, pagamento quitado preservado e novo pagamento bloqueado | Passou |
| 25 | Mensagens de validação e erro | Asserções de campo/texto em `PagamentoControllerTest`, `ItinerarioControllerTest`, `PacoteControllerTest`, `ReservaCancelamentoTest` e `RelatorioControllerTest` | Passou |
| 26 | Registrar bugs e evidências | Seção abaixo, log integral e CSV dos casos | Concluído |

## Bugs encontrados

### BUG-001 — variável do ambiente Nix faz os testes unitários procurarem o banco

- **Severidade:** média para execução da suíte; não representa falha funcional dos casos de uso.
- **Reprodução:** no ambiente do `flake.nix`, executar `mvn -B clean verify` com `TRIPCONTROL_REPOSITORIO=jdbc` e o banco padrão da aplicação indisponível na porta 5432.
- **Observado:** testes unitários que usam repositórios em memória tentam iniciar o pool JDBC e falham com `Connection to 127.0.0.1:5432 refused`. A primeira execução está em [mvn-clean-verify.log](mvn-clean-verify.log).
- **Esperado:** a configuração `repositorio.tipo=memoria` do Surefire deveria permitir que os testes unitários rodassem sem o banco da aplicação.
- **Causa identificada:** `ConfiguracaoAplicacao.tipoDeRepositorio()` prioriza `TRIPCONTROL_REPOSITORIO` sobre a propriedade de sistema passada pelo Surefire; o `flake.nix` define a variável como `jdbc`.
- **Contorno validado:** executar com `TRIPCONTROL_REPOSITORIO=memoria`; os testes JDBC continuam usando o banco de teste por conexão própria. A execução final passou sem casos ignorados.

Nenhum bug funcional foi reproduzido pelos cenários automatizados executados. 