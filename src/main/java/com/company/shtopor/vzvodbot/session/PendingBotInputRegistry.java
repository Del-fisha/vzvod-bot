package com.company.shtopor.vzvodbot.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class PendingBotInputRegistry {

    private final Cache<Long, PendingBotInput> cache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();

    public Optional<PendingBotInput> get(long chatId) {
        return Optional.ofNullable(cache.getIfPresent(chatId));
    }

    public void set(long chatId, PendingBotInput input) {
        cache.put(chatId, input);
    }

    public void clear(long chatId) {
        cache.invalidate(chatId);
    }
}
