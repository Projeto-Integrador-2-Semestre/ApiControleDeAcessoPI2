package com.example.controledeacesso.service;

import com.example.controledeacesso.entity.Enum.TipoAcesso;
import com.example.controledeacesso.entity.Log;
import com.example.controledeacesso.repositories.LogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LogService {

    public static final int LIMITE_PADRAO = 2000;
    public static final int LIMITE_MAXIMO = 10000;

    private final LogRepository logRepository;

    public List<Log> listarLogs() {
        return logRepository.findAll();
    }

    public Log buscarLogPorId(Long id) {
        return logRepository.findById(id).orElse(null);
    }

    public List<Log> buscarLogsDeUmaPessoaPorCpf(String cpf) {
        return logRepository.findByPessoaCpf(cpf);
    }

    /** Busca filtrada; `de` e `ate` podem vir em qualquer fuso e sao convertidos para UTC (padrao do banco). */
    public List<Log> buscar(String cpf, TipoAcesso tipo, OffsetDateTime de, OffsetDateTime ate, Integer limite) {
        int limiteEfetivo = limite == null ? LIMITE_PADRAO : Math.max(1, Math.min(limite, LIMITE_MAXIMO));
        return logRepository.buscar(
                cpf == null ? null : cpf.replaceAll("\\D", ""),
                tipo,
                paraUtc(de),
                paraUtc(ate),
                limiteEfetivo);
    }

    private static LocalDateTime paraUtc(OffsetDateTime valor) {
        return valor == null ? null : valor.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
