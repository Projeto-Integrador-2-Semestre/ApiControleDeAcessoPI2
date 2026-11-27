package com.example.controledeacesso.controller;

import com.example.controledeacesso.dispositivos.BloqueioGeralService;
import com.example.controledeacesso.dispositivos.DispositivoService;
import com.example.controledeacesso.dispositivos.EstacaoDTO;
import com.example.controledeacesso.dispositivos.TelemetriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Monitoramento das estacoes (catraca + totem) usado pela tela "Dispositivos & Catracas". */
@RestController
@RequestMapping("/dispositivos")
@RequiredArgsConstructor
public class DispositivoController {

    private final DispositivoService dispositivos;

    @GetMapping("/estacoes")
    public List<EstacaoDTO> listar() {
        return dispositivos.listar();
    }

    @GetMapping("/estacoes/{id}")
    public EstacaoDTO buscar(@PathVariable long id) {
        return dispositivos.buscar(id);
    }

    @GetMapping("/estacoes/{id}/logs")
    public List<TelemetriaService.Evento> logs(@PathVariable long id) {
        return dispositivos.syslog(id);
    }

    @PostMapping("/estacoes/{id}/ping")
    public DispositivoService.Ping ping(@PathVariable long id) {
        return dispositivos.ping(id);
    }

    @PostMapping("/estacoes/{id}/sincronizar-facial")
    public Map<String, Object> sincronizarFacial(@PathVariable long id) {
        return dispositivos.sincronizarFacial(id);
    }

    /** 501 por enquanto: depende do rele da catraca. */
    @PostMapping("/estacoes/{id}/testar-giro")
    public void testarGiro(@PathVariable long id) {
        dispositivos.comandoNaoSuportado(id, "testar giro");
    }

    /** 501 por enquanto: o app do totem ainda nao recebe comandos remotos. */
    @PostMapping("/estacoes/{id}/reiniciar")
    public void reiniciar(@PathVariable long id) {
        dispositivos.comandoNaoSuportado(id, "reiniciar");
    }

    @GetMapping("/bloqueio-geral")
    public BloqueioGeralService.Estado bloqueioGeral() {
        return dispositivos.bloqueioGeral();
    }

    /** Ativa ({"ativo": true}, padrao quando o corpo vem vazio) ou desativa ({"ativo": false}) o bloqueio geral. */
    @PostMapping("/bloqueio-geral")
    public BloqueioGeralService.Estado alterarBloqueioGeral(@RequestBody(required = false) Map<String, Object> pedido,
                                                            @AuthenticationPrincipal Jwt jwt) {
        boolean ativo = pedido == null || !Boolean.FALSE.equals(pedido.get("ativo"));
        String operador = jwt == null ? null : String.valueOf(jwt.getClaims().getOrDefault("nome", jwt.getSubject()));
        return dispositivos.alterarBloqueioGeral(ativo, operador);
    }
}
