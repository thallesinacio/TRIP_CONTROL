-- ============================================================================
-- Carga inicial (migracao repetivel: roda sempre que o conteudo mudar).
--
-- Cria o funcionario que permite entrar no sistema logo depois que o banco e
-- criado do zero. A senha nunca fica em texto puro: o valor abaixo e o hash
-- BCrypt (custo 10) da senha "tripcontrol", conferido por SenhaUtils no login.
--
-- Troque este usuario (ou a senha) antes de usar o sistema para valer.
-- ============================================================================

insert into Funcionario (CPFFuncionario, nome, email, senha, perfil, ativo)
values ('00000000000',
        'Ana Silva',
        'ana.silva@tripcontrol.com',
        '$2a$10$pBullaGLKE5cgAD9rvjF..VFhJZWSDn.c2WIBTXUEp8eI6kGO5uU6',
        'FUNCIONARIO',
        true)
on conflict (CPFFuncionario) do nothing;
