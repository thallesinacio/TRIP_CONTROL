------- TABELAS -------

create table Cliente (
    CPFCliente varchar(11) primary key,
    nome varchar(120) not null,
    contato varchar(120) not null,
    preferencia text
);

create table Funcionario (
    CPFFuncionario varchar(11) primary key,
    nome varchar(120) not null,
    email varchar(120) not null,
    senha varchar not null
);

create table Pacote (
    codPacote serial primary key,
    destino varchar(120) not null,
    dataInicio date not null,
    dataFim date not null,
    descricao text not null,
    roteiro text not null,
    capacidadeAtual smallint not null,
    preco decimal(10,2) not null check (preco > 0),
    check (dataFim >= dataInicio)
);

create table Reserva (
    idReserva serial primary key,
    fkcodPacote int not null references Pacote(codPacote),
    fkCPFCliente varchar(11) not null references Cliente(CPFCliente),
    fkCPFFuncionario varchar(11) references Funcionario(CPFFuncionario),
    quantidadeViajantes smallint not null check (quantidadeViajantes > 0),
    observacoes text,
    valorTotal decimal(10,2) not null check (valorTotal > 0),
    dataCriacao timestamp not null,
    motivoCancelamento varchar,
    dataCancelamento timestamp,
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
    fkCPFFuncionario varchar(11) references Funcionario(CPFFuncionario),
    dataInsercao timestamp not null default now(),
    quantidade smallint not null check (quantidade > 0),
    justificativa text
);

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
