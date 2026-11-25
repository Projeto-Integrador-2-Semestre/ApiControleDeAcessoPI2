package com.example.controledeacesso.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais e identificadores do banco Cloudflare D1.
 * Valores vem de application.properties / variaveis de ambiente (ver .env.example).
 *
 * @param baseUrl          URL base da API REST do Cloudflare (pode apontar para um D1 falso em testes locais)
 * @param criarTabelas     cria na subida as tabelas que esta API controla (catraca, liberacao_catraca)
 */
@ConfigurationProperties(prefix = "cloudflare.d1")
public record CloudflareD1Properties(
        String accountId,
        String databaseId,
        String apiToken,
        boolean verificarConexao,
        String baseUrl,
        Boolean criarTabelas
) {

    public static final String BASE_URL_PADRAO = "https://api.cloudflare.com/client/v4";

    public String baseUrlEfetiva() {
        return baseUrl == null || baseUrl.isBlank() ? BASE_URL_PADRAO : baseUrl;
    }

    public boolean criarTabelasEfetivo() {
        return criarTabelas == null || criarTabelas;
    }
}
