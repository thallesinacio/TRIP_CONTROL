# TripControl — decisões de banco de dados

Este documento explica **como o PostgreSQL foi integrado ao código Java** e **por que cada
decisão foi tomada**. Ele parte do princípio de que você sabe SQL, mas nunca ligou um banco
a uma aplicação Java.

Escopo: Etapas 3 (primeira fatia), 6 (segunda fatia) e 7 (relatórios) do roadmap.

---

## 1. O mapa geral: quem fala com quem

```
Tela (FXML + ViewController)
        │  só conhece o Controller
        ▼
Controller (regras do caso de uso)
        │  só conhece INTERFACES de repositório
        ▼
Repositório (JdbcXxxRepository  ou  InMemoryXxxRepository)
        │
        ▼
Conexoes → ConnectionFactory (pool HikariCP) → PostgreSQL
```

A regra que sustenta tudo: **nenhum Controller e nenhuma tela sabem que existe um banco.**
Eles dependem de interfaces como `ReservaRepository`. Quem escolhe a implementação é um
único arquivo, `app/ContextoAplicacao`. Foi isso que permitiu migrar o sistema inteiro para
o PostgreSQL sem alterar uma linha de Controller, View ou FXML.

Se quiser conferir: procure por `import java.sql` dentro de `controller/` e `view/`. Não há
nenhum.

---

## 2. Decisões de infraestrutura

### 2.1 Pool de conexões em vez de abrir conexão a cada consulta

- **O que foi feito:** `util/ConnectionFactory` mantém um pool HikariCP (máximo 5 conexões).
- **Alternativas:** `DriverManager.getConnection()` a cada operação.
- **Por quê:** abrir uma conexão TCP + autenticar custa dezenas de milissegundos; numa tela
  que carrega uma tabela isso aparece. O pool mantém conexões prontas e ainda dá detecção de
  vazamento (`leakDetectionThreshold`), que avisa no log quando alguém esquece de fechar.
- **Onde:** `util/ConnectionFactory.java`.

### 2.2 Migrações com Flyway, e a V1 é o script da equipe

- **O que foi feito:** o `database/script.sql` escrito pela equipe é a migração
  `V1__criar_schema_inicial.sql`. Ajustes entraram em `V2`, `V3`, e assim por diante.
- **Alternativas:** rodar o script à mão em cada máquina; usar `create table if not exists`.
- **Por quê:** o Flyway grava numa tabela de controle quais migrações já rodaram, então o
  banco de cada integrante converge para o mesmo estado sozinho, e o professor consegue
  criar o banco do zero só rodando a aplicação.
- **Regra que não pode ser quebrada:** o Flyway guarda o **checksum** de cada migração
  aplicada. Editar um arquivo já aplicado faz a aplicação falhar com
  *"Migration checksum mismatch"* em toda máquina que já rodou o projeto. **Toda mudança de
  schema é um arquivo novo** (V4, V5, …).
- **Onde:** `util/Migracoes.java`, `src/main/resources/db/migration/`.

### 2.3 Alternar entre banco e memória por configuração

- **O que foi feito:** a chave `repositorio.tipo` aceita `jdbc` (padrão) ou `memoria`.
- **Por quê:** três ganhos concretos. Os testes de unidade rodam sem exigir PostgreSQL
  instalado; existe um plano B se o banco falhar na máquina da apresentação; e a existência
  de duas implementações **prova** que a camada de persistência é substituível — é um
  argumento de arquitetura que se demonstra, não se promete.
- **Onde:** `util/ConfiguracaoAplicacao.java`, `app/ContextoAplicacao.criar()`.

### 2.4 Erros de banco nunca chegam à tela como `SQLException`

- **O que foi feito:** `JdbcRepositorioBase` captura `SQLException` e a traduz em
  `RepositorioException` com uma frase em português ("Falha ao salvar reserva: …").
  Violação de unicidade (SQLState 23505) é reconhecida e vira o fluxo de duplicidade do
  caso de uso.
- **Por quê:** *stack trace* na tela é defeito de usabilidade e RNF01. E o Controller precisa
  distinguir "o banco caiu" de "esse CPF já existe" — a segunda é um fluxo alternativo
  previsto, não um erro.
- **Onde:** `repository/RepositorioException.java`, `repository/jdbc/JdbcRepositorioBase.java`.

---

## 3. Decisões de modelagem (o que foi acrescentado ao schema)

Nenhuma migração apaga dado existente. Todas são colunas novas, tabelas novas ou remoção de
restrição.

