package com.company.shtopor.vzvodbot.api;

import jakarta.validation.constraints.NotNull;

public record TelegramRevokeAccessRequest(
        @NotNull Long chatId,
        String message
) {
}
