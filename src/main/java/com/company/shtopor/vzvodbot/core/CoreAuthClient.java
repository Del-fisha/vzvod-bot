package com.company.shtopor.vzvodbot.core;

import com.company.shtopor.vzvodbot.config.CoreClientProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Service
public class CoreAuthClient {

    private final RestClient restClient;

    public CoreAuthClient(CoreClientProperties props) {
        RestClient.Builder b = RestClient.builder()
                .baseUrl(props.getBaseUrl().replaceAll("/+$", ""));
        if (!props.getApiKey().isBlank()) {
            b.defaultHeader("X-Api-Key", props.getApiKey());
        }
        this.restClient = b.build();
    }

    public AuthResponse authenticate(String phoneNumber, long chatId) {
        AuthRequest body = new AuthRequest(phoneNumber, chatId);
        try {
            return restClient.post()
                    .uri("/api/bot/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(AuthResponse.class);
        } catch (RestClientResponseException e) {
            throw new CoreAuthException(e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    public record AuthRequest(String phoneNumber, long chatId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthResponse(UUID userId, String displayName) {
    }

    public static final class CoreAuthException extends RuntimeException {
        private final int status;

        public CoreAuthException(int status, String body) {
            super("Core /api/bot/auth HTTP " + status + ": " + body);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}
