package com.AJTBackend.web;

import com.AJTBackend.exception.PassageiroNaoEncontradoException;
import com.AJTBackend.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/*
 * o que testa: os caminhos de erro de /api/passageiros — cada regra do
 * PassageiroRequestDTO (obrigatorio, vazio, tamanho), id inexistente nos 4 verbos,
 * CPF invalido (regra do service) e passageiro em uso na exclusao.
 *
 * como rodar: @WebMvcTest (ErrosWebTestBase), service mockado. sem banco. "mvn test".
 *
 * por que existe: requisito da disciplina — o caminho triste precisa de teste
 * proprio, com o status HTTP certo pra cada erro, nao so o caminho feliz.
 */
class PassageiroErrosWebTest extends ErrosWebTestBase {

    private static final String VALIDO = "{\"nome\":\"Ana\",\"tipoDocumento\":\"PASSAPORTE\",\"documento\":\"X1\"}";

    static Stream<Arguments> corposInvalidos() {
        return Stream.of(
                Arguments.of("sem nome", "{\"tipoDocumento\":\"CPF\",\"documento\":\"1\"}", "nome: Nome é obrigatório"),
                Arguments.of("nome so com espacos", "{\"nome\":\"   \",\"tipoDocumento\":\"CPF\",\"documento\":\"1\"}", "nome: Nome é obrigatório"),
                Arguments.of("nome maior que 100", "{\"nome\":\"" + "a".repeat(101) + "\",\"tipoDocumento\":\"CPF\",\"documento\":\"1\"}", "100 caracteres"),
                Arguments.of("sem tipo de documento", "{\"nome\":\"Ana\",\"documento\":\"1\"}", "tipoDocumento: Tipo de documento é obrigatório"),
                Arguments.of("tipo maior que 20", "{\"nome\":\"Ana\",\"tipoDocumento\":\"" + "T".repeat(21) + "\",\"documento\":\"1\"}", "20 caracteres"),
                Arguments.of("sem documento", "{\"nome\":\"Ana\",\"tipoDocumento\":\"CPF\"}", "documento: Documento é obrigatório"),
                Arguments.of("documento maior que 50", "{\"nome\":\"Ana\",\"tipoDocumento\":\"RG\",\"documento\":\"" + "9".repeat(51) + "\"}", "50 caracteres"),
                Arguments.of("nacionalidade maior que 50", "{\"nome\":\"Ana\",\"tipoDocumento\":\"RG\",\"documento\":\"1\",\"nacionalidade\":\"" + "b".repeat(51) + "\"}", "50 caracteres"));
    }

    @ParameterizedTest(name = "POST {0} -> 400")
    @MethodSource("corposInvalidos")
    void cadastroInvalidoRetorna400SemChamarOService(String caso, String json, String detalhe) throws Exception {
        esperaValidacao(enviar(post("/api/passageiros"), json), detalhe);
        verify(passageiroService, never()).criar(any());
    }

    @ParameterizedTest(name = "PUT {0} -> 400")
    @MethodSource("corposInvalidos")
    void edicaoInvalidaRetorna400(String caso, String json, String detalhe) throws Exception {
        esperaValidacao(enviar(put("/api/passageiros/1"), json), detalhe);
        verify(passageiroService, never()).atualizar(any(), any());
    }

    @Test
    void patchRecusaTextoVazioMasAceitaCampoAusente() throws Exception {
        esperaValidacao(enviar(patch("/api/passageiros/1"), "{\"nome\":\"\"}"), "nome: Nome não pode ser vazio");
        esperaValidacao(enviar(patch("/api/passageiros/1"), "{\"documento\":\" \"}"), "documento: Documento não pode ser vazio");
        verify(passageiroService, never()).atualizarParcial(any(), any());
    }

    @Test
    void corpoAusenteOuMalformadoRetorna400() throws Exception {
        esperaErro(enviar(post("/api/passageiros"), ""), 400, "Requisição inválida", "corpo da requisição");
        esperaErro(enviar(post("/api/passageiros"), "{\"nome\":"), 400, "Requisição inválida", "corpo da requisição");
    }

    @Test
    void idInexistenteRetorna404NosQuatroVerbos() throws Exception {
        when(passageiroService.buscarPorId(99L)).thenThrow(new PassageiroNaoEncontradoException(99L));
        when(passageiroService.atualizar(eq(99L), any())).thenThrow(new PassageiroNaoEncontradoException(99L));
        when(passageiroService.atualizarParcial(eq(99L), any())).thenThrow(new PassageiroNaoEncontradoException(99L));
        doThrow(new PassageiroNaoEncontradoException(99L)).when(passageiroService).deletar(99L);

        esperaErro(chamar(get("/api/passageiros/99")), 404, "Recurso não encontrado", "99");
        esperaErro(enviar(put("/api/passageiros/99"), VALIDO), 404, "Recurso não encontrado", "99");
        esperaErro(enviar(patch("/api/passageiros/99"), "{\"nome\":\"Bia\"}"), 404, "Recurso não encontrado", "99");
        esperaErro(chamar(delete("/api/passageiros/99")), 404, "Recurso não encontrado", "99");
    }

    @Test
    void cpfInvalidoRetorna400DeRegraDeNegocio() throws Exception {
        when(passageiroService.criar(any())).thenThrow(new RegraNegocioException("CPF inválido: confira os 11 dígitos."));

        esperaErro(enviar(post("/api/passageiros"), "{\"nome\":\"Ana\",\"tipoDocumento\":\"CPF\",\"documento\":\"123\"}"),
                400, "Regra de negócio", "CPF inválido");
    }

    @Test
    void excluirPassageiroEmUsoRetorna409() throws Exception {
        doThrow(new DataIntegrityViolationException("fk")).when(passageiroService).deletar(1L);

        esperaErro(chamar(delete("/api/passageiros/1")), 409, "Conflito de dados", "em uso");
    }

    @Test
    void idComTipoErradoRetorna400() throws Exception {
        esperaErro(chamar(get("/api/passageiros/abc")), 400, "Parâmetro inválido", "id");
    }
}
