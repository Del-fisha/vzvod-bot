package com.company.shtopor.vzvodbot.telegram;

import com.company.shtopor.vzvodbot.core.BotShiftEndTimeRequestDto;
import com.company.shtopor.vzvodbot.core.CoreProfileClient;
import com.company.shtopor.vzvodbot.session.ShiftEndAwaitingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Завершение смены: выбор времени окончания (колбэки {@code sfe|}, {@code sft|}, {@code sfm|}).
 */
@Component
public class ShiftEndTimeHandler {

    private static final Logger log = LoggerFactory.getLogger(ShiftEndTimeHandler.class);

    public static final String PREFIX_END_PICK = "sfe";
    public static final String PREFIX_END_APPLY = "sft";
    public static final String PREFIX_END_MANUAL = "sfm";

    private static final DateTimeFormatter DATE_RU = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru-RU"));
    private static final DateTimeFormatter TIME_SHORT = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("ru-RU"));

    private final CoreProfileClient coreProfileClient;
    private final ShiftEndAwaitingStore awaitingStore;
    private final MainMenuKeyboardFactory mainMenuKeyboardFactory;

    public ShiftEndTimeHandler(
            CoreProfileClient coreProfileClient,
            ShiftEndAwaitingStore awaitingStore,
            MainMenuKeyboardFactory mainMenuKeyboardFactory
    ) {
        this.coreProfileClient = coreProfileClient;
        this.awaitingStore = awaitingStore;
        this.mainMenuKeyboardFactory = mainMenuKeyboardFactory;
    }

    public void clearAwaiting(long chatId) {
        awaitingStore.clear(chatId);
    }

    /** @return true если ожидался ввод времени и сессия сброшена */
    public boolean clearAwaitingIfPresent(long chatId) {
        if (awaitingStore.get(chatId).isEmpty()) {
            return false;
        }
        awaitingStore.clear(chatId);
        return true;
    }

    /**
     * @return true если префикс относится к завершению смены (в т.ч. «пустой» чат — колбэк погашен)
     */
    public boolean isShiftEndCallback(String data) {
        return data != null && (data.startsWith(PREFIX_END_PICK + "|")
                || data.startsWith(PREFIX_END_APPLY + "|")
                || data.startsWith(PREFIX_END_MANUAL + "|"));
    }

    public boolean handleCallback(TelegramLongPollingBot bot, long chatId, String data, String cqId)
            throws TelegramApiException {
        if (!isShiftEndCallback(data)) {
            return false;
        }
        answer(bot, cqId);
        if (data.startsWith(PREFIX_END_PICK + "|")) {
            try {
                UUID shiftId = UUID.fromString(data.substring((PREFIX_END_PICK + "|").length()));
                sendEndTimePicker(bot, chatId, shiftId);
            } catch (IllegalArgumentException e) {
                log.debug("bad sfe {}", data);
                bot.execute(SendMessage.builder().chatId(chatId).text("Некорректная ссылка на смену.").build());
            }
            return true;
        }
        if (data.startsWith(PREFIX_END_APPLY + "|")) {
            String[] p = data.split("\\|");
            if (p.length == 3) {
                try {
                    UUID shiftId = UUID.fromString(p[1]);
                    LocalTime end = parseHmDigits(p[2]);
                    if (end != null) {
                        applyEndTime(bot, chatId, shiftId, end);
                    } else {
                        bot.execute(SendMessage.builder().chatId(chatId).text("Некорректное время в кнопке.").build());
                    }
                } catch (IllegalArgumentException e) {
                    log.debug("bad sft {}", data);
                    bot.execute(SendMessage.builder().chatId(chatId).text("Некорректная ссылка на смену.").build());
                }
            }
            return true;
        }
        if (data.startsWith(PREFIX_END_MANUAL + "|")) {
            try {
                UUID shiftId = UUID.fromString(data.substring((PREFIX_END_MANUAL + "|").length()));
                awaitingStore.put(chatId, shiftId);
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Введите время окончания смены в формате ЧЧ:ММ (например 20:30).")
                        .build());
            } catch (IllegalArgumentException e) {
                log.debug("bad sfm {}", data);
                bot.execute(SendMessage.builder().chatId(chatId).text("Некорректная ссылка на смену.").build());
            }
            return true;
        }
        return true;
    }

    public boolean handleText(TelegramLongPollingBot bot, long chatId, String text) throws TelegramApiException {
        var opt = awaitingStore.get(chatId);
        if (opt.isEmpty()) {
            return false;
        }
        UUID shiftId = opt.get();
        LocalTime t = parseTimeHm(text.trim());
        if (t == null) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Нужно время в формате ЧЧ:ММ.")
                    .build());
            return true;
        }
        applyEndTime(bot, chatId, shiftId, t);
        return true;
    }

    private void sendEndTimePicker(TelegramLongPollingBot bot, long chatId, UUID shiftId) throws TelegramApiException {
        String sid = shiftId.toString();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                ib("21:00", PREFIX_END_APPLY + "|" + sid + "|2100"),
                ib("22:00", PREFIX_END_APPLY + "|" + sid + "|2200"),
                ib("23:00", PREFIX_END_APPLY + "|" + sid + "|2300")));
        rows.add(List.of(ib("Или ввести самому", PREFIX_END_MANUAL + "|" + sid)));
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Укажите время окончания смены.")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void applyEndTime(TelegramLongPollingBot bot, long chatId, UUID shiftId, LocalTime endTime)
            throws TelegramApiException {
        try {
            var saved = coreProfileClient.setShiftEndTime(chatId, shiftId, new BotShiftEndTimeRequestDto(endTime));
            awaitingStore.clear(chatId);
            String t0 = saved.startTime() != null ? saved.startTime().format(TIME_SHORT) : "—";
            String t1 = saved.endTime() != null ? saved.endTime().format(TIME_SHORT) : "—";
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Смена завершена в учёте: "
                            + (saved.date() != null ? saved.date().format(DATE_RU) : "—")
                            + " · с " + t0 + " по " + t1)
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            awaitingStore.clear(chatId);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(endTimeErrorHint(e.getStatus()))
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        }
    }

    private static String endTimeErrorHint(int status) {
        return switch (status) {
            case 400 -> "Не удалось сохранить: проверьте, что время окончания позже времени начала.";
            case 403 -> "Доступ к этой смене запрещён.";
            case 404 -> "Смена не найдена.";
            case 409 -> "Время окончания уже было указано ранее.";
            case 401 -> "Ошибка API-ключа к Core.";
            default -> "Ошибка запроса к Core (код " + status + ").";
        };
    }

    private static LocalTime parseTimeHm(String v) {
        try {
            return LocalTime.parse(v.trim(), TIME_SHORT);
        } catch (DateTimeParseException e) {
            try {
                return LocalTime.parse(v.trim());
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static LocalTime parseHmDigits(String four) {
        if (four == null || four.length() != 4) {
            return null;
        }
        try {
            int h = Integer.parseInt(four.substring(0, 2));
            int m = Integer.parseInt(four.substring(2, 4));
            return LocalTime.of(h, m);
        } catch (Exception e) {
            return null;
        }
    }

    private static InlineKeyboardButton ib(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    private static void answer(TelegramLongPollingBot bot, String cqId) throws TelegramApiException {
        bot.execute(AnswerCallbackQuery.builder().callbackQueryId(cqId).build());
    }
}
