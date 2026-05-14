package com.company.shtopor.vzvodbot.api;

import com.company.shtopor.vzvodbot.telegram.TelegramOutboundService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/telegram")
public class InternalTelegramSendController {

    private final TelegramOutboundService telegramOutboundService;
    private final String internalToken;

    public InternalTelegramSendController(
            TelegramOutboundService telegramOutboundService,
            @Value("${internal.api.token:}") String internalToken
    ) {
        this.telegramOutboundService = telegramOutboundService;
        this.internalToken = internalToken;
    }

    @PostMapping("/send")
    public void send(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestBody @Valid TelegramSendRequest request
    ) {
        requireInternalToken(token);
        telegramOutboundService.sendText(request.chatId(), request.text());
    }

    private void requireInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
