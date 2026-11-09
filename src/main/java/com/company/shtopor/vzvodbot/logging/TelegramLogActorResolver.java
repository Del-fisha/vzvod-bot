package com.company.shtopor.vzvodbot.logging;

import com.company.shtopor.vzvodbot.core.BotProfileDto;
import com.company.shtopor.vzvodbot.core.CoreProfileClient;
import com.company.shtopor.vzvodbot.session.TelegramSessionRegistry;
import org.springframework.stereotype.Component;

@Component
public class TelegramLogActorResolver {

    private static final String UNKNOWN_USER = "неавторизованный пользователь";

    private final TelegramSessionRegistry sessions;
    private final CoreProfileClient profileClient;

    public TelegramLogActorResolver(TelegramSessionRegistry sessions, CoreProfileClient profileClient) {
        this.sessions = sessions;
        this.profileClient = profileClient;
    }

    public String resolve(long chatId) {
        return sessions.find(chatId)
                .map(session -> session.displayName())
                .filter(name -> !name.isBlank())
                .orElseGet(() -> lookupProfileName(chatId));
    }

    private String lookupProfileName(long chatId) {
        try {
            BotProfileDto profile = profileClient.getProfile(chatId);
            if (profile != null && profile.displayName() != null && !profile.displayName().isBlank()) {
                return profile.displayName();
            }
        } catch (CoreProfileClient.CoreProfileException ignored) {
            // Нет привязки chatId → сотрудник.
        }
        return UNKNOWN_USER;
    }
}
