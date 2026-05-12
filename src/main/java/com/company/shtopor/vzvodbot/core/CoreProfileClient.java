package com.company.shtopor.vzvodbot.core;

import com.company.shtopor.vzvodbot.config.CoreClientProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class CoreProfileClient {

    private final RestClient restClient;

    public CoreProfileClient(CoreClientProperties props) {
        RestClient.Builder b = RestClient.builder()
                .baseUrl(props.getBaseUrl().replaceAll("/+$", ""));
        if (!props.getApiKey().isBlank()) {
            b.defaultHeader("X-Api-Key", props.getApiKey());
        }
        this.restClient = b.build();
    }

    public BotProfileDto getProfile(long telegramChatId) {
        try {
            return restClient.get()
                    .uri("/api/bot/me/profile")
                    .header("X-Telegram-Chat-Id", Long.toString(telegramChatId))
                    .retrieve()
                    .body(BotProfileDto.class);
        } catch (RestClientResponseException e) {
            throw new CoreProfileException(e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    public BotProfileDto updateProfile(long telegramChatId, BotProfilePatchDto patch) {
        try {
            return restClient.put()
                    .uri("/api/bot/me/profile")
                    .header("X-Telegram-Chat-Id", Long.toString(telegramChatId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(patch)
                    .retrieve()
                    .body(BotProfileDto.class);
        } catch (RestClientResponseException e) {
            throw new CoreProfileException(e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    public BotShiftsResponseDto getShifts(long telegramChatId) {
        try {
            return restClient.get()
                    .uri("/api/bot/me/shifts")
                    .header("X-Telegram-Chat-Id", Long.toString(telegramChatId))
                    .retrieve()
                    .body(BotShiftsResponseDto.class);
        } catch (RestClientResponseException e) {
            throw new CoreProfileException(e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    public BotVacationsResponseDto getVacations(long telegramChatId) {
        try {
            return restClient.get()
                    .uri("/api/bot/me/vacations")
                    .header("X-Telegram-Chat-Id", Long.toString(telegramChatId))
                    .retrieve()
                    .body(BotVacationsResponseDto.class);
        } catch (RestClientResponseException e) {
            throw new CoreProfileException(e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    public static final class CoreProfileException extends RuntimeException {
        private final int status;

        public CoreProfileException(int status, String body) {
            super("Core bot API HTTP " + status + ": " + body);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}
