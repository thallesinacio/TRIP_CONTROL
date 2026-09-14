# TripControl

Sistema desktop **100% local** de gestão para agências de turismo — projeto da
disciplina de Engenharia de Software II (UNIVASF, 2026.2).

| Item | Definição |
|------|-----------|
| Linguagem | Java 21 |
| Interface | JavaFX 21 (FXML + Controller separado) |
| Build | Maven |
| Persistência | PostgreSQL via JDBC puro — **ainda não ligada** (ver abaixo) |
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
| UC04 – Controlar Vagas | cálculo de ocupação e alteração de capacidade (FA03, FA04) | próxima fase |
| UC05 – Gerenciar Pagamentos | entidades e repositórios prontos | próxima fase |
| UC06 – Montar Itinerário | entidades e repositórios prontos | próxima fase |
| UC07 – Cancelar Reservas | entidades prontas (`Cancelamento`, `Reserva.cancelar`) | próxima fase |
| UC08 – Gerar Relatórios | consultas de apoio nos repositórios | próxima fase |

As telas de UC04 a UC08 já aparecem no menu lateral e abrem um marcador
"em construção", de modo que a navegação funciona de ponta a ponta.

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

## Banco de dados: por que ainda não está ligado

O modelo de dados está sendo fechado pela equipe, então nenhuma classe abre
conexão nesta fase. O que já existe:

- `util/DatabaseConfig` lê host, porta, banco, usuário e senha de
  `resources/config/database.properties`, com sobrescrita por variável de
  ambiente (`TRIPCONTROL_DB_HOST` e afins).
- `util/ConnectionFactory` tem o método `abrirConexao()` com o corpo real
  comentado e uma exceção explícita no lugar.

**Para ligar o banco, na próxima fase:**

1. Criar `JdbcPacoteRepository`, `JdbcClienteRepository`, etc., implementando as
   mesmas interfaces de `repository/`.
2. Descomentar o corpo de `ConnectionFactory.abrirConexao()`.
3. Trocar as implementações no construtor de `app/ContextoAplicacao`.

Nenhum Controller e nenhuma View precisam ser alterados — essa era a razão de
isolar a persistência desde agora.

---

## Observação sobre o esqueleto antigo

O projeto foi gerado pelo IntelliJ com a classe `org/example/Main.java`. Ela não
é mais usada e pode ser apagada junto com a pasta `src/main/java/org`.