### 3.1 Nada derivado é gravado

Quatro coisas que **poderiam** ser colunas e deliberadamente não são:

| Informação | Como é obtida |
|---|---|
| Vagas ocupadas de um pacote | soma dos viajantes das reservas ativas (view `SituacaoPacote`) |
| Situação financeira da reserva | view `SituacaoFinanceiraReserva` / `SituacaoFinanceira.calcular` |
| Status da parcela (Pendente/Atrasada/Quitada) | data de recebimento + vencimento vs. hoje |
| Status do itinerário (Rascunho/Finalizado) | `Pacote.itinerarioFinalizadoEm` é nulo ou não |

- **Alternativa:** guardar contadores e status em colunas, atualizados a cada operação.
- **Por quê:** um valor derivado gravado é um valor que pode divergir da realidade. "Vagas
  ocupadas = 4" e quatro reservas canceladas é um estado impossível de explicar ao cliente.
  Além disso, "Atrasada" muda sozinha com a passagem do tempo: nenhuma escrita acontece
  quando um vencimento vence, então um status gravado estaria errado no dia seguinte.
- **Custo aceito:** as consultas fazem mais trabalho. Por isso a V3 criou índices em
  `Parcela(fkidReserva)` e nas chaves estrangeiras das tabelas de item.

### 3.2 Não existe tabela `Pagamento`

- **O que foi feito:** o recebimento vive nas colunas da própria `Parcela` —
  `valorPago`, `dataRecebimento`, `formaPagamento`, `identificadorPagamento`,
  `comprovante`, `observacao`. `JdbcParcelaRepository` e `JdbcPagamentoRepository` escrevem
  na mesma linha, em colunas diferentes.
- **Alternativa:** tabela `Pagamento` com FK para `Parcela`.
- **Por quê:** a decisão A da equipe diz que **um pagamento quita exatamente uma parcela**.
  Com relação 1:1, uma tabela separada acrescentaria um *join* em toda consulta financeira
  sem nenhum ganho. O schema da equipe já estava modelado assim.
- **Consequência aceita conscientemente:** não é possível registrar pagamento parcial de uma
  parcela, nem manter histórico de tentativas de pagamento. Se a agência precisar disso, a
  mudança é criar a tabela e reescrever `JdbcPagamentoRepository` — nada fora dele muda.
- **Cuidado ao ler o código:** `JdbcParcelaRepository.atualizar` **não** escreve as colunas
  de pagamento. Se escrevesse, a baixa da parcela apagaria o pagamento recém-gravado.

### 3.3 Dois identificadores diferentes, facilmente confundidos

| Coluna | O que é | Origem | Único? |
|---|---|---|---|
| `identificadorPagamento` | **número do recibo** (UC05 passo 10) | gerado pela sequence `seq_numero_recibo` | sim |
| `comprovante` | **identificador da transação** (UC05 passo 6) | digitado pelo funcionário | não; é onde o FA04 detecta repetição |

A V3 acrescentou `comprovante` e a restrição de unicidade em `identificadorPagamento`.
`formaPagamento` deixou de ser `NOT NULL`, porque uma parcela recém-planejada ainda não tem
forma de pagamento — sem isso, "Definir Parcelamento" não conseguiria gravar o plano.

### 3.4 Tabela `Recurso`: catálogo × ocorrência

- **O que foi feito:** nova tabela `Recurso(idRecurso, tipo, nome, local, contato, descricao)`,
  e `Hospedagem`/`Transporte`/`Atividade` ganharam `fkidRecurso`.
- **Por quê:** as três tabelas guardavam a **ocorrência** de um serviço dentro de um pacote
  (datas, horários) e não tinham nome — não havia onde dizer *qual hotel*. A pré-condição do
  UC06 exige registros "previamente cadastrados", reutilizáveis entre pacotes. É a mesma
  relação que `Pacote` tem com `Reserva`: catálogo de um lado, ocorrência do outro.
- **Integridade no banco, não só no código:** cada tabela de item ganhou uma coluna gerada
  constante (`tipoRecurso`) e uma chave estrangeira composta para `Recurso(idRecurso, tipo)`.
  O efeito é que o banco **recusa** uma linha de `Hospedagem` apontando para um recurso de
  tipo `TRANSPORTE`. Usa o mesmo recurso do PostgreSQL que a equipe já usava em
  `Reserva.situacao`.

### 3.5 Não existe tabela `Itinerario`

