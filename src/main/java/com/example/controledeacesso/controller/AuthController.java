package com.example.controledeacesso.controller;

import com.example.controledeacesso.seguranca.SegurancaProperties;
import com.example.controledeacesso.seguranca.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;

/**
 * Login do painel. Por enquanto existe um unico operador, definido por variavel de ambiente
 * (ADMIN_USUARIO / ADMIN_SENHA / ADMIN_NOME). Quando houver cadastro de operadores, so a
 * conferencia da senha muda; o token e o resto da API continuam iguais.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final SegurancaProperties props;
    private final TokenService tokens;

    public AuthController(SegurancaProperties props, TokenService tokens) {
        this.props = props;
        this.tokens = tokens;
    }

    /** {"usuario": "...", "senha": "..."} -> {"token", "tipo", "expiraEm", "usuario", "nome"}. 401 se errar. */
    @PostMapping("/login")
    public LoginResponse login(@RequestBody(required = false) LoginRequest pedido) {
        if (pedido == null || vazio(pedido.usuario()) || vazio(pedido.senha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe usuário e senha.");
        }
        if (vazio(props.adminUsuario()) || vazio(props.adminSenha())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Login não configurado na API (defina ADMIN_USUARIO e ADMIN_SENHA).");
        }
        // Compara as duas coisas sempre, em tempo constante, para nao dar pista de qual errou
        boolean usuarioOk = iguais(pedido.usuario().trim(), props.adminUsuario());
        boolean senhaOk = iguais(pedido.senha(), props.adminSenha());
        if (!(usuarioOk & senhaOk)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos.");
        }

        String nome = props.adminNomeEfetivo();
        TokenService.TokenGerado gerado = tokens.gerar(props.adminUsuario(), nome);
        return new LoginResponse(gerado.token(), "Bearer", gerado.expiraEm(), props.adminUsuario(), nome);
    }

    /** Confere se o token ainda vale e devolve quem esta logado. */
    @GetMapping("/me")
    public Map<String, Object> eu(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            // Seguranca desligada (SEGURANCA_HABILITADA=false)
            return Map.of("usuario", "anonimo", "nome", props.adminNomeEfetivo());
        }
        return Map.of("usuario", jwt.getSubject(), "nome", String.valueOf(jwt.getClaims().get("nome")));
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    private static boolean iguais(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public record LoginRequest(String usuario, String senha) {
    }

    public record LoginResponse(String token, String tipo, Instant expiraEm, String usuario, String nome) {
    }
}
