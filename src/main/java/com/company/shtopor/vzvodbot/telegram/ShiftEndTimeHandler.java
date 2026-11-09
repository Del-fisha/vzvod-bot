package com.company.shtopor.vzvodbot.telegram;

import com.company.shtopor.vzvodbot.session.ShiftEndConfirmSession;
import com.company.shtopor.vzvodbot.session.ShiftEndConfirmSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalTime;
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
    /** Выбор времени при уточнении в потоке «Изменить» */
    public static final String PREFIX_END_APPLY_EDIT = "sfte";
    public static final String PREFIX_END_MANUAL_EDIT = "sfme";

    private final ShiftEndConfirmSessionStore confirmSessionStore;
    private final ShiftEndConfirmationHandler confirmationHandler;

    public ShiftEndTimeHandler(
            ShiftEndConfirmSessionStore confirmSessionStore,
            ShiftEndConfirmationHandler confirmationHandler
    ) {
        this.confirmSessionStore = confirmSessionStore;
        this.confirmationHandler = confirmationHandler;
    }

    public void clearAwaiting(long chatId) {
        confirmSessionStore.clear(chatId);
    }

    public boolean isConfirmFlowActive(long chatId) {
        return confirmationHandler.isActive(chatId);
    }

    /** @return true если ожидался ввод времени и сессия сброшена */
    public boolean clearAwaitingIfPresent(long chatId) {
        var opt = confirmSessionStore.get(chatId);
        if (opt.isEmpty()) {
            return false;
        }
        ShiftEndConfirmSession.EditStep step = opt.get().getStep();
        if (step == ShiftEndConfirmSession.EditStep.AWAIT_MANUAL_TIME
                || step == ShiftEndConfirmSession.EditStep.AWAIT_EDIT_MANUAL_TIME) {
            confirmSessionStore.clear(chatId);
            return true;
        }
        return false;
    }

    public boolean isShiftEndCallback(String data) {
        if (data == null) {
            return false;
        }
        return data.startsWith(PREFIX_END_PICK + "|")
                || data.startsWith(PREFIX_END_APPLY + "|")
                || data.startsWith(PREFIX_END_MANUAL + "|")
                || data.startsWith(PREFIX_END_APPLY_EDIT + "|")
                || data.startsWith(PREFIX_END_MANUAL_EDIT + "|")
                || confirmationHandler.isConfirmCallback(data);
    }

    public boolean handleCallback(TelegramLongPollingBot bot, long chatId, String data, String cqId)
            throws TelegramApiException {
        if (confirmationHandler.handleCallback(bot, chatId, data, cqId)) {
            return confirmationHandler.isConfirmCallback(data);
        }
        if (!isTimePickerCallback(data)) {
            return false;
        }
        answer(bot, cqId);
        if (data.startsWith(PREFIX_END_PICK + "|")) {
            try {
                UUID shiftId = UUID.fromString(data.substring((PREFIX_END_PICK + "|").length()));
                confirmationHandler.sendEndTimePicker(bot, chatId, shiftId, false);
            } catch (IllegalArgumentException e) {
                log.debug("bad sfe {}", data);
                bot.execute(SendMessage.builder().chatId(chatId).text("Некорректная ссылка на смену.").build());
            }
            return true;
        }
        if (data.startsWith(PREFIX_END_APPLY + "|") || data.startsWith(PREFIX_END_APPLY_EDIT + "|")) {
            boolean editFlow = data.startsWith(PREFIX_END_APPLY_EDIT + "|");
            String prefix = editFlow ? PREFIX_END_APPLY_EDIT : PREFIX_END_APPLY;
            String[] p = data.split("\\|");
            if (p.length == 3) {
                try {
                    UUID shiftId = UUID.fromString(p[1]);
                    LocalTime end = parseHmDigits(p[2]);
                    if (end != null) {
                        confirmationHandler.onTimePicked(bot, chatId, shiftId, end);
                    } else {
                        bot.execute(SendMessage.builder().chatId(chatId).text("Некорректное время в кнопке.").build());
                    }
                } catch (IllegalArgumentException e) {
                    log.debug("bad end apply {}", data);
                    bot.execute(SendMessage.builder().chatId(chatId).text("Некорректная ссылка на смену.").build());
                }
            }
            return true;
        }
        if (data.startsWith(PREFIX_END_MANUAL + "|")) {
            try {
                UUID shiftId = UUID.fromString(data.substring((PREFIX_END_MANUAL + "|").length()));
                confirmationHandler.beginAwaitingManualTime(chatId, shiftId);
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
        if (data.startsWith(PREFIX_END_MANUAL_EDIT + "|")) {
            try {
                UUID shiftId = UUID.fromString(data.substring((PREFIX_END_MANUAL_EDIT + "|").length()));
                var opt = confirmSessionStore.get(chatId);
                if (opt.isPresent() && shiftId.equals(opt.get().getShiftId())) {
                    ShiftEndConfirmSession session = opt.get();
                    session.setStep(ShiftEndConfirmSession.EditStep.AWAIT_EDIT_MANUAL_TIME);
                    confirmSessionStore.put(chatId, session);
                }
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Введите время окончания смены в формате ЧЧ:ММ (например 20:30).")
                        .build());
            } catch (IllegalArgumentException e) {
                log.debug("bad sfme {}", data);
                bot.execute(SendMessage.builder().chatId(chatId).text("Некорректная ссылка на смену.").build());
            }
            return true;
        }
        return true;
    }

    public boolean handleText(TelegramLongPollingBot bot, long chatId, String text) throws TelegramApiException {
        return confirmationHandler.handleText(bot, chatId, text);
    }

    private static boolean isTimePickerCallback(String data) {
        return data.startsWith(PREFIX_END_PICK + "|")
                || data.startsWith(PREFIX_END_APPLY + "|")
                || data.startsWith(PREFIX_END_MANUAL + "|")
                || data.startsWith(PREFIX_END_APPLY_EDIT + "|")
                || data.startsWith(PREFIX_END_MANUAL_EDIT + "|");
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

    private static void answer(TelegramLongPollingBot bot, String cqId) throws TelegramApiException {
        bot.execute(AnswerCallbackQuery.builder().callbackQueryId(cqId).build());
    }
}
