package com.AJTBackend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    // toda operacao (menos o login) pode responder 401/403: documenta isso uma vez so, aqui
    @Bean
    public OpenApiCustomizer respostasDeSeguranca() {
        return openApi -> openApi.getPaths().forEach((caminho, item) -> {
            if (caminho.equals("/api/auth/login")) {
                return;
            }
            item.readOperations().forEach(operacao -> {
                operacao.getResponses().addApiResponse("401",
                        new ApiResponse().description("Token ausente, inválido ou expirado"));
                operacao.getResponses().addApiResponse("403",
                        new ApiResponse().description("Perfil sem permissão para esta operação"));
            });
        });
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AJT Backend - Sistema Receptivo")
                        .description("API REST da AJT Viagens e Turismo: passageiros, motoristas, veículos, transfers, ordens de serviço e paradas.\n\n" +
                                "**Como testar aqui:** faça `POST /api/auth/login`, copie o `token` da resposta, clique em **Authorize** (cadeado) e cole o token. " +
                                "Depois é só usar **Try it out** em qualquer endpoint.\n\n" +
                                "**Perfis:** ADMIN, GERENTE, ATENDENTE e MOTORISTA — cada um enxerga e altera recursos diferentes (401 = sem token; 403 = sem permissão).")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
