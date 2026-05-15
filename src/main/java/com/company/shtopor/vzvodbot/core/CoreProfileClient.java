package com.company.shtopor.vzvodbot.core;

import com.company.shtopor.vzvodbot.config.CoreClientProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;

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
        return jsonBody(restClient.get()
                .uri("/api/bot/me/profile")
                .header("X-Telegram-Chat-Id", Long.toString(telegramChatId)), BotProfileDto.class);
    }

    public BotProfileDto updateProfile(long telegramChatId, BotProfilePatchDto patch) {
        return jsonBody(restClient.put()
                .uri("/api/bot/me/profile")
                .header("X-Telegram-Chat-Id", Long.toString(telegramChatId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(patch), BotProfileDto.class);
    }

    public BotShiftsResponseDto getShifts(long telegramChatId) {
        return jsonBody(restClient.get()
                .uri("/api/bot/me/shifts")
                .header("X-Telegram-Chat-Id", Long.toString(telegramChatId)), BotShiftsResponseDto.class);
    }

    public BotShiftItemDto getShift(long telegramChatId, java.util.UUID shiftId) {
        return jsonBody(restClient.get()
                .uri("/api/bot/me/shifts/{id}", shiftId)
                .header("X-Telegram-Chat-Id", Long.toString(telegramChatId)), BotShiftItemDto.class);
    }

    public BotVacationsResponseDto getVacations(long telegramChatId) {
        return jsonBody(restClient.get()
                .uri("/api/bot/me/vacations")
                .header("X-Telegram-Chat-Id", Long.toString(telegramChatId)), BotVacationsResponseDto.class);
    }

    public BotEventsResponseDto getEvents(long telegramChatId) {
        return jsonBody(restClient.get()
                .uri("/api/bot/me/events")
                .header("X-Telegram-Chat-Id", Long.toString(telegramChatId)), BotEventsResponseDto.class);
    }

    public BotColleaguesResponseDto getColleagues(long telegramChatId, int department, int page) {
        return getColleagues(telegramChatId, department, page, null);
    }

    public BotColleaguesResponseDto getColleagues(long telegramChatId, int department, int page,
                                                  java.util.UUID excludeShiftId) {
        return jsonBody(restClient.get()
                .uri(uriBuilder -> {
                    var b = uriBuilder.path("/api/bot/me/colleagues")
                            .queryParam("department", department)
                            .queryParam("page", page);
                    if (excludeShiftId != null) {
                        b.queryParam("excludeShiftId", excludeShiftId);
                    }
                    return b.build();
                })
                .header("X-Telegram-Chat-Id", Long.toString(telegramChatId)), BotColleaguesResponseDto.class);
    }

    public BotShiftItemDto createShift(long telegramChatId, BotShiftUpsertRequestDto body) {
        return jsonBody(restClient.post()
                .uri("/api/bot/me/shifts")
                .header("X-Telegram-Chat-Id", Long.toString(telegramChatId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body), BotShiftItemDto.class);
    }

    public BotShiftItemDto updateShift(long telegramChatId, java.util.UUID shiftId, BotShiftUpsertRequestDto body) {
        return jsonBody(restClient.put()
                .uri("/api/bot/me/shifts/{id}", shiftId)
                .header("X-Telegram-Chat-Id", Long.toString(telegramChatId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body), BotShiftItemDto.class);
    }

    public BotShiftItemDto setShiftEndTime(long telegramChatId, java.util.UUID shiftId, BotShiftEndTimeRequestDto body) {
        return jsonBody(restClient.post()
                .uri("/api/bot/me/shifts/{id}/end-time", shiftId)
                .header("X-Telegram-Chat-Id", Long.toString(telegramChatId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body), BotShiftItemDto.class);
    }

    public BotShiftItemDto adjustIbdWithMigrant(long telegramChatId, java.util.UUID shiftId,
                                                BotShiftMetricDeltaRequestDto body) {
        return jsonBody(restClient.post()
                .uri("/api/bot/me/shifts/{id}/ibd-with-migrant", shiftId)
                .header("X-Telegram-Chat-Id", Long.toString(telegramChatId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body), BotShiftItemDto.class);
    }

    public BotShiftItemDto adjustCountOfStatements(long telegramChatId, java.util.UUID shiftId,
                                                   BotShiftMetricDeltaRequestDto body) {
        return jsonBody(restClient.post()
                .uri("/api/bot/me/shifts/{id}/count-of-statements", shiftId)
                .header("X-Telegram-Chat-Id", Long.toString(telegramChatId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body), BotShiftItemDto.class);
    }

    public BotViolationOptionsResponseDto getViolationOptions() {
        return jsonBody(restClient.get()
                .uri("/api/bot/me/violation-options"), BotViolationOptionsResponseDto.class);
    }

    public BotShiftItemDto createAdministrativeViolation(long telegramChatId, java.util.UUID shiftId,
                                                         BotAdministrativeViolationCreateRequestDto body) {
        return jsonBody(restClient.post()
                .uri("/api/bot/me/shifts/{id}/administrative-violations", shiftId)
                .header("X-Telegram-Chat-Id", Long.toString(telegramChatId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body), BotShiftItemDto.class);
    }

    public BotShiftItemDto createCriminalViolation(long telegramChatId, java.util.UUID shiftId,
                                                   BotCriminalViolationCreateRequestDto body) {
        return jsonBody(restClient.post()
                .uri("/api/bot/me/shifts/{id}/criminal-violations", shiftId)
                .header("X-Telegram-Chat-Id", Long.toString(telegramChatId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body), BotShiftItemDto.class);
    }

    private <T> T jsonBody(RestClient.RequestHeadersSpec<?> request, Class<T> type) {
        try {
            return request.retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes());
                        throw new CoreProfileException(res.getStatusCode().value(), body);
                    })
                    .body(type);
        } catch (CoreProfileException e) {
            throw e;
        } catch (RestClientException e) {
            throw toClientException(e);
        }
    }

    private static CoreProfileException toClientException(RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            return new CoreProfileException(response.getStatusCode().value(), response.getResponseBodyAsString());
        }
        if (e instanceof UnknownContentTypeException) {
            return new CoreProfileException(0,
                    "Сервер Core ответил не JSON (часто HTML). Перезапустите Core из свежей сборки с новыми API /api/bot/me.");
        }
        return new CoreProfileException(0, e.getMessage() == null ? "Ошибка запроса к Core." : e.getMessage());
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

        public String userHint() {
            if (status == 0) {
                String message = getMessage();
                if (message != null && message.contains("Core bot API HTTP 0: ")) {
                    return message.substring("Core bot API HTTP 0: ".length());
                }
                return message == null ? "Ошибка запроса к Core." : message;
            }
            return switch (status) {
                case 400 -> "Некорректный запрос к Core.";
                case 401 -> "Ошибка API-ключа к Core.";
                case 403 -> "Доступ к этой смене запрещён.";
                case 404 -> "Смена или данные не найдены в Core.";
                case 409 -> "Смена уже завершена.";
                default -> "Ошибка запроса к Core (код " + status + ").";
            };
        }
    }
}
