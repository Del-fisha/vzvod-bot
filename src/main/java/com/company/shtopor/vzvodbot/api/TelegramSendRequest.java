package com.company.shtopor.vzvodbot.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TelegramSendRequest(
        @NotNull Long chatId,
        @NotBlank String text
) {
}