- **O que foi feito:** uma coluna, `Pacote.itinerarioFinalizadoEm timestamp`.
- **Alternativa:** tabela `Itinerario(idItinerario, fkcodPacote unique, situacao, dataFinalizacao)`.
- **Por quê:** o itinerário é **1:1 com o pacote**, e uma tabela 1:1 com outra é sinal de que
  aquelas colunas pertencem à tabela original. O status é derivável (nulo = rascunho) e os
  itens já apontam para o pacote. Uma tabela inteira para guardar uma data não se paga.
- **Onde:** `repository/jdbc/JdbcItinerarioRepository.java`.

### 3.6 Chaves compostas e o `Long id` das entidades

As interfaces de repositório exigem `buscarPorId(Long)`, mas `Parcela` tem chave composta
(`numParcela + fkidReserva`) e os itens de itinerário vivem em três tabelas com sequences
independentes. Duas traduções resolvem isso sem mudar o schema:

- `ChaveDaParcela`: `id = reservaId * 1000 + numeroDaParcela` (limite de 999 parcelas por
  reserva, folgado para uma agência de turismo).
- `ChaveDoItem`: cada tipo ocupa uma faixa de um bilhão de ids, para que a hospedagem 1 e o
  transporte 1 não sejam considerados o mesmo item — `ItemItinerario.equals` compara por id,
  e sem isso remover um item removeria o outro.

Alternativa descartada: acrescentar colunas `serial` só para satisfazer o Java. Mudaria o
schema da equipe sem ganho de modelagem.

---

## 4. Transações e concorrência

### 4.1 Onde a transação começa e termina

`util/Conexoes.emTransacao(...)` recebe um bloco de código e garante: abre a conexão, desliga
o *auto-commit*, executa o bloco, dá `commit` no fim e `rollback` em qualquer exceção.
Enquanto o bloco roda, todos os repositórios chamados **reaproveitam a mesma conexão** — é o
que faz duas gravações caírem na mesma transação. O truque: a conexão fica num
`ThreadLocal`, e o que os repositórios recebem é um *proxy* cujo `close()` não fecha nada
(senão o primeiro repositório a terminar fecharia a transação do segundo).

No modo memória o mesmo método simplesmente executa o bloco. Foi isso que permitiu escrever
UC05 e UC06 na Etapa 4/5 com os dados em memória e, na Etapa 6, ganhar transação de verdade
sem tocar nos Controllers.

Três operações usam esse envelope:

| Operação | O que entra junto |
|---|---|
| Registrar reserva (UC03) | bloqueio do pacote + conferência de vagas + gravação |
| Registrar recebimento (UC05) | pagamento + baixa da parcela |
| Finalizar itinerário (UC06) | cabeçalho + todos os itens |
| Cancelar reserva (UC07) | situação + motivo + responsável |

### 4.2 Detecção de alteração concorrente

Numa aplicação desktop local, "concorrência" é **duas instâncias do TripControl abertas
contra o mesmo banco** — exatamente o que os requisitos preveem. Três mecanismos, cada um
onde faz sentido:

1. **Bloqueio otimista por versão** (UC04 FA04, UC07 FA05): `Reserva` tem a coluna `versao`,
   e o `UPDATE` é `... where idReserva = ? and versao = ?`. Se ninguém mexeu, uma linha é
   afetada e a versão avança; se mexeu, **zero** linhas mudam e o repositório levanta
   `ConflitoDeConcorrenciaException`.
2. **Bloqueio pessimista da linha do pacote** (UC03): antes de contar vagas,
   `select 1 from Pacote where codPacote = ? for update`. Sem isso, duas reservas simultâneas
   leriam as mesmas vagas livres e juntas estourariam a capacidade.
3. **Duas camadas no UC05 FA05:** o Controller compara a "versão financeira" (quantidade de
   pagamentos da reserva) com a que a tela leu; e, no banco, o `UPDATE` do pagamento exige
   `and valorPago is null`. O primeiro dá a mensagem boa para o funcionário; o segundo fecha
   a corrida real, em que as duas instâncias passam pela conferência no mesmo instante.

### 4.3 A trigger da equipe continua valendo

`trg_checar_vagas` recusa no banco uma reserva que estoure a capacidade, mesmo que alguém
insira por fora da aplicação. A conferência no Java existe para dar mensagem decente; a
trigger existe para garantir o dado. As duas juntas são o certo — uma é usabilidade, a outra
é integridade.

---

## 5. Do clique ao banco: registrar um pagamento

Este é o caminho completo de uma operação, com nomes reais de arquivo.

**1. A tela coleta o que foi digitado** —
`view/PagamentoViewController.aoConfirmarPagamento`

