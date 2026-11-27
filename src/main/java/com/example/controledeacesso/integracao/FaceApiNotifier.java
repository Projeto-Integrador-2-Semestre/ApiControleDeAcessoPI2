package com.example.controledeacesso.integracao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
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
            return;
        }
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
}
