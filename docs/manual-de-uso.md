# Manual de uso do TripControl

## Iniciar e entrar

O TripControl é um aplicativo desktop JavaFX. Prepare o PostgreSQL local,
configure a conexão em `src/main/resources/config/database.properties` ou pelas
variáveis `TRIPCONTROL_DB_HOST`, `TRIPCONTROL_DB_PORT`, `TRIPCONTROL_DB_NAME`,
`TRIPCONTROL_DB_USER` e `TRIPCONTROL_DB_PASSWORD`, e execute `mvn javafx:run`.
No ambiente Nix do projeto, `direnv allow` ativa o flake; use
`tripcontrol-db start` para preparar os bancos. O Flyway aplica as migrações
ao iniciar. Entre com o usuário inicial informado no README ou com uma conta
cadastrada. As ações exigem autenticação.

Para uma demonstração sem banco, defina `TRIPCONTROL_REPOSITORIO=memoria` antes
de iniciar. Nesse modo os dados são descartados ao fechar o aplicativo, e a
tela de relatórios exige o banco relacional.

## Operações

1. **Cadastrar Pacote:** abra **Pacotes**, informe destino, datas, descrição,
   preço, capacidade e roteiro previsto; clique em **Salvar Pacote**. A data
   inicial não pode estar no passado, o fim deve ser posterior ao
   início, preço e capacidade devem ser positivos. Destino e período iguais
   aos de outro pacote são tratados como duplicidade. **Cancelar** descarta
   o formulário.
2. **Cadastrar Cliente:** abra **Clientes**, informe nome, CPF e telefone;
   complete os demais dados se necessário e salve. O sistema valida CPF e
   telefone e identifica CPF já cadastrado. **Cancelar** descarta a operação.
3. **Registrar Reserva:** abra **Reservas**. Selecione um cliente e um pacote
   disponível, informe quantidade de viajantes e período dentro das datas do
   pacote, e confirme. Pacotes lotados ou encerrados não aparecem na seleção.
   A disponibilidade e a situação são conferidas novamente ao gravar. A
   confirmação mostra código, cliente, pacote, período, viajantes e total.
   Se faltar um cliente, use o atalho de cadastro na própria tela.
4. **Controlar Vagas:** abra **Vagas** para consultar código, destino, início,
   fim, capacidade, vagas ocupadas, vagas disponíveis e situação de cada
   pacote. Para alterar a capacidade, selecione o pacote, informe um inteiro
   positivo e uma justificativa, e salve. A nova capacidade não pode ficar
   abaixo das vagas ocupadas. **Atualizar** recarrega a consulta.
5. **Gerenciar Pagamentos:** abra **Pagamentos** e pesquise a reserva por
   código, CPF, nome ou código do pacote. Consulte parcelas, total pago, saldo
   e situação financeira. Use **Gerar Parcelas** para definir o parcelamento;
   depois use **Registrar Pagamento**, informe parcela, valor, recebimento, forma
   e vencimento; acrescente a referência quando houver, e confirme. O valor
   não pode ultrapassar o saldo; parcela e transação duplicadas são recusadas.
   Reservas canceladas ficam
   disponíveis apenas para consulta financeira.
6. **Montar Itinerário:** abra **Itinerários**, pesquise e selecione um pacote.
   Escolha Hospedagem, Transporte ou Atividade e um recurso cadastrado. Se
   precisar, use **Cadastrar novo** para criar o recurso. Informe os horários
   e locais exigidos, adicione os itens e clique em **Finalizar Itinerário**.
   Os itens são ordenados por data e horário; conflitos, intervalos fora do
   pacote e itinerários vazios são recusados. **Cancelar Itinerário** descarta
   o rascunho após confirmação.
7. **Cancelar Reserva:** abra **Cancelamentos**, pesquise e selecione uma
   reserva ativa. Escolha o motivo; se escolher **Outro**, descreva-o. Após
   validar esses campos, o sistema mostra a quantidade de vagas que voltará
   ao pacote e solicita confirmação. O histórico financeiro é preservado.
8. **Gerar Relatórios:** abra **Relatórios** no modo JDBC, escolha Reservas,
   Pagamentos, Pacotes Mais Procurados, Clientes ou Ocupação dos Pacotes,
   preencha os filtros aplicáveis e clique em **Gerar Relatório**. Consulte a
   prévia e imprima ou exporte em PDF/CSV. Se nenhum registro atender aos
   filtros, ajuste os critérios e gere novamente.

## Dados e manutenção

O modo JDBC persiste dados no PostgreSQL local. As vagas disponíveis são
calculadas a partir da capacidade e das reservas ativas; cancelar uma reserva
devolve suas vagas sem alterar um contador separado. As migrações estão em
`src/main/resources/db/migration/`; novas alterações de esquema devem usar
novos arquivos de versão Flyway. As regras de negócio ficam em
`src/main/java/com/tripcontrol/controller/`, o acesso a dados em
`src/main/java/com/tripcontrol/repository/` e as telas em
`src/main/resources/fxml/` e `src/main/java/com/tripcontrol/view/`.
