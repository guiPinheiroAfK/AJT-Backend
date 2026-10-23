package com.AJTBackend.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * o que testa: o contrato de sucesso de cada endpoint — o verbo e a rota existem, o corpo
 * valido passa pela validacao e a resposta vem com o status combinado (200 leitura e
 * edicao, 201 criacao, 204 exclusao). os *ErrosWebTest cobrem o lado contrario.
 *
 * como rodar: @WebMvcTest (ErrosWebTestBase), services mockados (devolvem null, o que
 * basta pra conferir o status). sem banco. "mvn test".
 *
 * por que existe: um teste de erro so prova que o erro sai certo; junto com este fica
 * provado que o mesmo endpoint, com entrada boa, responde com o codigo certo — e que
 * mudar um @ResponseStatus ou um verbo sem querer quebra o build.
 */
class ContratoSucessoWebTest extends ErrosWebTestBase {

    private static final String PASSAGEIRO = "{\"nome\":\"Ana\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"X1\"}";
    private static final String MOTORISTA = "{\"nome\":\"Carlos\",\"cnh\":\"123\"}";
    private static final String VEICULO = "{\"label\":\"Van\",\"placa\":\"ABC1D23\",\"capacidade\":10}";
    private static final String USUARIO = "{\"nome\":\"Maria\",\"username\":\"maria\",\"senha\":\"senha12345\",\"role\":\"ATENDENTE\"}";
    private static final String PONTO = "{\"transferId\":1,\"localColeta\":\"P\",\"latitude\":0,\"longitude\":0}";
    private static final String PARADA = "{\"osId\":1,\"ordemParada\":1,\"localParada\":\"A\"}";
    private static final String OS = "{\"dataServico\":\"2026-10-21\"}";
    private static final String TRANSFER = "{\"dataTransfer\":\"2026-10-21\",\"horaTransfer\":\"10:00:00\",\"origem\":\"A\",\"destino\":\"B\"}";

