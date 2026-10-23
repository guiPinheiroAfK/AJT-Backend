package com.AJTBackend.repository;

import com.AJTBackend.config.CriptografiaConverter;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/*
 * base dos testes de repositorio: @DataJpaTest sobe so a camada de persistencia (JPA,
 * Flyway e o banco) — sem controllers, seguranca nem services — e cada teste roda numa
 * transacao desfeita no final, entao um nao enxerga os dados do outro.
 *
 * o banco e um PostgreSQL real (replace = NONE): as migrations e as consultas usam recursos
 * do Postgres, e um banco em memoria "parecido" esconderia justamente os erros de SQL.
 * container no static{} pelo mesmo motivo da IntegracaoTestBase: sobe uma vez pra todas
 * as classes filhas. sem Docker, as classes sao puladas.
 *
 * o CriptografiaConverter entra no @Import porque o Passageiro cifra o documento com ele.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(CriptografiaConverter.class)
@Testcontainers(disabledWithoutDocker = true)
abstract class RepositorioTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }
}
