------- TABELAS -------

create table Cliente (
    CPFCliente varchar(11) primary key,
    nome varchar(120) not null,
    id serial,
    telefone varchar(11),
    email varchar(120),
    endereco varchar(200),
    dataCadastro timestamp not null default now(),
    constraint uk_cliente_id unique (id)
);

create table ClientePreferencia (
    fkCPFCliente varchar(11) not null references Cliente(CPFCliente) on delete cascade,
    preferencia varchar(60) not null,
    constraint pk_cliente_preferencia primary key (fkCPFCliente, preferencia)
);

create table Funcionario (
    CPFFuncionario varchar(11) primary key,
    id serial,
    nome varchar(120) not null,
    email varchar(120) not null,
    senha varchar not null,
    perfil varchar(20) not null,
    ativo boolean not null default true,
    constraint uk_funcionario_id unique (id),
    check (perfil in ('FUNCIONARIO', 'ADMINISTRADOR')),
    constraint uk_funcionario_email unique (email)
);

create table Pacote (
    codPacote serial primary key,
    codigo varchar(10),
    destino varchar(120) not null,
    dataInicio date not null,
    dataFim date not null,
    dataCadastro timestamp not null default now(),
    descricao text,
    roteiro text,
    capacidadeAtual smallint not null,
    preco decimal(10,2) not null check (preco > 0),
    constraint ck_pacote_periodo_estrito check (dataFim > dataInicio),
    constraint uk_pacote_codigo unique (codigo),
    constraint uk_pacote_destino_periodo unique (destino, dataInicio, dataFim),
    constraint ck_pacote_capacidade check (capacidadeAtual > 0)
);

create table Reserva (
    idReserva serial primary key,
    codigo varchar(10),
    fkcodPacote int not null references Pacote(codPacote),
    fkCPFCliente varchar(11) not null references Cliente(CPFCliente),
    quantidadeViajantes smallint not null check (quantidadeViajantes > 0),
    observacoes text,
    dataInicio date,
    dataFim date,
    valorTotal decimal(10,2) not null check (valorTotal > 0),
    dataCriacao timestamp not null,
    versao bigint not null default 0,
    motivoCancelamento varchar,
    dataCancelamento timestamp,
    descricaoCancelamento text,
    responsavelCancelamento varchar(120),
    vagasDevolvidas smallint,
    constraint uk_reserva_codigo unique (codigo),
    constraint ck_reserva_periodo check (dataInicio is null or dataFim is null or dataFim >= dataInicio),
    situacao varchar generated always as (
        case when motivoCancelamento is not null then 'Cancelada' else 'Ativa' end
    ) stored
    
);

create table Parcela ( -- é preciso que o programa em java crie as parcelas de acordo com a quantidade de parcelas e definir o valor
    numParcela smallint,
    fkidReserva int not null references Reserva(idReserva),
    valor decimal(10,2) not null check (valor > 0),
    valorPago decimal(10,2) check (valorPago is null or valorPago > 0),
    vencimento date not null,
    dataRecebimento date,
    formaPagamento varchar(30) not null,
    identificadorPagamento varchar,
    observacao text,
    constraint pk_parcela primary key (numParcela, fkidReserva)
);

create table Atividade (
    idAtividade serial primary key,
    fkcodPacote int not null references Pacote(codPacote),
    dataA date not null,
    horarioInicio time not null,
    horarioFim time not null,
    localA varchar(120) not null,
    infoAdicional text,
    check (horarioFim > horarioInicio)
);

create table Hospedagem (
    idHospedagem serial primary key,
    fkcodPacote int not null references Pacote(codPacote),
    checkin timestamp not null,
    checkout timestamp not null,
    infoAdicional text,
    check (checkout > checkin)
);

create table Transporte (
    idTransporte serial primary key,
    fkcodPacote int not null references Pacote(codPacote),
    saida timestamp not null,
    chegada timestamp not null,
    origem varchar(120) not null,
    destino varchar(120) not null,
    infoAdicional text,
    check (chegada > saida)
);

create table CapacidadeLog (
    idCapacidade serial primary key,
    fkcodPacote int not null references Pacote(codPacote),
    capacidadeAnterior smallint,
    responsavel varchar(120),
    dataInsercao timestamp not null default now(),
    quantidade smallint not null check (quantidade > 0),
    justificativa text
);


