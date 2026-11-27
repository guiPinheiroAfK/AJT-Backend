package com.ajtbackend;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

/*
 * o que testa: o main da aplicacao repassa os argumentos pro Spring subir a API
 * com a classe certa (a que tem @SpringBootApplication, @EnableFeignClients e @EnableCaching).
 *
 * como rodar: teste unitario com o SpringApplication.run mockado: nao sobe servidor
 * nem banco. "mvn test". a subida de verdade ja e coberta pelos testes de integracao.
 *
 * por que existe: trocar a classe passada pro run (ou esquecer os args, que levam o
 * --server.port) so apareceria no deploy.
 */
class AJTBackendApplicationTest {

    @Test
    void deveSubirOSpringComAClassePrincipalEOsArgumentos_quandoMainEhChamado() {
        // given
        String[] args = {"--server.port=9090"};
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {

            // when
            AJTBackendApplication.main(args);

            // then
            spring.verify(() -> SpringApplication.run(AJTBackendApplication.class, args));
        }
    }
}
