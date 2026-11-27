package com.example.controledeacesso.integracao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Avisa a face_api que a tabela pessoa mudou (cadastro, edicao, bloqueio ou exclusao), chamando
 * POST /faces/reload?forcar=false. Assim o rosto novo e o bloqueio valem na hora, sem esperar o
 * ciclo de 60 s (FACE_CACHE_TTL_SECONDS) da face_api.
 *
 * A chamada roda em segundo plano: a resposta ao front nao espera a face_api, que no plano gratuito
 * do Render pode estar dormindo. Se ela falhar, o ciclo de 60 s da propria face_api cobre.
 * Sem FACE_API_URL configurada, nao faz nada.
 */
@Component
public class FaceApiNotifier {

    private static final Logger log = LoggerFactory.getLogger(FaceApiNotifier.class);

    private final RestClient restClient;
    // Cliente com tempo curto para o /health do painel de monitoramento (nao pode travar a tela)
    private final RestClient restClientRapido;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "face-api-reload");
        t.setDaemon(true);
        return t;
    });
    // Evita empilhar varios reloads seguidos (ex.: cadastro em lote): um pendente ja basta
    private final AtomicBoolean pendente = new AtomicBoolean(false);

    public FaceApiNotifier(RestClient.Builder builder, @Value("${face-api.url:}") String url) {
        if (url == null || url.isBlank()) {
            this.restClient = null;
            this.restClientRapido = null;
            return;
        }
        SimpleClientHttpRequestFactory rapida = new SimpleClientHttpRequestFactory();
        rapida.setConnectTimeout(Duration.ofSeconds(3));
        rapida.setReadTimeout(Duration.ofSeconds(6));
        this.restClientRapido = builder.clone()
                .baseUrl(url.trim().replaceAll("/+$", ""))
                .requestFactory(rapida)
                .build();
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(10));
        // Releitura parcial e rapida, mas a face_api pode estar acordando (cold start do Render)
        fabrica.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = builder.clone()
                .baseUrl(url.trim().replaceAll("/+$", ""))
                .requestFactory(fabrica)
                .build();
    }

    public boolean isConfigurado() {
        return restClient != null;
    }

    /** Pede para a face_api reler a tabela pessoa. Nao bloqueia e nunca lanca excecao. */
    public void pessoasAlteradas() {
        if (restClient == null || !pendente.compareAndSet(false, true)) {
            return;
        }
        executor.submit(() -> {
            pendente.set(false);
            try {
                restClient.post().uri("/faces/reload?forcar=false").retrieve().toBodilessEntity();
                log.info("face_api recarregou o cache de rostos.");
            } catch (RuntimeException e) {
                log.warn("Nao foi possivel avisar a face_api ({}). Ela relê a tabela sozinha em ate 60 s.",
                        e.getMessage());
            }
        });
    }

    /**
     * Estado da face_api para o monitoramento.
     *
     * @param online       respondeu ao GET /health
     * @param pronta       terminou de carregar o modelo e os rostos
     * @param latenciaMs   tempo de resposta do /health, medido pela API
     */
    public record Saude(boolean configurada, boolean online, boolean pronta, Integer rostosEmCache,
                        Boolean antiSpoofing, Long latenciaMs) {

        static Saude naoConfigurada() {
            return new Saude(false, false, false, null, null, null);
        }

        static Saude semResposta() {
            return new Saude(true, false, false, null, null, null);
        }
    }

    /** Consulta o GET /health da face_api (tempo-limite curto). Nunca lanca excecao. */
    @SuppressWarnings("unchecked")
    public Saude consultarSaude() {
        if (restClientRapido == null) {
            return Saude.naoConfigurada();
        }
        long inicio = System.nanoTime();
        try {
            Map<String, Object> corpo = restClientRapido.get().uri("/health").retrieve().body(Map.class);
            long latencia = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
            if (corpo == null) {
                return Saude.semResposta();
            }
            boolean pronta = Boolean.TRUE.equals(corpo.get("pronta"));
            Object rostos = corpo.get("embeddings_em_cache");
            Object antiSpoofing = corpo.get("anti_spoofing");
            return new Saude(true, true, pronta,
                    rostos instanceof Number n ? n.intValue() : null,
                    antiSpoofing instanceof Boolean b ? b : null,
                    latencia);
        } catch (RuntimeException e) {
            log.debug("face_api sem resposta no /health: {}", e.getMessage());
            return Saude.semResposta();
        }
    }

    /**
     * Pede a releitura da tabela pessoa e espera o resultado (botao "Sincronizar" do monitoramento).
     * Lanca excecao se a face_api nao estiver configurada ou nao responder.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> recarregarAgora() {
        if (restClient == null) {
            throw new IllegalStateException("FACE_API_URL nao configurada");
        }
        Map<String, Object> resumo = restClient.post().uri("/faces/reload?forcar=false").retrieve().body(Map.class);
        return resumo == null ? Map.of() : resumo;
    }
}
