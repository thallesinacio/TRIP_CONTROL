package com.tripcontrol.util;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/**
 * Aplica as migracoes de banco no start da aplicacao (RNF05 e RNF06).
 *
 * <p>Os scripts ficam em {@code src/main/resources/db/migration}:
 * {@code V1__criar_schema_inicial.sql} e o script escrito pela equipe, sem
 * alteracao; {@code V2__ajustes_fatia_1.sql} acrescenta o que os casos de uso ja
 * implementados exigem; {@code R__carga_inicial.sql} cria o usuario inicial.</p>
 *
 * <p>Com isso, quem for avaliar o projeto cria um banco vazio, abre a aplicacao
 * e o schema se monta sozinho — sem rodar script na mao.</p>
 */
public final class Migracoes {

    private static final String LOCAL_DOS_SCRIPTS = "classpath:db/migration";

    private Migracoes() {
    }

    public static void aplicar(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCAL_DOS_SCRIPTS)
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }
}