---------- SEQUENCES E INDICES ----------
-- Geracao dos codigos de negocio (PacoteRepository.proximoCodigo e
-- ReservaRepository.proximoCodigo). Buracos na numeracao sao esperados:
-- uma sequence nao volta atras quando a transacao falha.
create sequence seq_codigo_pacote start 101;
create sequence seq_codigo_reserva start 8901;


-- RNF03: o PostgreSQL nao cria indice para chave estrangeira automaticamente,
-- e a contagem de vagas ocupadas roda em praticamente toda operacao.
create index idx_reserva_pacote_situacao on Reserva (fkcodPacote, situacao);
create index idx_reserva_cliente on Reserva (fkCPFCliente);
create index idx_reserva_periodo on Reserva (dataInicio, dataFim);
create index idx_capacidadelog_pacote on CapacidadeLog (fkcodPacote);



------- FUNÇÕES E VIEWS -------

create view SituacaoFinanceiraReserva as -- view para verificação do(s) pagamento(s) da reserva
select
    r.idReserva,
    r.valorTotal,
    coalesce(sum(p.valorPago), 0) as totalPago, -- pega a soma de todos os valores pagos das parcelas e salva como totalPago
    r.valorTotal - coalesce(sum(p.valorPago), 0) as saldoPendente, -- calcula o que resta pagar
    case
        when coalesce(sum(p.valorPago), 0) >= r.valorTotal then 'Quitada' -- verifica se já pagou tudo

        when exists (
            select 1 from Parcela p1
            where p1.fkidReserva = r.idReserva
              and p1.dataRecebimento is null
              and p1.vencimento < current_date
        ) then 'Atrasada' -- verifica se tá faltando pagamento e se já venceu

        when coalesce(sum(p.valorPago), 0) > 0 then 'Parcialmente Paga' -- se não tá atrasada nem quitada, mas já foi pago algo

        else 'Pendente'
    end as situacaoFinanceira
from Reserva r
left join Parcela p on p.fkidReserva = r.idReserva
group by r.idReserva, r.valorTotal; -- mostra todas as reservas e as parcelas associadas


create view SituacaoPacote as -- view para mostrar a situação atual do pacote
select
    p.codPacote,
    p.destino,
    p.dataInicio,
    p.dataFim,
    p.capacidadeAtual,
    coalesce(sum(r.quantidadeViajantes), 0) as vagasOcupadas, -- verifica em todas as reservas quantos viajantes estão registrados
    p.capacidadeAtual - coalesce(sum(r.quantidadeViajantes), 0) as vagasDisponiveis, -- salva a quantidade de vagas disponiveis

    case
        when p.dataFim < current_date then 'Encerrado' -- verifica se já encerrou
        when coalesce(sum(r.quantidadeViajantes), 0) >= p.capacidadeAtual then 'Lotado' -- verifica se lotou
        else 'Disponivel'
    end as situacao
from Pacote p

left join Reserva r
    on r.fkcodPacote = p.codPacote
    and r.situacao = 'Ativa'
group by p.codPacote, p.destino, p.dataInicio, p.dataFim, p.CapacidadeAtual;



create or replace function checar_vagas_disponiveis()
returns trigger as $$
declare
    v_capacidade smallint;
    v_ocupadas smallint;
begin
    -- trava a linha do pacote (evita corrida entre reservas concorrentes do mesmo pacote)
    perform 1 from Pacote where codPacote = new.fkcodPacote for update;

    -- pega a capacidade atual do pacote
    select capacidadeAtual into v_capacidade
    from Pacote
    where codPacote = new.fkcodPacote;

    -- soma viajantes das reservas ativas
    select coalesce(sum(quantidadeViajantes), 0) into v_ocupadas
    from Reserva
    where fkcodPacote = new.fkcodPacote
      and situacao = 'Ativa';

    if v_ocupadas + new.quantidadeViajantes > v_capacidade then
        raise exception 'Vagas insuficientes para o pacote % (disponiveis: %)',
            new.fkcodPacote, v_capacidade - v_ocupadas;
    end if;

    return new; -- retorna a possibilidade de criar a reserva
end;
$$ language plpgsql;


create trigger trg_checar_vagas -- trigger pra verificar se existem vagas disponíveis antes de criar uma reserva
before insert on Reserva
for each row
execute function checar_vagas_disponiveis();
