package com.example.controledeacesso.seguranca;

import com.example.controledeacesso.controller.ApiExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Protecao dos endpoints com JWT (header "Authorization: Bearer <token>").
 *
 * Ficam abertos so o que nao tem como mandar token:
 *  - GET /health (monitor de uptime e Render);
 *  - POST /auth/login (e onde o token e obtido);
 *  - POST /catracas/{id}/liberacao/confirmar (consulta do tablet; ele so recebe a liberacao que o
 *    operador ja pediu logado, nao consegue abrir a catraca sozinho);
 *  - OPTIONS (preflight do CORS).
 * Todo o resto (/pessoa, /logs, /catracas...) exige login.
 *
 * Com SEGURANCA_HABILITADA=false tudo fica aberto, como era antes (so para desenvolvimento).
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain filtros(HttpSecurity http, SegurancaProperties props, TokenService tokens) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API sem cookie de sessao: o token vai no header
                .cors(Customizer.withDefaults()) // usa as origens do CorsConfig
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable());

        if (!props.habilitadaEfetiva()) {
            log.warn("SEGURANCA_HABILITADA=false: todos os endpoints estao abertos.");
            http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
            return http.build();
        }

        if ("1234".equals(props.adminSenha())) {
            log.warn("ADMIN_SENHA esta com o valor padrao. Defina outra senha antes de publicar a API.");
        }

        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/health", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/catracas/*/liberacao/confirmar").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(tokens.decoder()))
                        .authenticationEntryPoint((req, resp, e) ->
                                responder(resp, HttpStatus.UNAUTHORIZED, "Faça login para continuar.")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, resp, ex) ->
                                responder(resp, HttpStatus.UNAUTHORIZED, "Faça login para continuar."))
                        .accessDeniedHandler((req, resp, ex) ->
                                responder(resp, HttpStatus.FORBIDDEN, "Acesso negado.")));
        return http.build();
    }

    /** Mesmo formato de erro do resto da API ({"detail": "..."}), que o front ja sabe ler. */
    private static void responder(HttpServletResponse resp, HttpStatus status, String mensagem) throws IOException {
        ResponseEntity<Object> erro = ApiExceptionHandler.erro(status, mensagem);
        resp.setStatus(status.value());
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(JsonMapper.builder().build().writeValueAsString(erro.getBody()));
    }
}