Os campos saem como **texto**, do jeito que o funcionário digitou, num
`DadosPagamento`. Junto vai `situacaoAtual.versaoFinanceira()`, a marca de concorrência lida
quando o painel carregou. A tela não converte nem valida nada.

**2. O Controller decide** — `controller/PagamentoController.registrarRecebimento`

Na ordem: reserva existe? (FA01) · está cancelada? (FA02) · o plano existe? · os campos são
válidos? (FA03) · a parcela já foi quitada ou o comprovante repetiu? (FA04) · o valor é
exatamente o da parcela? (decisão A) · a versão financeira mudou? (FA05). **Nenhuma escrita
aconteceu até aqui.**

**3. A transação abre** — `util/Conexoes.emTransacao`

```java
Conexoes.emTransacao(() -> {
    pagamento.setNumeroRecibo(pagamentoRepository.proximoNumeroRecibo()); // nextval
    pagamentoRepository.salvar(pagamento);          // UPDATE das colunas de pagamento
    parcela.quitar(dataRecebimento, pagamento.getId());
    return parcelaRepository.atualizar(parcela);    // UPDATE da data de recebimento
});
```

← **a transação começa aqui**

**4. Os repositórios emitem SQL** — `repository/jdbc/Jdbc*Repository`

`JdbcRepositorioBase` pega a conexão da transação corrente (`Conexoes.atual()`), monta o
`PreparedStatement`, passa os parâmetros e traduz qualquer `SQLException`. O `UPDATE` do
pagamento tem `and valorPago is null`: se outra instância quitou a parcela nesse intervalo,
zero linhas são afetadas e sobe `ConflitoDeConcorrenciaException`.

**5. O pool entrega a conexão física** — `util/ConnectionFactory` → HikariCP → PostgreSQL

**6. A transação fecha** — `commit` se o bloco terminou; `rollback` em qualquer exceção.

← **a transação termina aqui**

Ou o recibo e a baixa da parcela existem juntos, ou nenhum dos dois existe. Não há estado
intermediário visível.

**7. O Controller monta a resposta** — um `Resultado` com `SUCESSO` e o comprovante (recibo,
valor, saldo atualizado, situação recalculada), ou `DUPLICIDADE` / `CONFLITO_CONCORRENCIA` /
`OPERACAO_BLOQUEADA`.

**8. A tela reage ao status** — `Alertas.sucesso(...)` com os dados do passo 10 do UC05, ou
destaque nos campos inválidos, e recarrega o painel.

---

## 6. Relatórios (UC08): uma camada de consulta separada

- **O que foi feito:** `repository/relatorio/RelatorioRepository` com cinco métodos, um por
  relatório, e `JdbcRelatorioRepository` com o SQL.
- **Alternativas:** espalhar as consultas nos repositórios de entidade; filtrar em memória
  com `Stream`.
- **Por quê:** relatório é leitura agregada que atravessa várias tabelas — não pertence a
  nenhuma entidade. E é onde a diferença entre SQL e `Stream` mais aparece: somas, contagens,
  ranking e percentuais saem do banco prontos, com índice, em vez de trazer tudo para a
  memória e agregar em Java.
- **Reaproveitamento:** os relatórios de Pagamentos e de Ocupação leem as views
  `SituacaoFinanceiraReserva` e `SituacaoPacote` que a equipe escreveu, em vez de repetir
  aquelas regras em SQL novo. Há um teste de integração que confere que a view e o cálculo em
  Java concordam (`FluxoPagamentoJdbcIT.bancoEJavaConcordamSobreASituacao`).

**Padrão dos filtros opcionais.** Toda consulta usa a mesma forma:

```sql
where (cast(? as date) is null or dataFim >= ?)
```

Um filtro nulo simplesmente não restringe nada, e a **mesma** consulta serve para qualquer
combinação de filtros preenchidos — sem montar SQL por concatenação, que abriria porta para
injeção. A única parte interpolada é a coluna de ordenação do ranking, e ela vem de um
`enum`, nunca de texto digitado.

**Linhas como texto já formatado.** Os cinco relatórios não têm nenhuma coluna em comum.
Devolver objetos de domínio exigiria cinco tipos e cinco formatadores para chegar ao mesmo
lugar; com um formato tabular único, a tabela da tela, o CSV e o PDF compartilham o mesmo
caminho. A formatação continua centralizada em `util/Formatadores`. **Exceção:** os totais
monetários são somados **pelo banco**, e não a partir das strings formatadas — em pt-BR
"R$ 1.500,00" tem separador de milhar e espaço inquebrável, e refazer esse caminho de volta é
pedir para errar (foi assim que um bug real apareceu durante o desenvolvimento).

