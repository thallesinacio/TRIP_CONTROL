# TripControl

Sistema desktop **100% local** de gestão para agências de turismo — projeto da
disciplina de Engenharia de Software II (UNIVASF, 2026.2).

| Item | Definição |
|------|-----------|
| Linguagem | Java 21 |
| Interface | JavaFX 21 (FXML + Controller separado) |
| Build | Maven |
| Persistência | PostgreSQL via JDBC (HikariCP + Flyway), com modo memória opcional |
| Arquitetura | MVC com camada de repositório (padrão Repository/DAO) |

---

## Como rodar

Pré-requisitos: JDK 21, Maven 3.9+ e PostgreSQL local.

```bash
mvn clean javafx:run
```

Pelo IntelliJ: abrir o projeto, aguardar o download das dependências e executar
`com.tripcontrol.Main` (ou o goal `javafx:run` na aba Maven).

O [manual de uso](docs/manual-de-uso.md) descreve os fluxos das oito telas,
incluindo reservas, vagas, pagamentos, itinerários, cancelamentos e relatórios.

Rodar os testes:

```bash
TRIPCONTROL_REPOSITORIO=memoria mvn test
```

### Acesso de demonstração

No modo JDBC, a migração `R__carga_inicial.sql` cria um usuário inicial. No
modo memória, `DadosDemonstracao` cria o usuário e dados de exemplo:

- **E-mail:** `ana.silva@tripcontrol.com`
- **Senha:** `tripcontrol`

No modo memória, são criados pacotes, clientes, reservas e recursos para
experimentar as telas. O modo JDBC mantém os dados cadastrados entre execuções.

---

## Estado atual

| Caso de uso | Regras | Tela |
|-------------|--------|------|
| UC01 – Cadastrar Pacotes | implementadas | disponível |
| UC02 – Cadastrar Clientes | implementadas | disponível |
| UC03 – Registrar Reservas | implementadas | disponível |
| UC04 – Controlar Vagas | implementadas | disponível |
| UC05 – Gerenciar Pagamentos | implementadas | disponível |
| UC06 – Montar Itinerário | implementadas | disponível |
| UC07 – Cancelar Reservas | implementadas | disponível |
| UC08 – Gerar Relatórios | implementadas | disponível |

Os oito casos de uso têm telas e regras de negócio. Esta tabela indica
existência da funcionalidade; não é uma declaração de aderência integral a
todos os fluxos do documento funcional. O manual registra como operar as
telas e as condições de uso do banco de dados.

O cadastro de hospedagens, transportes e atividades que o UC06 pressupõe é um
diálogo modal aberto pela tela de Itinerários, e não um item do menu lateral: o
menu segue o protótipo.

---

## Estrutura de pastas

```
src/main/java/com/tripcontrol/
├── Main.java                  # ponto de entrada
├── app/                       # TripControlApp (JavaFX), ContextoAplicacao (injeção), DadosDemonstracao
├── model/                     # entidades de domínio e enums (POJOs puros)
├── repository/                # interfaces Repository/DAO
│   ├── jdbc/                  # implementações PostgreSQL
│   └── memory/                # implementações em memória para testes e demonstração
├── controller/                # regras de aplicação dos casos de uso
│   └── dto/                   # dados de formulário e projeções de leitura
├── view/                      # controllers das telas JavaFX + navegação
└── util/                      # validações, formatação, hash de senha, config do banco
src/main/resources/
├── fxml/                      # layout das telas
├── css/app.css                # identidade visual (protótipo do Figma)
├── config/                   # modo de persistência e conexão local
└── db/migration/             # migrações Flyway do PostgreSQL
src/test/java/com/tripcontrol/  # testes JUnit 5 das regras de negócio
```

### Decisões que valem registro

- **Vagas nunca são persistidas.** As vagas ocupadas são sempre derivadas da soma
  dos viajantes das reservas ativas, e recalculadas no momento da gravação. Isso
  elimina a classe de bug em que um contador guardado no pacote diverge das
  reservas reais.
- **Controllers devolvem `Resultado`, não exceções.** Situações previstas nos
  casos de uso (campo inválido, duplicidade, concorrência) são estados normais do
  fluxo; a tela lê o `StatusResultado` e decide entre destacar campos, oferecer
  abrir o registro existente ou recarregar os dados.
- **Formulários chegam ao Controller como texto.** Toda conversão e validação
  fica na camada de aplicação, o que permite testar os fluxos alternativos sem
  subir a interface gráfica — os testes rodam sem JavaFX.
- **Senha só existe como hash BCrypt** (`SenhaUtils`), atendendo ao RNF04.

---

## Banco de dados

**O sistema inteiro roda sobre PostgreSQL.** Usuário, Pacote, Cliente e Reserva
entraram na primeira fatia (Etapa 3); Parcela, Pagamento, Recurso e Itinerário na
segunda (Etapa 6). O modo memória continua disponível como plano B — veja abaixo.

As decisões de modelagem e integração estão explicadas em
[`docs/decisoes-banco.md`](docs/decisoes-banco.md).

### Escolher onde os dados ficam

`src/main/resources/config/aplicacao.properties`:

```properties
# memoria = nada é gravado, não exige PostgreSQL (plano B da apresentação)
# jdbc    = persistência real, aplica as migrações no start
repositorio.tipo=jdbc
```

Também dá para sobrescrever pela variável de ambiente `TRIPCONTROL_REPOSITORIO`
ou pela propriedade de sistema `-Drepositorio.tipo=...`.

### Preparar o banco

1. Criar um banco vazio: `createdb tripcontrol` (ou `CREATE DATABASE tripcontrol;`).
2. Conferir host, porta, usuário e senha em `config/database.properties` — cada chave
   aceita sobrescrita por variável de ambiente (`TRIPCONTROL_DB_HOST` e afins), para
   nunca haver senha no repositório.
3. Rodar a aplicação. O Flyway aplica as migrações sozinho:

```
src/main/resources/db/migration/
├── V1__criar_schema_inicial.sql   # o script da equipe, sem alteração
├── V2__ajustes_fatia_1.sql        # colunas e constraints que os casos de uso exigem
├── V3__fatia_2_pagamentos_e_itinerario.sql # pagamentos, recursos e itinerários
└── R__carga_inicial.sql           # usuário inicial (ana.silva@tripcontrol.com / tripcontrol)
```

Uma migração já aplicada nunca é editada: correções entram em novas versões.

### Testes

```bash
TRIPCONTROL_REPOSITORIO=memoria mvn clean test     # unidade, não exige banco
TRIPCONTROL_REPOSITORIO=memoria mvn clean verify   # inclui integração (*IT) no banco de teste
```

Os testes de integração usam um banco separado e desfazem cada transação no fim.
Sem banco disponível eles se auto-ignoram, então a build não quebra. Configuração por
variável de ambiente: `TRIPCONTROL_TEST_DB_HOST`, `..._PORT`, `..._NAME`
(padrão `tripcontrol_test`), `..._USER`, `..._PASSWORD`.

### Como a troca de persistência foi isolada

`app/ContextoAplicacao.criar()` é o único ponto que sabe qual implementação está em
uso. Controllers e Views dependem só das interfaces de repositório e não mudaram por
causa do banco — que era exatamente a aposta feita na primeira fase.

## Observação sobre o esqueleto antigo

O projeto foi gerado pelo IntelliJ com a classe `org/example/Main.java`. Ela não
é mais usada e pode ser apagada junto com a pasta `src/main/java/org`.
