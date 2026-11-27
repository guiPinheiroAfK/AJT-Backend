package com.ajtbackend.exception;

import com.ajtbackend.dto.ErroResponseDTO;
import com.ajtbackend.model.enums.StatusTransfer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * o que testa: as bordas do 400 de JSON ilegivel no GlobalExceptionHandler que o
 * MockMvc nao produz — valor invalido sem tipo de destino conhecido ou sem o caminho
 * do campo. nos dois casos a resposta e a generica ("corpo ... mal formatado"), sem
 * inventar nome de campo. o caso comum (enum invalido num campo) fica no ValidacaoWebTest.
 *
 * como rodar: teste unitario puro chamando o handler direto. "mvn test".
 *
 * por que existe: o Jackson nem sempre preenche tipo e caminho; se o handler
 * confiasse neles, um JSON estranho virava 500 em vez de 400.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static HttpMessageNotReadableException jsonIlegivel(InvalidFormatException causa) {
        return new HttpMessageNotReadableException("JSON ilegivel", causa, new MockHttpInputMessage(new byte[0]));
    }

    @Test
    void deveResponderOErroGenerico_quandoValorInvalidoNaoTemTipoDeDestino() {
        // given
        InvalidFormatException causa = new InvalidFormatException(null, "valor invalido", "XPTO", null);

        // when
        ResponseEntity<ErroResponseDTO> resposta = handler.handleJsonInvalido(jsonIlegivel(causa));

        // then
        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().erro()).isEqualTo("Requisição inválida");
        assertThat(resposta.getBody().detalhes()).isNull();
    }

    @Test
    void deveResponderOErroGenerico_quandoEnumInvalidoNaoTemCaminhoDoCampo() {
        // given: enum invalido, mas sem dizer em qual campo (path vazio)
        InvalidFormatException causa = new InvalidFormatException(null, "valor invalido", "XPTO", StatusTransfer.class);

        // when
        ResponseEntity<ErroResponseDTO> resposta = handler.handleJsonInvalido(jsonIlegivel(causa));

        // then
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().erro()).isEqualTo("Requisição inválida");
        assertThat(resposta.getBody().mensagem()).contains("mal formatado");
    }

    @Test
    void deveListarOsValoresAceitos_quandoEnumInvalidoTemOCaminhoDoCampo() {
        // given
        InvalidFormatException causa = new InvalidFormatException(null, "valor invalido", "XPTO", StatusTransfer.class);
        causa.prependPath(Object.class, "status");

        // when
        ResponseEntity<ErroResponseDTO> resposta = handler.handleJsonInvalido(jsonIlegivel(causa));

        // then
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().erro()).isEqualTo("Erro de validação");
        assertThat(resposta.getBody().detalhes())
                .singleElement().asString()
                .startsWith("status: valor 'XPTO' inválido. Aceitos: ")
                .contains("CONFIRMADO");
    }
}
