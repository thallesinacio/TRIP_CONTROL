# TripControl

Sistema desktop **100% local** de gestão para agencias de turismo — projeto da
disciplina de Engenharia de Software II (UNIVASF, 2026.2).

| Item | Definicao |
|------|-----------|
| Linguagem | Java 21 |
| Interface | JavaFX 21 (FXML + Controller separado) |
| Build | Maven |
| Persistencia | PostgreSQL via JDBC puro — **ainda nao ligada** (ver abaixo) |
| Arquitetura | MVC com camada de repositorio (padrao Repository/DAO) |

---

## Como rodar

Pré-requisitos: JDK 21 e Maven 3.9+ (o IntelliJ ja traz um Maven embutido).

```bash
mvn clean javafx:run
```

Pelo IntelliJ: abrir o projeto, aguardar o download das dependencias e executar
`com.tripcontrol.Main` (ou o goal `javafx:run` na aba Maven).

Rodar os testes:

```bash
mvn test
```

### Acesso de demonstracao

Enquanto os dados vivem em memoria, a aplicacao cria um usuario de exemplo na
inicializacao (`DadosDemonstracao`):

- **E-mail:** `ana.silva@tripcontrol.com`
- **Senha:** `tripcontrol`

Junto com ele sao criados tres pacotes, dois clientes e duas reservas, para que
as telas ja abram com conteudo.

---

## Estado atual (primeira fase)

| Caso de uso | Regras (Controller) | Tela |
|-------------|--------------------|------|
| UC01 – Cadastrar Pacotes | completo, com FA01 a FA04 | completa |
| UC02 – Cadastrar Clientes | completo, com FA01 a FA03 | completa |
| UC03 – Registrar Reservas | completo, com FA01 a FA04 | completa |
| UC04 – Controlar Vagas | calculo de ocupacao e alteracao de capacidade (FA03, FA04) | proxima fase |
| UC05 – Gerenciar Pagamentos | entidades e repositorios prontos | proxima fase |
| UC06 – Montar Itinerario | entidades e repositorios prontos | proxima fase |
| UC07 – Cancelar Reservas | entidades prontas (`Cancelamento`, `Reserva.cancelar`) | proxima fase |
| UC08 – Gerar Relatorios | consultas de apoio nos repositorios | proxima fase |

As telas de UC04 a UC08 ja aparecem no menu lateral e abrem um marcador
"em construcao", de modo que a navegacao funciona de ponta a ponta.

---

## Estrutura de pastas

```
src/main/java/com/tripcontrol/
├── Main.java                  # ponto de entrada
├── app/                       # TripControlApp (JavaFX), ContextoAplicacao (injecao), DadosDemonstracao
├── model/                     # entidades de dominio e enums (POJOs puros)
├── repository/                # interfaces Repository/DAO
│   └── memory/                # implementacoes em memoria (fase atual)
├── controller/                # regras de aplicacao dos casos de uso
│   └── dto/                   # dados de formulario e projecoes de leitura
├── view/                      # controllers das telas JavaFX + navegacao
└── util/                      # validacoes, formatacao, hash de senha, config do banco
src/main/resources/
├── fxml/                      # layout das telas
├── css/app.css                # identidade visual (prototipo do Figma)
└── config/database.properties # parametros do banco (ainda nao usados)
src/test/java/com/tripcontrol/  # testes JUnit 5 das regras de negocio
```

### Decisoes que valem registro

- **Vagas nunca sao persistidas.** As vagas ocupadas sao sempre derivadas da soma
  dos viajantes das reservas ativas, e recalculadas no momento da gravacao. Isso
  elimina a classe de bug em que um contador guardado no pacote diverge das
  reservas reais.
- **Controllers devolvem `Resultado`, nao excecoes.** Situacoes previstas nos
  casos de uso (campo invalido, duplicidade, concorrencia) sao estados normais do
  fluxo; a tela le o `StatusResultado` e decide entre destacar campos, oferecer
  abrir o registro existente ou recarregar os dados.
- **Formularios chegam ao Controller como texto.** Toda conversao e validacao
  fica na camada de aplicacao, o que permite testar os fluxos alternativos sem
  subir a interface grafica — os testes rodam sem JavaFX.
- **Senha so existe como hash BCrypt** (`SenhaUtils`), atendendo ao RNF04.

---

## Banco de dados: por que ainda nao esta ligado

O modelo de dados esta sendo fechado pela equipe, entao nenhuma classe abre
conexao nesta fase. O que ja existe:

- `util/DatabaseConfig` le host, porta, banco, usuario e senha de
  `resources/config/database.properties`, com sobrescrita por variavel de
  ambiente (`TRIPCONTROL_DB_HOST` e afins).
- `util/ConnectionFactory` tem o metodo `abrirConexao()` com o corpo real
  comentado e uma excecao explicita no lugar.

**Para ligar o banco, na proxima fase:**

1. Criar `JdbcPacoteRepository`, `JdbcClienteRepository`, etc., implementando as
   mesmas interfaces de `repository/`.
2. Descomentar o corpo de `ConnectionFactory.abrirConexao()`.
3. Trocar as implementacoes no construtor de `app/ContextoAplicacao`.

Nenhum Controller e nenhuma View precisam ser alterados — essa era a razao de
isolar a persistencia desde agora.

---

## Observacao sobre o esqueleto antigo

O projeto foi gerado pelo IntelliJ com a classe `org/example/Main.java`. Ela nao
e mais usada e pode ser apagada junto com a pasta `src/main/java/org`.