    static Stream<Arguments> endpoints() {
        return Stream.of(
                // passageiros
                Arguments.of("GET", "/api/passageiros?nome=ana", null, 200),
                Arguments.of("GET", "/api/passageiros/1", null, 200),
                Arguments.of("GET", "/api/passageiros/buscar?nacionalidade=argentina", null, 200),
                Arguments.of("POST", "/api/passageiros", PASSAGEIRO, 201),
                Arguments.of("PUT", "/api/passageiros/1", PASSAGEIRO, 200),
                Arguments.of("PATCH", "/api/passageiros/1", "{\"nome\":\"Bia\"}", 200),
                Arguments.of("DELETE", "/api/passageiros/1", null, 204),
                // motoristas
                Arguments.of("GET", "/api/motoristas?nome=car", null, 200),
                Arguments.of("GET", "/api/motoristas/1", null, 200),
                Arguments.of("GET", "/api/motoristas/buscar?cnh=123", null, 200),
                Arguments.of("POST", "/api/motoristas", MOTORISTA, 201),
                Arguments.of("PUT", "/api/motoristas/1", MOTORISTA, 200),
                Arguments.of("PATCH", "/api/motoristas/1", "{\"telefone\":\"45999990000\"}", 200),
                Arguments.of("DELETE", "/api/motoristas/1", null, 204),
                // veiculos
                Arguments.of("GET", "/api/veiculos", null, 200),
                Arguments.of("GET", "/api/veiculos/1", null, 200),
                Arguments.of("GET", "/api/veiculos/buscar?placa=ABC1D23", null, 200),
                Arguments.of("POST", "/api/veiculos", VEICULO, 201),
                Arguments.of("PUT", "/api/veiculos/1", VEICULO, 200),
                Arguments.of("PATCH", "/api/veiculos/1", "{\"capacidade\":12}", 200),
                Arguments.of("DELETE", "/api/veiculos/1", null, 204),
                // usuarios
                Arguments.of("GET", "/api/usuarios", null, 200),
                Arguments.of("GET", "/api/usuarios/1", null, 200),
                Arguments.of("GET", "/api/usuarios/buscar?username=maria", null, 200),
                Arguments.of("POST", "/api/usuarios", USUARIO, 201),
                Arguments.of("PUT", "/api/usuarios/1", USUARIO, 200),
                Arguments.of("PATCH", "/api/usuarios/1", "{\"ativo\":false}", 200),
                Arguments.of("DELETE", "/api/usuarios/1", null, 204),
                // pontos de coleta
                Arguments.of("GET", "/api/pontos-coleta", null, 200),
                Arguments.of("GET", "/api/pontos-coleta/1", null, 200),
                Arguments.of("GET", "/api/pontos-coleta/transfer/1", null, 200),
                Arguments.of("POST", "/api/pontos-coleta", PONTO, 201),
                Arguments.of("PUT", "/api/pontos-coleta/1", PONTO, 200),
                Arguments.of("PATCH", "/api/pontos-coleta/1", "{\"ordemParada\":2}", 200),
                Arguments.of("DELETE", "/api/pontos-coleta/1", null, 204),
                // paradas
                Arguments.of("GET", "/api/paradas-os", null, 200),
                Arguments.of("GET", "/api/paradas-os/1", null, 200),
                Arguments.of("GET", "/api/paradas-os/ordem-servico/1", null, 200),
                Arguments.of("POST", "/api/paradas-os", PARADA, 201),
                Arguments.of("PUT", "/api/paradas-os/1", PARADA, 200),
                Arguments.of("PATCH", "/api/paradas-os/1", "{\"statusParada\":\"CONCLUIDA\"}", 200),
                Arguments.of("DELETE", "/api/paradas-os/1", null, 204),
                // ordens de servico
                Arguments.of("GET", "/api/ordens-servico?status=ABERTA", null, 200),
                Arguments.of("GET", "/api/ordens-servico/1", null, 200),
                Arguments.of("GET", "/api/ordens-servico/buscar?status=ABERTA", null, 200),
                Arguments.of("GET", "/api/ordens-servico/1/pdf", null, 200),
                Arguments.of("POST", "/api/ordens-servico", OS, 201),
                Arguments.of("PUT", "/api/ordens-servico/1", OS, 200),
                Arguments.of("PATCH", "/api/ordens-servico/1", "{\"status\":\"EM_ANDAMENTO\"}", 200),
                Arguments.of("DELETE", "/api/ordens-servico/1", null, 204),
                // transfers
                Arguments.of("GET", "/api/transfers?semOs=true", null, 200),
                Arguments.of("GET", "/api/transfers/1", null, 200),
                Arguments.of("GET", "/api/transfers/1/passageiros", null, 200),
                Arguments.of("GET", "/api/transfers/buscar?status=CONFIRMADO", null, 200),
                Arguments.of("POST", "/api/transfers", TRANSFER, 201),
                Arguments.of("PUT", "/api/transfers/1", TRANSFER, 200),
                Arguments.of("PATCH", "/api/transfers/1", "{\"destino\":\"C\"}", 200),
                Arguments.of("DELETE", "/api/transfers/1", null, 204),
                // leitura e utilitarios
                Arguments.of("GET", "/api/auditoria?tabela=transfers&registroId=1", null, 200),
                Arguments.of("GET", "/api/painel/resumo?data=2026-10-21", null, 200),
                Arguments.of("GET", "/api/cotacao?de=USD&para=BRL", null, 200),
                Arguments.of("GET", "/api/auth/me", null, 200),
                Arguments.of("PUT", "/api/auth/senha", "{\"senhaAtual\":\"atual1234\",\"novaSenha\":\"novasenha1\"}", 200));
    }

    @ParameterizedTest(name = "{0} {1} -> {3}")
    @MethodSource("endpoints")
    void endpointRespondeComOStatusCombinado(String verbo, String url, String corpo, int esperado) throws Exception {
        // Given
        var requisicao = request(HttpMethod.valueOf(verbo), url).with(admin());
        if (corpo != null) {
            requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }

        // When
        ResultActions resposta = mockMvc.perform(requisicao);

        // Then
        resposta.andExpect(status().is(esperado));
    }
}
