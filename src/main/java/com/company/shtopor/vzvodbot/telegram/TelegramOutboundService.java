package com.company.shtopor.vzvodbot.telegram;

import com.company.shtopor.vzvodbot.logging.RemoteLoggingClient;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Service
public class TelegramOutboundService {

    private static final int MAX_MESSAGE_LENGTH = 4000;

    private final VzvodTelegramBot bot;
    private final RemoteLoggingClient remoteLogging;

    public TelegramOutboundService(VzvodTelegramBot bot, RemoteLoggingClient remoteLogging) {
        this.bot = bot;
        this.remoteLogging = remoteLogging;
    }

    public void sendText(long chatId, String text) {
        try {
            bot.sendAndLog(SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(truncate(text))
                    .build());
        } catch (TelegramApiException e) {
            remoteLogging.logError("Ошибка отправки в Telegram (chat %d): %s".formatted(chatId, e.getMessage()));
            throw new IllegalStateException("Failed to send telegram message", e);
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_MESSAGE_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_MESSAGE_LENGTH);
    }
}