**Modo memória:** a pré-condição do UC08 exige o banco disponível. Em vez de duplicar as
cinco consultas agregadas em `Stream` — criando duas fontes de verdade para os mesmos números
—, `RelatorioIndisponivel` responde que os relatórios não estão disponíveis e a tela explica
isso ao funcionário. Nenhuma tela quebra.

**PDF sem biblioteca.** O roadmap sugeria OpenPDF ou PDFBox. Um relatório deste sistema é uma
tabela de texto em fonte padrão, e o subconjunto do formato PDF necessário para isso é
pequeno e estável desde os anos 90 — então `util/ExportadorPdf` o escreve direto, sem
dependência. Ganhos: nenhum jar novo, nenhuma discussão de licença, e o RNF02 (execução 100%
local) fica trivialmente satisfeito. Se a equipe preferir a biblioteca depois, a troca é
nessa classe só. Detalhe que custou um bug: `/WinAnsiEncoding` é o **CP1252**, não o
ISO-8859-1 — os dois coincidem nos acentos do português, mas só o CP1252 tem travessão e
aspas curvas.

**CSV que abre no Excel em português.** Separador `;` (no Excel pt-BR a vírgula é separador
decimal) e **BOM** no início do arquivo (sem os bytes `EF BB BF` o Excel no Windows lê como
ANSI e "Ocupação" vira "OcupaÃ§Ã£o"). Os dois detalhes estão em `util/ExportadorCsv`.

---

## 7. Como criar o banco do zero e rodar

### 7.1 Pré-requisitos

PostgreSQL 16 instalado e rodando. Nada mais: as tabelas são criadas pela própria aplicação.

### 7.2 Criar o banco vazio

As migrações criam **tabelas**, não o banco — o Flyway precisa se conectar a um banco que já
exista. Uma vez, no psql ou no pgAdmin:

```sql
create database tripcontrol;
```

### 7.3 Informar a senha (nunca no código, nunca no commit)

O arquivo `src/main/resources/config/database.properties` tem host, porta, banco e usuário.
A senha vem de fora:

```bash
# Windows (PowerShell), na sessão em que você vai rodar o projeto
$env:TRIPCONTROL_DB_PASSWORD = "sua-senha"
```

Qualquer chave pode ser sobrescrita por variável de ambiente:
`TRIPCONTROL_DB_HOST`, `TRIPCONTROL_DB_PORT`, `TRIPCONTROL_DB_NAME`,
`TRIPCONTROL_DB_USER`, `TRIPCONTROL_DB_PASSWORD`.

### 7.4 Rodar

```bash
mvn javafx:run
```

No primeiro start a aplicação aplica V1, V2, V3 e a carga inicial, cria o usuário da agência
e abre o login. O selo do menu lateral mostra **Conectado ao Banco**.

Credenciais iniciais: `ana.silva@tripcontrol.com` / `tripcontrol` (a senha está no banco
apenas como hash BCrypt).

### 7.5 Rodar no modo memória (plano B)

```bash
mvn javafx:run -Drepositorio.tipo=memoria
```

Ou mude `repositorio.tipo=memoria` em `src/main/resources/config/aplicacao.properties`. Nesse
modo o PostgreSQL nem é procurado, há carga de demonstração, o selo mostra
**Modo memória (plano B)** e os relatórios exibem a mensagem de indisponibilidade.

### 7.6 Recriar o banco do zero

```sql
drop database tripcontrol with (force);
create database tripcontrol;
```

O `with (force)` derruba conexões abertas (PostgreSQL 13+) — sem ele, o `drop` falha se a
aplicação ou o pgAdmin estiverem conectados.

### 7.7 Testes

```bash
mvn clean test      # unidade, sempre em memória, não exige PostgreSQL
mvn clean verify    # inclui os testes de integração
```

Os testes de integração usam um banco separado e **se auto-ignoram** se não houver banco
disponível, para não quebrar a build de quem não subiu o PostgreSQL:

```sql
create database tripcontrol_test;
```

```bash
TRIPCONTROL_TEST_DB_NAME=tripcontrol_test
TRIPCONTROL_TEST_DB_USER=postgres
TRIPCONTROL_TEST_DB_PASSWORD=sua-senha
```

Cada teste de integração roda dentro de uma transação desfeita no final, então o banco de
teste nunca fica sujo e um teste não vê o que o outro gravou.
