-- ============================================================================
-- V2 - Ajustes da primeira fatia do banco (Usuario, Pacote, Cliente, Reserva)
--
-- O V1 e o script escrito pela equipe, preservado sem nenhuma alteracao.
-- Esta migracao apenas ACRESCENTA o que os casos de uso ja implementados
-- precisam para funcionar sobre o banco. Cada bloco cita o caso de uso que o
-- exige, para a equipe poder revisar item a item.
-- ============================================================================

------------------------------------------------------------------ FUNCIONARIO

-- Identidade numerica: as entidades e as interfaces de repositorio do projeto
-- usam id Long. O CPF continua sendo a chave primaria e a identidade de negocio.
alter table Funcionario add column id serial;
alter table Funcionario add constraint uk_funcionario_id unique (id);

-- RNF04: perfil de acesso e usuario ativo/inativo (o login recusa inativo).
alter table Funcionario add column perfil varchar(20) not null default 'FUNCIONARIO'
    check (perfil in ('FUNCIONARIO', 'ADMINISTRADOR'));
alter table Funcionario add column ativo boolean not null default true;

-- O e-mail e a credencial de login: precisa ser unico.
alter table Funcionario add constraint uk_funcionario_email unique (email);

---------------------------------------------------------------------- CLIENTE

alter table Cliente add column id serial;
alter table Cliente add constraint uk_cliente_id unique (id);

-- UC02 e o prototipo separam telefone, e-mail e endereco; a coluna "contato"
-- original passa a ser opcional para nao duplicar informacao.
alter table Cliente add column telefone varchar(11);
alter table Cliente add column email varchar(120);
alter table Cliente add column endereco varchar(200);
alter table Cliente add column dataCadastro timestamp not null default now();
--alter table Cliente alter column contato drop not null;

-- UC02: preferencias sao varias por cliente (o UC08 ainda vai filtrar por elas).
create table ClientePreferencia (
    fkCPFCliente varchar(11) not null references Cliente(CPFCliente) on delete cascade,
    preferencia varchar(60) not null,
    constraint pk_cliente_preferencia primary key (fkCPFCliente, preferencia)
);

----------------------------------------------------------------------- PACOTE

-- UC01/UC04/UC07 e todas as telas identificam o pacote por um codigo de negocio
-- (PC-101), diferente da chave primaria.
alter table Pacote add column codigo varchar(10);
alter table Pacote add constraint uk_pacote_codigo unique (codigo);
alter table Pacote add column dataCadastro timestamp not null default now();

-- UC01: descricao e roteiro nao aparecem como obrigatorios em nenhum fluxo.
alter table Pacote alter column descricao drop not null;
alter table Pacote alter column roteiro drop not null;

-- UC01 FA03: duplicidade e "mesmo destino e exatamente o mesmo periodo".
alter table Pacote add constraint uk_pacote_destino_periodo unique (destino, dataInicio, dataFim);

-- UC01: capacidade e preco positivos; periodo com fim posterior ao inicio
-- (o CHECK do V1 aceita fim igual ao inicio).
alter table Pacote add constraint ck_pacote_capacidade check (capacidadeAtual > 0);
alter table Pacote add constraint ck_pacote_periodo_estrito check (dataFim > dataInicio);

---------------------------------------------------------------------- RESERVA

alter table Reserva add column codigo varchar(10);
alter table Reserva add constraint uk_reserva_codigo unique (codigo);

-- UC03 FA02 ("periodo da reserva incompativel com o pacote") e o painel de
-- detalhes do UC07 dependem do periodo escolhido na reserva.
alter table Reserva add column dataInicio date;
alter table Reserva add column dataFim date;
alter table Reserva add constraint ck_reserva_periodo
    check (dataInicio is null or dataFim is null or dataFim >= dataInicio);

-- Bloqueio otimista: UC04 FA04 e UC07 FA05 precisam detectar alteracao feita
-- por outro processo depois que a tela carregou os dados.
alter table Reserva add column versao bigint not null default 0;

-- UC07 passo 9: alem do motivo e da data (ja no V1), o cancelamento registra a
-- descricao detalhada (obrigatoria quando o motivo e "Outro") e o responsavel.
alter table Reserva add column descricaoCancelamento text;
alter table Reserva add column responsavelCancelamento varchar(120);
alter table Reserva add column vagasDevolvidas smallint;

--------------------------------------------------------------- CAPACIDADE LOG

-- UC04 passo 7: o historico guarda a capacidade anterior e a nova, e o nome do
-- funcionario responsavel pela alteracao.
alter table CapacidadeLog add column capacidadeAnterior smallint;
alter table CapacidadeLog add column responsavel varchar(120);

------------------------------------------------------------------- SEQUENCES

-- Geracao dos codigos de negocio (PacoteRepository.proximoCodigo e
-- ReservaRepository.proximoCodigo). Buracos na numeracao sao esperados:
-- uma sequence nao volta atras quando a transacao falha.
create sequence seq_codigo_pacote start 101;
create sequence seq_codigo_reserva start 8901;

---------------------------------------------------------------------- INDICES

-- RNF03: o PostgreSQL nao cria indice para chave estrangeira automaticamente,
-- e a contagem de vagas ocupadas roda em praticamente toda operacao.
create index idx_reserva_pacote_situacao on Reserva (fkcodPacote, situacao);
create index idx_reserva_cliente on Reserva (fkCPFCliente);
create index idx_reserva_periodo on Reserva (dataInicio, dataFim);
create index idx_capacidadelog_pacote on CapacidadeLog (fkcodPacote);
