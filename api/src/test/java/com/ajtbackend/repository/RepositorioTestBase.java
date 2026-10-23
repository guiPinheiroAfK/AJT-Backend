package com.ajtbackend.repository;

import com.ajtbackend.config.CriptografiaConverter;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/*
 * o que e: a classe base dos testes de repositorio (todas as classes de repository/).
 * nao tem teste proprio: @DataJpaTest sobe so a camada de persistencia (JPA, Flyway e o
 * banco) — sem controllers, seguranca nem services — e cada teste roda numa transacao
 * desfeita no final, entao um nao enxerga os dados do outro. o CriptografiaConverter entra
 * no @Import porque o Passageiro cifra o documento com ele.
 *
 * como rodar: precisa do Docker rodando. "mvn test"; sem Docker, as classes filhas sao
 * puladas em vez de falhar o build (@Testcontainers(disabledWithoutDocker = true)).
 *
 * por que existe: o banco e um PostgreSQL real (replace = NONE) porque as migrations e as
 * consultas usam recursos do Postgres, e um banco em memoria "parecido" esconderia
 * justamente os erros de SQL. o container fica no static{} pelo mesmo motivo da
 * IntegracaoTestBase: sobe uma vez so pra todas as classes filhas.
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
