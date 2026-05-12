package com.company.shtopor.vzvodbot.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class AddressEditSessionStore {

    private final Cache<Long, AddressEditSession> cache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(20))
            .build();

    public Optional<AddressEditSession> get(long chatId) {
        return Optional.ofNullable(cache.getIfPresent(chatId));
    }

    public void put(long chatId, AddressEditSession session) {
        cache.put(chatId, session);
    }

    public void clear(long chatId) {
        cache.invalidate(chatId);
    }
}
