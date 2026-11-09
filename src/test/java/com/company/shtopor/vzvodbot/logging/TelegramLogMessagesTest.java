package com.company.shtopor.vzvodbot.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Формат сообщений логов Telegram-бота")
class TelegramLogMessagesTest {

    @Test
    @DisplayName("действие пользователя без chat id")
    void userAction() {
        assertThat(TelegramLogMessages.userAction("Иванов И. О.", "main_menu"))
                .isEqualTo("Иванов И. О.: main_menu")
                .doesNotContain("chat");
    }

    @Test
    @DisplayName("сообщение пользователя без chat id")
    void userMessage() {
        assertThat(TelegramLogMessages.userMessage("Петров П. П.", "/start"))
                .isEqualTo("Петров П. П.: /start");
    }

    @Test
    @DisplayName("ответ программы без chat id")
    void programReply() {
        assertThat(TelegramLogMessages.programReply("Сидоров С. С.", "Главное меню"))
                .isEqualTo("Ответ программы (Сидоров С. С.): Главное меню");
    }

    @Test
    @DisplayName("отправка контакта")
    void contactShared() {
        assertThat(TelegramLogMessages.contactShared("Иванов И. О."))
                .isEqualTo("Иванов И. О. отправил контакт");
    }
}
