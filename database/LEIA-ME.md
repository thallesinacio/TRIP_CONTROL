# Sobre o `script.sql`

Este arquivo é o schema original escrito pela equipe e **não deve mais ser editado
diretamente**.

A aplicação aplica as migrações de `src/main/resources/db/migration/`, onde:

- `V1__criar_schema_inicial.sql` é uma cópia byte a byte deste `script.sql`;
- `V2__ajustes_fatia_1.sql` acrescenta as colunas e constraints que os casos de uso
  já implementados exigem (período e versão da reserva, códigos de negócio,
  dados do cancelamento, perfil/ativo do funcionário, contato do cliente etc.);
- `R__carga_inicial.sql` cria o usuário inicial.

Mudança no schema daqui para a frente entra como uma migração nova (`V3__...`),
nunca alterando um arquivo já aplicado — o Flyway guarda o checksum de cada um e
recusa a execução se o conteúdo mudar depois.

Quando a fatia estiver validada, dá para consolidar V1 + V2 num único script,
recriando o banco do zero uma vez.
