package com.company.shtopor.vzvodbot.session;

import com.company.shtopor.vzvodbot.telegram.TelegramOutboundService;
import org.springframework.stereotype.Service;

@Service
public class TelegramAccessRevokeService {

    private final TelegramBotSessionCleaner sessionCleaner;
    private final TelegramOutboundService telegramOutboundService;

    public TelegramAccessRevokeService(
            TelegramBotSessionCleaner sessionCleaner,
            TelegramOutboundService telegramOutboundService
    ) {
        this.sessionCleaner = sessionCleaner;
        this.telegramOutboundService = telegramOutboundService;
    }

    public void revoke(long chatId, String message) {
        sessionCleaner.clear(chatId);
        if (message != null && !message.isBlank()) {
            telegramOutboundService.sendText(chatId, message);
        }
    }
}
