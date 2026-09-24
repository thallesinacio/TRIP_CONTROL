# TripControl

Sistema desktop **100% local** de gestão para agências de turismo — projeto da
disciplina de Engenharia de Software II (UNIVASF, 2026.2).

| Item | Definição |
|------|-----------|
| Linguagem | Java 21 |
| Interface | JavaFX 21 (FXML + Controller separado) |
| Build | Maven |
| Persistência | PostgreSQL via JDBC puro (HikariCP + Flyway) — 1ª fatia ligada |
| Arquitetura | MVC com camada de repositório (padrão Repository/DAO) |

---

## Como rodar

Pré-requisitos: JDK 21 e Maven 3.9+ (o IntelliJ já traz um Maven embutido).

```bash
mvn clean javafx:run
```

Pelo IntelliJ: abrir o projeto, aguardar o download das dependências e executar
`com.tripcontrol.Main` (ou o goal `javafx:run` na aba Maven).

Rodar os testes:

```bash
mvn test
```

### Acesso de demonstração

Enquanto os dados vivem em memória, a aplicação cria um usuário de exemplo na
inicialização (`DadosDemonstracao`):

- **E-mail:** `ana.silva@tripcontrol.com`
- **Senha:** `tripcontrol`

Junto com ele são criados três pacotes, dois clientes e duas reservas, para que
as telas já abram com conteúdo.

---

## Estado atual (primeira fase)

| Caso de uso | Regras (Controller) | Tela |
|-------------|--------------------|------|
| UC01 – Cadastrar Pacotes | completo, com FA01 a FA04 | completa |
| UC02 – Cadastrar Clientes | completo, com FA01 a FA03 | completa |
| UC03 – Registrar Reservas | completo, com FA01 a FA04 | completa |
| UC04 – Controlar Vagas | completo, com FA01, FA03, FA04 e FA05 | completa |
| UC05 – Gerenciar Pagamentos | completo, com FA01 a FA06 | completa |
| UC06 – Montar Itinerário | completo, com FA01 a FA07 | completa |
| UC07 – Cancelar Reservas | completo, com FA01 a FA06 | completa |
| UC08 – Gerar Relatórios | consultas de apoio nos repositórios | próxima fase |

Só a tela do UC08 ainda abre o marcador "em construção", de modo que a navegação
funciona de ponta a ponta.

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
│   └── memory/                # implementações em memória (fase atual)
├── controller/                # regras de aplicação dos casos de uso
│   └── dto/                   # dados de formulário e projeções de leitura
├── view/                      # controllers das telas JavaFX + navegação
└── util/                      # validações, formatação, hash de senha, config do banco
src/main/resources/
├── fxml/                      # layout das telas
├── css/app.css                # identidade visual (protótipo do Figma)
└── config/database.properties # parâmetros do banco (ainda não usados)
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
└── R__carga_inicial.sql           # usuário inicial (ana.silva@tripcontrol.com / tripcontrol)
```

Uma migração já aplicada nunca é editada: correção entra como arquivo novo (`V3__...`).

### Testes

```bash
mvn clean test     # unidade, sempre em memória, não exige banco
mvn clean verify   # inclui os testes de integração (*IT) contra o banco de teste
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
