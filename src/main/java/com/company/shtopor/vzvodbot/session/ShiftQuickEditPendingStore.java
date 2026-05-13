package com.company.shtopor.vzvodbot.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class ShiftQuickEditPendingStore {

    public enum Kind {
        AWAIT_DATE,
        PARTNER_PICK
    }

    public record Pending(UUID shiftId, Kind kind, Integer partnerDepartment, Integer colleaguePage) {

        public static Pending awaitDate(UUID shiftId) {
            return new Pending(shiftId, Kind.AWAIT_DATE, null, null);
        }

        public static Pending partnerPick(UUID shiftId, int department, int page) {
            return new Pending(shiftId, Kind.PARTNER_PICK, department, page);
        }
    }

    private final Cache<Long, Pending> cache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(25))
            .build();

    public Optional<Pending> get(long chatId) {
        return Optional.ofNullable(cache.getIfPresent(chatId));
    }

    public void put(long chatId, Pending pending) {
        cache.put(chatId, pending);
    }

    public void clear(long chatId) {
        cache.invalidate(chatId);
    }
}
