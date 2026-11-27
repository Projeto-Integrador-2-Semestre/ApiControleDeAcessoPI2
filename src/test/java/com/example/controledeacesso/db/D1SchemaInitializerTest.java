package com.example.controledeacesso.db;

import com.example.controledeacesso.config.CloudflareD1Properties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class D1SchemaInitializerTest {

    private static List<Map<String, Object>> colunas(String... nomes) {
        return java.util.Arrays.stream(nomes).map(n -> Map.<String, Object>of("name", n)).toList();
    }

    @Test
    void criaSoAsColunasQueFaltam() {
        D1Client d1 = mock(D1Client.class);
        when(d1.query("PRAGMA table_info(pessoa)")).thenReturn(colunas("cpf", "nome", "foto", "status"));
        when(d1.query("PRAGMA table_info(logs)")).thenReturn(colunas("id", "pessoa_cpf", "tipo", "data_hora"));

        new D1SchemaInitializer(d1, mock(CloudflareD1Properties.class)).migrarColunas();

        verify(d1, never()).query("ALTER TABLE pessoa ADD COLUMN status TEXT NOT NULL DEFAULT 'liberado'");
        verify(d1).query("ALTER TABLE pessoa ADD COLUMN motivo_bloqueio TEXT");
        verify(d1).query("ALTER TABLE pessoa ADD COLUMN data_cadastro TEXT");
        verify(d1).query("ALTER TABLE logs ADD COLUMN catraca_id INTEGER");
    }

    @Test
    void tabelaInexistenteNaoEAlterada() {
        D1Client d1 = mock(D1Client.class);
        when(d1.query(anyString())).thenReturn(List.of());

        new D1SchemaInitializer(d1, mock(CloudflareD1Properties.class)).migrarColunas();

        verify(d1, never()).query(org.mockito.ArgumentMatchers.startsWith("ALTER TABLE"));
    }
}
