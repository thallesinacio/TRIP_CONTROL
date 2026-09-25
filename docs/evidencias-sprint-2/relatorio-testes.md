# Validação da Sprint 2 — TripControl

Data: 24/09/2026. Execução no Java 21, Maven 3.9.16 e PostgreSQL 16.15, em um banco de teste isolado (`tripcontrol_test`, porta 55432). As migrações Flyway foram aplicadas. Cada teste JDBC abre uma transação que é desfeita ao final.

## Resultado geral

Comando final: `TRIPCONTROL_REPOSITORIO=memoria TRIPCONTROL_TEST_DB_PORT=55432 mvn -B clean verify` (a senha do banco foi fornecida por variável de ambiente).

**186 testes passaram: 110 unitários e 76 de integração JDBC. Falhas: 0; erros: 0; ignorados: 0.** O resultado completo está em [mvn-clean-verify-final.log](mvn-clean-verify-final.log), e os nomes e resultados individuais estão em [casos-executados.csv](casos-executados.csv).

### Reexecução após o alinhamento ao documento funcional

Em 24/09/2026, após ajustar UC03, UC04, UC07 e a documentação, executei
`TRIPCONTROL_REPOSITORIO=memoria TRIPCONTROL_TEST_DB_PORT=55467 TRIPCONTROL_TEST_DB_NAME=tripcontrol_align_test mvn -B -q clean verify`
com um PostgreSQL 16.15 temporário e isolado (`tripcontrol_align_test`). O
banco principal não foi alterado. **190 testes passaram: 113 unitários e 77
de integração JDBC; 0 falhas, 0 erros e 0 ignorados.** Os resultados individuais
estão em [alinhamento-doc-funcional-2026-09-24.csv](alinhamento-doc-funcional-2026-09-24.csv).

Os novos casos cobrem a exclusão de pacotes lotados ou encerrados da seleção,
a recusa de reserva de pacote encerrado em memória e no JDBC, os campos do
resumo de confirmação e a validação antecipada do motivo de cancelamento.
O arquivo FXML da consulta de vagas foi validado como XML. A interface gráfica
não foi executada nesta reexecução.

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

### BUG-002 — tabelas de Relatórios e Cancelamentos comprimidas pela área de rolagem

- **Severidade:** alta para consulta visual; o banco e os controllers retornavam registros, mas a tabela podia parecer vazia.
- **Reprodução:** gerar o relatório de Clientes com um registro e consultar uma reserva em Cancelamentos na janela principal.
- **Observado:** o relatório mostrava `Clientes: 1` e `Registros Totais: 1`, enquanto as linhas não apareciam ao usuário. O `ScrollPane` principal usava `fitToHeight="true"` e comprimia a tabela de Relatórios de 300 para 118 pixels no diagnóstico de layout a 1280 × 760.
- **Correção:** removido `fitToHeight` de `principal.fxml`, permitindo rolagem vertical com a altura das tabelas preservada. Cancelamentos passou a mostrar a quantidade encontrada; Relatórios avisa sobre a prévia e rola até ela após a geração.
- **Verificação:** consulta somente de leitura ao banco principal confirmou 4 reservas e dados nos cinco relatórios. Após a correção, a inspeção JavaFX por código encontrou 1 linha com 5 células preenchidas na tabela de Clientes, com altura de 300 pixels, e 1 linha com 6 células preenchidas em Cancelamentos, com altura de 170 pixels. `TRIPCONTROL_REPOSITORIO=memoria mvn -B -q test`: 113 testes, sem falhas ou ignorados. Nenhum dado do banco principal foi alterado.
