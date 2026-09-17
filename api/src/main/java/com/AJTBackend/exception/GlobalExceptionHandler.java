package com.AJTBackend.exception;

import com.AJTBackend.dto.ErroResponseDTO;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 404 - qualquer XNaoEncontradoException (Motorista, Passageiro, Transfer, ...)
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponseDTO> handleRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        return resposta(HttpStatus.NOT_FOUND, "Recurso não encontrado", ex.getMessage(), null);
    }

    // 404 - rota inexistente (sem isso caia no fallback 500)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResponseDTO> handleRotaInexistente(NoResourceFoundException ex) {
        return resposta(HttpStatus.NOT_FOUND, "Recurso não encontrado",
                "Endpoint '/" + ex.getResourcePath() + "' não existe", null);
    }

    // 400 - qualquer XJaCadastradoException (CNH, placa, username, ...)
    @ExceptionHandler(RegistroDuplicadoException.class)
    public ResponseEntity<ErroResponseDTO> handleRegistroDuplicado(RegistroDuplicadoException ex) {
        return resposta(HttpStatus.BAD_REQUEST, "Erro de validação", ex.getMessage(), null);
    }

    // 400 - regra de negocio violada
    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResponseDTO> handleRegraNegocio(RegraNegocioException ex) {
        return resposta(HttpStatus.BAD_REQUEST, "Regra de negócio", ex.getMessage(), null);
    }

    // 400 - erro de validação (@Valid / @Validated nos DTOs de request)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponseDTO> handleValidacao(MethodArgumentNotValidException ex) {
        List<String> detalhes = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .toList();

        return resposta(HttpStatus.BAD_REQUEST, "Erro de validação", "Um ou mais campos estão inválidos", detalhes);
    }

    // 400 - validacao de parametros de metodo (@RequestParam, @PathVariable com constraints)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErroResponseDTO> handleValidacaoParametros(HandlerMethodValidationException ex) {
        List<String> detalhes = ex.getAllErrors()
                .stream()
                .map(erro -> erro.getDefaultMessage())
                .toList();

        return resposta(HttpStatus.BAD_REQUEST, "Erro de validação", "Um ou mais parâmetros estão inválidos", detalhes);
    }

    // 400 - parametro obrigatorio ausente na query string
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErroResponseDTO> handleParametroAusente(MissingServletRequestParameterException ex) {
        return resposta(HttpStatus.BAD_REQUEST, "Parâmetro inválido",
                "O parâmetro '" + ex.getParameterName() + "' é obrigatório", null);
    }

    // 400 - ordenacao por campo inexistente (ex: ?sort=xpto)
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErroResponseDTO> handleOrdenacaoInvalida(PropertyReferenceException ex) {
        return resposta(HttpStatus.BAD_REQUEST, "Parâmetro inválido",
                "Não é possível ordenar pelo campo '" + ex.getPropertyName() + "'", null);
    }

    // 409 - violação de integridade (ex: FK, tentar excluir registro referenciado)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResponseDTO> handleIntegridade(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());

        return resposta(HttpStatus.CONFLICT, "Conflito de dados",
                "Não foi possível concluir a operação: o registro está em uso ou viola uma restrição do banco", null);
    }

    // 400 - parâmetro de URL com tipo incompatível (ex: /api/motoristas/abc)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResponseDTO> handleTipoInvalido(MethodArgumentTypeMismatchException ex) {
        return resposta(HttpStatus.BAD_REQUEST, "Parâmetro inválido",
                "O valor '" + ex.getValue() + "' é inválido para o parâmetro '" + ex.getName() + "'", null);
    }

    // 400 - JSON malformado, ou valor fora do enum (ex: status "XPTO")
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponseDTO> handleJsonInvalido(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof InvalidFormatException ife && ife.getTargetType() != null
                && ife.getTargetType().isEnum() && !ife.getPath().isEmpty()) {
            String campo = ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            String aceitos = Arrays.stream(ife.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));

            return resposta(HttpStatus.BAD_REQUEST, "Erro de validação", "Um ou mais campos estão inválidos",
                    List.of(campo + ": valor '" + ife.getValue() + "' inválido. Aceitos: " + aceitos));
        }

        return resposta(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "O corpo da requisição está ausente ou mal formatado", null);
    }

    // 405 - verbo HTTP não suportado pelo endpoint
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResponseDTO> handleMetodoNaoSuportado(HttpRequestMethodNotSupportedException ex) {
        return resposta(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido", ex.getMessage(), null);
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErroResponseDTO> handleCredenciaisInvalidas(CredenciaisInvalidasException ex) {
        // 401, não 400 — é problema de autenticação
        return resposta(HttpStatus.UNAUTHORIZED, "Falha na autenticação", ex.getMessage(), null);
    }

    // 403 - regra de permissao checada dentro do service (ex: motorista editando parada)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResponseDTO> handleAcessoNegado(AccessDeniedException ex) {
        return resposta(HttpStatus.FORBIDDEN, "Acesso negado", ex.getMessage(), null);
    }

    // 429 - bloqueio temporario de login
    @ExceptionHandler(MuitasTentativasException.class)
    public ResponseEntity<ErroResponseDTO> handleMuitasTentativas(MuitasTentativasException ex) {
        return resposta(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas", ex.getMessage(), null);
    }

    // 502 - falha ao consumir a API externa de cotacao
    @ExceptionHandler(CotacaoIndisponivelException.class)
    public ResponseEntity<ErroResponseDTO> handleCotacaoIndisponivel(CotacaoIndisponivelException ex) {
        log.warn("Falha ao consultar API de cotacao: {}", ex.getMessage());
        return resposta(HttpStatus.BAD_GATEWAY, "Servico externo indisponível", ex.getMessage(), null);
    }

    // 500 - fallback genérico, pra qualquer coisa não prevista
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponseDTO> handleGenerico(Exception ex) {
        log.error("Erro inesperado", ex);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado. Tente novamente mais tarde.", null);
    }

    private ResponseEntity<ErroResponseDTO> resposta(HttpStatus status, String erro, String mensagem, List<String> detalhes) {
        ErroResponseDTO corpo = new ErroResponseDTO(LocalDateTime.now(), status.value(), erro, mensagem, detalhes);
        return ResponseEntity.status(status).body(corpo);
    }
}
