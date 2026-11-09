package com.company.shtopor.vzvodbot.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class RemoteLoggingClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteLoggingClient.class);

    private final RestClient restClient;
    private final String ingestToken;
    private final boolean enabled;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "logging-client");
        t.setDaemon(true);
        return t;
    });

    public RemoteLoggingClient(
            @Value("${app.logging-service.base-url:}") String baseUrl,
            @Value("${app.logging-service.ingest-token:}") String ingestToken
    ) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        this.enabled = !url.isEmpty();
        this.ingestToken = ingestToken == null ? "" : ingestToken.trim();
        this.restClient = enabled ? RestClient.builder().baseUrl(url).build() : null;
    }

    public void logMain(String message) {
        send("TELEGRAM_BOT", "MAIN", message);
    }

    public void logError(String message) {
        send("TELEGRAM_BOT", "ERROR", message);
    }

    private void send(String service, String category, String message) {
        if (!enabled || message == null || message.isBlank()) {
            return;
        }
        executor.execute(() -> {
            try {
                RestClient.RequestBodySpec spec = restClient.post()
                        .uri("/api/logs")
                        .contentType(MediaType.APPLICATION_JSON);
                if (!ingestToken.isEmpty()) {
                    spec = spec.header("X-Internal-Token", ingestToken);
                }
                spec.body(new Payload(service, category, message.trim()))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.debug("logging-service: {}", e.toString());
            }
        });
    }

    private record Payload(String service, String category, String message) {
    }
}
