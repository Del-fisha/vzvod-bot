package com.company.shtopor.vzvodbot.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class ShiftEndConfirmSessionStore {

    private final Cache<Long, ShiftEndConfirmSession> cache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(25))
            .build();

    public Optional<ShiftEndConfirmSession> get(long chatId) {
        return Optional.ofNullable(cache.getIfPresent(chatId));
    }

    public void put(long chatId, ShiftEndConfirmSession session) {
        cache.put(chatId, session);
    }

    public void clear(long chatId) {
        cache.invalidate(chatId);
    }
}
