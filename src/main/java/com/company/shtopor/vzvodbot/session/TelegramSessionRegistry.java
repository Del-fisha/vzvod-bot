package com.company.shtopor.vzvodbot.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class TelegramSessionRegistry {

    private final Cache<Long, BotSession> cache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofHours(24))
            .build();

    public Optional<BotSession> find(long chatId) {
        return Optional.ofNullable(cache.getIfPresent(chatId));
    }

    public void put(long chatId, BotSession session) {
        cache.put(chatId, session);
    }

    public void remove(long chatId) {
        cache.invalidate(chatId);
    }
}
