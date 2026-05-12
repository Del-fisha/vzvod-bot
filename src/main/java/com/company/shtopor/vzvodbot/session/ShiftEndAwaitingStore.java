package com.company.shtopor.vzvodbot.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class ShiftEndAwaitingStore {

    private final Cache<Long, UUID> cache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(25))
            .build();

    public Optional<UUID> get(long chatId) {
        return Optional.ofNullable(cache.getIfPresent(chatId));
    }

    public void put(long chatId, UUID shiftId) {
        cache.put(chatId, shiftId);
    }

    public void clear(long chatId) {
        cache.invalidate(chatId);
    }
}
