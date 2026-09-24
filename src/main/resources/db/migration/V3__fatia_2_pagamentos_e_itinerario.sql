-- ============================================================================
-- V3 - Segunda fatia do banco: Parcela/Pagamento, Recurso e Itinerario
--
-- Resolve as pendencias de schema levantadas nas Etapas 4 e 5. Nada aqui apaga
-- dado existente: sao colunas novas, tabelas novas e um NOT NULL que sai.
--
-- A V1 e a V2 nao podem ser editadas: o Flyway guarda o checksum de cada
-- migracao ja aplicada. Toda mudanca daqui em diante entra numa migracao nova.
-- ============================================================================

---------------------------------------------------------------------- PARCELA

-- UC05 passo 10 - numero do recibo.
-- Decisao da equipe: "identificadorPagamento" e o numero do recibo, gerado pelo
-- sistema. Por isso ganha unicidade e uma sequence proria.
alter table Parcela add constraint uk_parcela_recibo unique (identificadorPagamento);
create sequence seq_numero_recibo start 1;

-- UC05 passo 6 - identificador da transacao/comprovante informado pelo
-- funcionario ("quando existir"). E um dado diferente do recibo: opcional,
-- digitado a mao, e e sobre ele que o FA04 detecta duplicidade na reserva.
alter table Parcela add column comprovante varchar(60);

-- Uma parcela recem-planejada ainda nao tem forma de pagamento: o NOT NULL
-- original impediria a acao "Definir Parcelamento" de gravar o plano.
alter table Parcela alter column formaPagamento drop not null;

-- RNF03 - a tela de pagamentos le as parcelas por reserva a cada abertura, e o
-- relatorio de Pagamentos (UC08) filtra por data de recebimento.
create index idx_parcela_reserva on Parcela (fkidReserva);
create index idx_parcela_recebimento on Parcela (dataRecebimento);

---------------------------------------------------------------------- RECURSO

-- Pre-condicao do UC06: hospedagens, transportes e atividades precisam estar
-- "previamente cadastrados". As tabelas Atividade/Hospedagem/Transporte guardam
-- a OCORRENCIA dentro de um pacote (datas, horarios) e nao tem nome: faltava o
-- catalogo reutilizavel entre pacotes. E a mesma relacao que Pacote tem com
-- Reserva - catalogo de um lado, ocorrencia do outro.
create table Recurso (
    idRecurso serial primary key,
    tipo varchar(20) not null check (tipo in ('HOSPEDAGEM', 'TRANSPORTE', 'ATIVIDADE')),
    nome varchar(120) not null,
    local varchar(200),
    contato varchar(120),
    descricao text,
    constraint uk_recurso_tipo_nome unique (tipo, nome),
    -- necessaria para a chave estrangeira composta usada abaixo
    constraint uk_recurso_id_tipo unique (idRecurso, tipo)
);

-- Cada tabela de item aponta para o recurso, e a coluna gerada garante no banco
-- que uma hospedagem so pode referenciar um recurso do tipo HOSPEDAGEM. E o
-- mesmo recurso do PostgreSQL que a equipe ja usa em Reserva.situacao.
alter table Hospedagem add column fkidRecurso int;
alter table Hospedagem add column tipoRecurso varchar(20)
    generated always as ('HOSPEDAGEM') stored;
alter table Hospedagem add constraint fk_hospedagem_recurso
    foreign key (fkidRecurso, tipoRecurso) references Recurso(idRecurso, tipo);

alter table Transporte add column fkidRecurso int;
alter table Transporte add column tipoRecurso varchar(20)
    generated always as ('TRANSPORTE') stored;
alter table Transporte add constraint fk_transporte_recurso
    foreign key (fkidRecurso, tipoRecurso) references Recurso(idRecurso, tipo);

alter table Atividade add column fkidRecurso int;
alter table Atividade add column tipoRecurso varchar(20)
    generated always as ('ATIVIDADE') stored;
alter table Atividade add constraint fk_atividade_recurso
    foreign key (fkidRecurso, tipoRecurso) references Recurso(idRecurso, tipo);

-------------------------------------------------------------------- ITINERARIO

-- UC06 passo 11 - marca quando o roteiro do pacote foi finalizado.
--
-- Nao existe tabela Itinerario de proposito: o itinerario e 1:1 com o pacote, e
-- uma tabela 1:1 com outra e sinal de que as colunas pertencem a tabela
-- original. O status Rascunho/Finalizado tambem nao e gravado - ele e derivado
-- desta data, na mesma linha de Reserva.situacao (coluna gerada) e da situacao
-- financeira (view): nulo = rascunho, preenchido = finalizado.
alter table Pacote add column itinerarioFinalizadoEm timestamp;

-- RNF03 - o cronograma e lido por pacote toda vez que a tela de itinerarios abre.
create index idx_hospedagem_pacote on Hospedagem (fkcodPacote);
create index idx_transporte_pacote on Transporte (fkcodPacote);
create index idx_atividade_pacote on Atividade (fkcodPacote);
