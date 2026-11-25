package com.example.controledeacesso.controller;

import com.example.controledeacesso.dto.LogDTO;
import com.example.controledeacesso.entity.Enum.TipoAcesso;
import com.example.controledeacesso.entity.Log;
import com.example.controledeacesso.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Registros de entrada/saida gravados pela face_api quando o rosto e reconhecido no totem.
 */
@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    /**
     * Lista os logs mais recentes primeiro.
     * Ex.: GET /logs?de=2026-11-24T00:00:00-03:00&ate=2026-11-24T23:59:59-03:00&tipo=entrada&limite=500
     */
    @GetMapping
    public List<LogDTO> listar(
            @RequestParam(required = false) String cpf,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime ate,
            @RequestParam(required = false) Integer limite
    ) {
        TipoAcesso tipoAcesso;
        try {
            tipoAcesso = TipoAcesso.deTexto(tipo);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo deve ser 'entrada' ou 'saida'");
        }
        return logService.buscar(cpf, tipoAcesso, de, ate, limite).stream().map(LogDTO::de).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<LogDTO> buscar(@PathVariable Long id) {
        Log log = logService.buscarLogPorId(id);
        return log != null ? ResponseEntity.ok(LogDTO.de(log)) : ResponseEntity.notFound().build();
    }

    @GetMapping("/pessoa/{cpf}")
    public List<LogDTO> listarDaPessoa(@PathVariable String cpf) {
        return logService.buscarLogsDeUmaPessoaPorCpf(cpf.replaceAll("\\D", "")).stream().map(LogDTO::de).toList();
    }
}
