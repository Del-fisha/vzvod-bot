package com.company.shtopor.vzvodbot.logging;

import com.company.shtopor.vzvodbot.core.BotProfileDto;
import com.company.shtopor.vzvodbot.core.CoreProfileClient;
import com.company.shtopor.vzvodbot.session.BotSession;
import com.company.shtopor.vzvodbot.session.TelegramSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Определение сотрудника для логов Telegram-бота")
class TelegramLogActorResolverTest {

    private static final long CHAT_ID = 1209523557L;

    @Mock
    private TelegramSessionRegistry sessions;

    @Mock
    private CoreProfileClient profileClient;

    private TelegramLogActorResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TelegramLogActorResolver(sessions, profileClient);
    }

    @Test
    @DisplayName("использует ФИО из активной сессии")
    void resolve_fromSession() {
        when(sessions.find(CHAT_ID)).thenReturn(Optional.of(
                new BotSession(UUID.randomUUID(), "Иванов И. О.")
        ));

        assertThat(resolver.resolve(CHAT_ID)).isEqualTo("Иванов И. О.");
    }

    @Test
    @DisplayName("запрашивает профиль в Core, если сессии нет")
    void resolve_fromCoreProfile() {
        when(sessions.find(CHAT_ID)).thenReturn(Optional.empty());
        when(profileClient.getProfile(CHAT_ID)).thenReturn(new BotProfileDto(
                UUID.randomUUID(),
                "Петров П. П.",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(resolver.resolve(CHAT_ID)).isEqualTo("Петров П. П.");
    }

    @Test
    @DisplayName("не возвращает chat id")
    void resolve_unknownUser() {
        when(sessions.find(CHAT_ID)).thenReturn(Optional.empty());
        when(profileClient.getProfile(CHAT_ID)).thenThrow(new CoreProfileClient.CoreProfileException(404, "not found"));

        assertThat(resolver.resolve(CHAT_ID)).isEqualTo("неавторизованный пользователь");
        assertThat(resolver.resolve(CHAT_ID)).doesNotContain("1209523557");
    }
}
