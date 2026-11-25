package com.example.controledeacesso.repositories;

import com.example.controledeacesso.db.D1Client;
import com.example.controledeacesso.dto.LogDTO;
import com.example.controledeacesso.entity.Enum.TipoAcesso;
import com.example.controledeacesso.entity.Log;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogRepositoryTest {

    private static Map<String, Object> linha(long id, String tipo, String dataHora) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("pessoa_cpf", "47811936844");
        row.put("nome", "Pessoa Teste");
        row.put("tipo", tipo);
        row.put("data_hora", dataHora);
        return row;
    }

    @Test
    void leLogsGravadosPelaFaceApiEmMinusculasComSufixoZ() {
        D1Client d1 = mock(D1Client.class);
        when(d1.query(anyString(), any(Object[].class))).thenReturn(List.of(
                linha(38, "saida", "2026-11-13T00:52:58.377Z"),
                linha(1, "ENTRADA", "2026-11-12 10:00:00")));

        List<Log> logs = new LogRepository(d1).buscar(null, null, null, null, 10);

        assertThat(logs).extracting(Log::getTipoAcesso).containsExactly(TipoAcesso.SAIDA, TipoAcesso.ENTRADA);
        LogDTO dto = LogDTO.de(logs.get(0));
        assertThat(dto.tipo()).isEqualTo("saida");
        assertThat(dto.dataHora()).isEqualTo(Instant.parse("2026-11-13T00:52:58.377Z"));
    }

    @Test
    void filtrosViramParametrosDaQuery() {
        D1Client d1 = mock(D1Client.class);
        when(d1.query(anyString(), any(Object[].class))).thenReturn(List.of());

        new LogRepository(d1).buscar("123", TipoAcesso.ENTRADA,
                LocalDateTime.of(2026, 11, 24, 3, 0), LocalDateTime.of(2026, 11, 25, 2, 59, 59), 500);

        verify(d1).query(argThat(sql -> sql.contains("lower(l.tipo) = ?") && sql.contains("datetime(l.data_hora) >= ?")
                        && !sql.contains("p.foto")),
                eq("123"), eq("entrada"), eq("2026-11-24 03:00:00"), eq("2026-11-25 02:59:59"), eq(500));
    }
}
