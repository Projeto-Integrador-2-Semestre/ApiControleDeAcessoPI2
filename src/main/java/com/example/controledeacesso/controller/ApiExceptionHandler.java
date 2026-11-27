package com.example.controledeacesso.controller;

import com.example.controledeacesso.db.D1Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

/**
 * Formato unico de erro da API: JSON {"detail": "mensagem"} com o status HTTP correspondente.
 * O front-end mostra o campo "detail" (mensagemDeErro em api.js).
 *
 * Estende ResponseEntityExceptionHandler para que os erros do proprio Spring (parametro faltando,
 * JSON malformado, metodo nao suportado, rota inexistente...) tambem saiam nesse formato, e nao no
 * padrao do Spring Boot ({"timestamp", "error", "path"}), que nao tem "detail".
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Monta a resposta de erro no formato combinado com o front. */
    public static ResponseEntity<Object> erro(HttpStatusCode status, String mensagem) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("detail", mensagem));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> status(ResponseStatusException e) {
        String mensagem = e.getReason() == null ? e.getStatusCode().toString() : e.getReason();
        return erro(e.getStatusCode(), mensagem);
    }

    @ExceptionHandler(D1Exception.class)
    public ResponseEntity<Object> bancoIndisponivel(D1Exception e) {
        log.error("Erro no Cloudflare D1", e);
        return erro(HttpStatus.SERVICE_UNAVAILABLE, "Banco de dados indisponível no momento.");
    }

    /** Qualquer erro nao previsto: registra no log e nao vaza detalhes internos para o cliente. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> inesperado(Exception e) {
        log.error("Erro inesperado", e);
        return erro(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor.");
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return erro(HttpStatus.CONTENT_TOO_LARGE, "A foto é maior que o tamanho permitido.");
    }

    /** Converte o ProblemDetail que o Spring montaria para {"detail": "..."}. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        String mensagem = null;
        if (body instanceof ProblemDetail problema) {
            mensagem = problema.getDetail();
        }
        if (statusCode.value() == 404) {
            mensagem = "Recurso não encontrado.";
        } else if (mensagem == null || mensagem.isBlank()) {
            mensagem = "Requisição inválida.";
        }
        return erro(statusCode, mensagem);
    }
}
