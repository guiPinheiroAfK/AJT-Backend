package com.AJTBackend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * o que testa: a documentacao Swagger — esquema Bearer JWT declarado e as respostas 401/403
 * acrescentadas em toda operacao, menos no login (que e publico).
 *
 * como rodar: teste unitario montando um OpenAPI na mao e aplicando o customizer. "mvn test".
 *
 * por que existe: o botao "Authorize" do Swagger depende do esquema bearerAuth; sem ele a
 * demonstracao da API nao consegue mandar o token.
 */
class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void deveDeclararEsquemaBearerJwt_quandoMontaADocumentacao() {
        // when
        OpenAPI openApi = config.customOpenAPI();

        // then
        SecurityScheme esquema = openApi.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(esquema.getScheme()).isEqualTo("bearer");
        assertThat(esquema.getBearerFormat()).isEqualTo("JWT");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
        assertThat(openApi.getSecurity()).singleElement()
                .satisfies(requisito -> assertThat(requisito).containsKey("bearerAuth"));
    }

    @Test
    void deveAcrescentar401E403MenosNoLogin_quandoCustomizaAsOperacoes() {
        // given
        Operation login = new Operation().responses(new ApiResponses());
        Operation listar = new Operation().responses(new ApiResponses());
        Operation criar = new Operation().responses(new ApiResponses());
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/auth/login", new PathItem().post(login))
                .addPathItem("/api/motoristas", new PathItem().get(listar).post(criar)));

        // when
        config.respostasDeSeguranca().customise(openApi);

        // then
        assertThat(login.getResponses()).doesNotContainKeys("401", "403");
        assertThat(listar.getResponses()).containsKeys("401", "403");
        assertThat(criar.getResponses()).containsKeys("401", "403");
    }
}
