package com.example.controledeacesso.controller;

import com.example.controledeacesso.dto.LiberarCatracaRequest;
import com.example.controledeacesso.entity.Catraca;
import com.example.controledeacesso.entity.Liberacao;
import com.example.controledeacesso.dispositivos.TelemetriaService;
import com.example.controledeacesso.service.LiberacaoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/catracas")
@RequiredArgsConstructor
public class CatracaController {

    private final LiberacaoService liberacaoService;
    private final TelemetriaService telemetria;

    @GetMapping
    public List<Catraca> listar() {
        return liberacaoService.listarCatracas();
    }

    @PostMapping
    public ResponseEntity<Catraca> cadastrar(@RequestBody Catraca catraca) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(liberacaoService.cadastrarCatraca(catraca.nome(), catraca.local()));
    }

    /** Front-end: libera manualmente a catraca. O tablet dela mostra "ACESSO LIBERADO" em ~2 s. */
    @PostMapping("/{id}/liberar")
    public ResponseEntity<Liberacao> liberar(@PathVariable Long id,
                                             @RequestBody(required = false) LiberarCatracaRequest pedido) {
        Liberacao liberacao = liberacaoService.liberar(
                id,
                pedido == null ? null : pedido.operador(),
                pedido == null ? null : pedido.motivo());
        return ResponseEntity.status(HttpStatus.CREATED).body(liberacao);
    }

    /**
     * Tablet/totem: consulta periodica. 200 com a liberacao (ja marcada como confirmada) quando
     * houver uma pendente para esta catraca; 204 quando nao houver.
     */
    @PostMapping("/{id}/liberacao/confirmar")
    public ResponseEntity<Liberacao> confirmar(@PathVariable Long id, HttpServletRequest requisicao) {
        // Cada consulta do tablet e o sinal de vida usado pelo monitoramento (GET /dispositivos/estacoes)
        telemetria.registrarContato(id, ipDoCliente(requisicao));
        return liberacaoService.confirmar(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Front-end: acompanha se o tablet ja confirmou a liberacao. */
    @GetMapping("/liberacoes/{liberacaoId}")
    public ResponseEntity<Liberacao> buscarLiberacao(@PathVariable Long liberacaoId) {
        return liberacaoService.buscar(liberacaoId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Historico das liberacoes manuais (auditoria). */
    @GetMapping("/liberacoes")
    public List<Liberacao> listarLiberacoes(@RequestParam(required = false) Integer limite) {
        return liberacaoService.listarRecentes(limite);
    }

    /** Atras do proxy do Render o IP real vem no X-Forwarded-For (o primeiro da lista). */
    private static String ipDoCliente(HttpServletRequest requisicao) {
        String encaminhado = requisicao.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return requisicao.getRemoteAddr();
    }
}
