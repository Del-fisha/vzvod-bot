package com.company.shtopor.vzvodbot.telegram;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Service
public class TelegramOutboundService {

    private static final int MAX_MESSAGE_LENGTH = 4000;

    private final VzvodTelegramBot bot;

    public TelegramOutboundService(VzvodTelegramBot bot) {
        this.bot = bot;
    }

    public void sendText(long chatId, String text) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(truncate(text))
                    .build());
        } catch (TelegramApiException e) {
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
