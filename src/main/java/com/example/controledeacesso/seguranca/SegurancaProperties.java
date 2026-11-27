package com.example.controledeacesso.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do login (prefixo "seguranca" no application.properties).
 *
 * @param habilitada    false deixa todos os endpoints abertos (so para desenvolvimento local)
 * @param adminUsuario  usuario do operador do painel
 * @param adminSenha    senha do operador; em producao defina ADMIN_SENHA no Render
 * @param adminNome     nome exibido no painel e gravado como operador nas liberacoes manuais
 * @param jwtSegredo    chave HMAC do token (minimo 32 caracteres). Vazia = chave aleatoria a cada subida
 * @param jwtValidadeHoras quanto tempo o token vale
 */
@ConfigurationProperties(prefix = "seguranca")
public record SegurancaProperties(
        Boolean habilitada,
        String adminUsuario,
        String adminSenha,
        String adminNome,
        String jwtSegredo,
        Integer jwtValidadeHoras
) {

    public boolean habilitadaEfetiva() {
        return habilitada == null || habilitada;
    }

    public int validadeHorasEfetiva() {
        return jwtValidadeHoras == null || jwtValidadeHoras <= 0 ? 8 : jwtValidadeHoras;
    }

    public String adminNomeEfetivo() {
        return adminNome == null || adminNome.isBlank() ? "Administrador" : adminNome.trim();
    }
}
