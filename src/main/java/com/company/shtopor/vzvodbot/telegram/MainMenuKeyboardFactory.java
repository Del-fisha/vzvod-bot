package com.company.shtopor.vzvodbot.telegram;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Component
public class MainMenuKeyboardFactory {

    public static final String BTN_PROFILE = "📋 Профиль";
    public static final String BTN_SHIFTS = "🔄 Смены";
    public static final String BTN_VACATIONS = "🏖 Отпуска";
    public static final String BTN_BACK = "Назад";

    private final ShiftMetricsHandler shiftMetricsHandler;

    public MainMenuKeyboardFactory(ShiftMetricsHandler shiftMetricsHandler) {
        this.shiftMetricsHandler = shiftMetricsHandler;
    }

    public ReplyKeyboardMarkup build(long chatId) {
        List<KeyboardRow> rows = new ArrayList<>();
        rows.add(new KeyboardRow(List.of(
                KeyboardButton.builder().text(BTN_PROFILE).build(),
                KeyboardButton.builder().text(BTN_SHIFTS).build(),
                KeyboardButton.builder().text(BTN_VACATIONS).build())));
        shiftMetricsHandler.appendOpenShiftRows(chatId, rows);
        rows.add(new KeyboardRow(List.of(KeyboardButton.builder().text(BTN_BACK).build())));
        return ReplyKeyboardMarkup.builder()
                .keyboard(rows)
                .resizeKeyboard(true)
                .build();
    }

    public ReplyKeyboardMarkup backOnly() {
        return ReplyKeyboardMarkup.builder()
                .keyboard(List.of(new KeyboardRow(List.of(KeyboardButton.builder().text(BTN_BACK).build()))))
                .resizeKeyboard(true)
                .build();
    }
}
