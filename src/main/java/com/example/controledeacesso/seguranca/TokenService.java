package com.example.controledeacesso.seguranca;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Gera e valida o JWT do login (HS256, chave simetrica em JWT_SEGREDO).
 *
 * Sem JWT_SEGREDO a chave e sorteada na subida: funciona, mas todo mundo precisa logar de novo
 * quando a API reinicia (no Render isso acontece sempre que ela dorme). Em producao defina a variavel.
 */
@Component
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    static final String EMISSOR = "controle-de-acesso-api";
    private static final int TAMANHO_MINIMO_SEGREDO = 32;

    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final Duration validade;
    private final Clock clock;

    public TokenService(SegurancaProperties props, Optional<Clock> clock) {
        SecretKey chave = new SecretKeySpec(segredo(props.jwtSegredo()), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(chave));
        this.decoder = NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
        this.validade = Duration.ofHours(props.validadeHorasEfetiva());
        this.clock = clock.orElse(Clock.systemUTC());
    }

    public TokenGerado gerar(String usuario, String nome) {
        Instant agora = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expira = agora.plus(validade);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISSOR)
                .subject(usuario)
                .claim("nome", nome)
                .issuedAt(agora)
                .expiresAt(expira)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenGerado(token, expira);
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    private static byte[] segredo(String configurado) {
        if (configurado == null || configurado.isBlank()) {
            log.warn("JWT_SEGREDO nao definido: usando uma chave aleatoria. Os logins caem quando a API reiniciar.");
            byte[] aleatorio = new byte[32];
            new SecureRandom().nextBytes(aleatorio);
            return aleatorio;
        }
        byte[] bytes = configurado.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalStateException("JWT_SEGREDO precisa ter pelo menos " + TAMANHO_MINIMO_SEGREDO + " caracteres.");
        }
        return bytes;
    }

    public record TokenGerado(String token, Instant expiraEm) {
    }
}
