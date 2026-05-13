package com.company.shtopor.vzvodbot.telegram;

import com.company.shtopor.vzvodbot.core.BotColleagueItemDto;
import com.company.shtopor.vzvodbot.core.BotShiftItemDto;
import com.company.shtopor.vzvodbot.core.BotShiftUpsertRequestDto;
import com.company.shtopor.vzvodbot.core.CoreProfileClient;
import com.company.shtopor.vzvodbot.session.ShiftQuickEditPendingStore;
import com.company.shtopor.vzvodbot.session.ShiftQuickEditPendingStore.Pending;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Быстрое редактирование своей смены: дата, время начала, добавление напарников (PUT в Core).
 */
@Component
public class ShiftQuickEditHandler {

    private static final Logger log = LoggerFactory.getLogger(ShiftQuickEditHandler.class);

    public static final String PREFIX_EDIT_MENU = "sxm";
    public static final String PREFIX_EDIT_DATE = "sxd";
    public static final String PREFIX_EDIT_START = "sxp";
    public static final String PREFIX_EDIT_CANCEL = "sxz";
    /** Начать выбор напарника для смены: {@code sxnp|shiftUuid} */
    public static final String PREFIX_EDIT_PARTNER = "sxnp";
    /** Отделение коллег: {@code sxnd|shiftUuid|1|2} */
    public static final String PREFIX_EDIT_PARTNER_DEPT = "sxnd";
    /** Выбран коллега: {@code sxnc|serviceInfoUuid} (shift в pending) */
    public static final String PREFIX_EDIT_PARTNER_COL = "sxnc";
    /** Следующая страница списка: {@code sxng|page} (отделение и смена в pending) */
    public static final String PREFIX_EDIT_PARTNER_MORE = "sxng";

    private static final DateTimeFormatter DATE_RU = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru-RU"));
    private static final Pattern DATE_DMY = Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{4}$");

    private final CoreProfileClient coreProfileClient;
    private final ShiftQuickEditPendingStore pendingStore;

    public ShiftQuickEditHandler(CoreProfileClient coreProfileClient, ShiftQuickEditPendingStore pendingStore) {
        this.coreProfileClient = coreProfileClient;
        this.pendingStore = pendingStore;
    }

    public void clear(long chatId) {
        pendingStore.clear(chatId);
    }

    public boolean clearIfAwaitingDate(long chatId) {
        var o = pendingStore.get(chatId);
        if (o.isEmpty() || o.get().kind() != ShiftQuickEditPendingStore.Kind.AWAIT_DATE) {
            return false;
        }
        pendingStore.clear(chatId);
        return true;
    }

    public boolean clearIfPartnerPick(long chatId) {
        var o = pendingStore.get(chatId);
        if (o.isEmpty() || o.get().kind() != ShiftQuickEditPendingStore.Kind.PARTNER_PICK) {
            return false;
        }
        pendingStore.clear(chatId);
        return true;
    }

    public boolean isQuickEditCallback(String data) {
        if (data == null) {
            return false;
        }
        return data.startsWith(PREFIX_EDIT_MENU + "|")
                || data.startsWith(PREFIX_EDIT_DATE + "|")
                || data.startsWith(PREFIX_EDIT_START + "|")
                || PREFIX_EDIT_CANCEL.equals(data)
                || data.startsWith(PREFIX_EDIT_PARTNER + "|")
                || data.startsWith(PREFIX_EDIT_PARTNER_DEPT + "|")
                || data.startsWith(PREFIX_EDIT_PARTNER_COL + "|")
                || data.startsWith(PREFIX_EDIT_PARTNER_MORE + "|");
    }

    public boolean handleCallback(TelegramLongPollingBot bot, long chatId, String data, String cqId)
            throws TelegramApiException {
        if (!isQuickEditCallback(data)) {
            return false;
        }
        answer(bot, cqId);
        if (PREFIX_EDIT_CANCEL.equals(data)) {
            pendingStore.clear(chatId);
            bot.execute(SendMessage.builder().chatId(chatId).text("Редактирование отменено.").build());
            return true;
        }
        if (data.startsWith(PREFIX_EDIT_MENU + "|")) {
            try {
                UUID shiftId = UUID.fromString(data.substring((PREFIX_EDIT_MENU + "|").length()));
                sendMenu(bot, chatId, shiftId);
            } catch (IllegalArgumentException e) {
                log.debug("bad sxm {}", data);
            }
            return true;
        }
        if (data.startsWith(PREFIX_EDIT_PARTNER + "|")) {
            try {
                UUID shiftId = UUID.fromString(data.substring((PREFIX_EDIT_PARTNER + "|").length()));
                BotShiftItemDto cur = coreProfileClient.getShift(chatId, shiftId);
                if (cur.endTime() != null) {
                    bot.execute(SendMessage.builder()
                            .chatId(chatId)
                            .text("Редактировать можно только активную (незавершённую) смену.")
                            .build());
                    return true;
                }
                sendPartnerDeptStep(bot, chatId, shiftId);
            } catch (IllegalArgumentException e) {
                log.debug("bad sxnp {}", data);
            } catch (CoreProfileClient.CoreProfileException e) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(editErrorHint(e.getStatus()))
                        .build());
            }
            return true;
        }
        if (data.startsWith(PREFIX_EDIT_PARTNER_DEPT + "|")) {
            try {
                String rest = data.substring((PREFIX_EDIT_PARTNER_DEPT + "|").length());
                int last = rest.lastIndexOf('|');
                if (last <= 0 || last >= rest.length() - 1) {
                    return true;
                }
                UUID shiftId = UUID.fromString(rest.substring(0, last));
                int dep = Integer.parseInt(rest.substring(last + 1));
                if (dep != 1 && dep != 2) {
                    return true;
                }
                BotShiftItemDto cur = coreProfileClient.getShift(chatId, shiftId);
                if (cur.endTime() != null) {
                    bot.execute(SendMessage.builder()
                            .chatId(chatId)
                            .text("Редактировать можно только активную (незавершённую) смену.")
                            .build());
                    return true;
                }
                pendingStore.put(chatId, Pending.partnerPick(shiftId, dep, 0));
                sendPartnerColleagueStep(bot, chatId, shiftId, dep, 0);
            } catch (IllegalArgumentException e) {
                log.debug("bad sxnd {}", data);
            } catch (CoreProfileClient.CoreProfileException e) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(editErrorHint(e.getStatus()))
                        .build());
            }
            return true;
        }
        if (data.startsWith(PREFIX_EDIT_PARTNER_MORE + "|")) {
            try {
                int page = Integer.parseInt(data.substring((PREFIX_EDIT_PARTNER_MORE + "|").length()));
                if (page < 0) {
                    return true;
                }
                var opt = pendingStore.get(chatId);
                if (opt.isEmpty() || opt.get().kind() != ShiftQuickEditPendingStore.Kind.PARTNER_PICK) {
                    return true;
                }
                Pending p = opt.get();
                if (p.partnerDepartment() == null) {
                    return true;
                }
                pendingStore.put(chatId, Pending.partnerPick(p.shiftId(), p.partnerDepartment(), page));
                sendPartnerColleagueStep(bot, chatId, p.shiftId(), p.partnerDepartment(), page);
            } catch (IllegalArgumentException e) {
                log.debug("bad sxng {}", data);
            }
            return true;
        }
        if (data.startsWith(PREFIX_EDIT_PARTNER_COL + "|")) {
            try {
                UUID colleagueSi = UUID.fromString(data.substring((PREFIX_EDIT_PARTNER_COL + "|").length()));
                var opt = pendingStore.get(chatId);
                if (opt.isEmpty() || opt.get().kind() != ShiftQuickEditPendingStore.Kind.PARTNER_PICK) {
                    return true;
                }
                applyPut(bot, chatId, opt.get().shiftId(), null, null, colleagueSi);
            } catch (IllegalArgumentException e) {
                log.debug("bad sxnc {}", data);
            }
            return true;
        }
        if (data.startsWith(PREFIX_EDIT_DATE + "|")) {
            try {
                UUID shiftId = UUID.fromString(data.substring((PREFIX_EDIT_DATE + "|").length()));
                BotShiftItemDto cur = coreProfileClient.getShift(chatId, shiftId);
                if (cur.endTime() != null) {
                    bot.execute(SendMessage.builder()
                            .chatId(chatId)
                            .text("Редактировать можно только активную (незавершённую) смену.")
                            .build());
                    return true;
                }
                pendingStore.put(chatId, Pending.awaitDate(shiftId));
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Новая дата смены в формате ДД.ММ.ГГГГ одним сообщением.")
                        .build());
            } catch (IllegalArgumentException e) {
                log.debug("bad sxd {}", data);
            } catch (CoreProfileClient.CoreProfileException e) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(editErrorHint(e.getStatus()))
                        .build());
            }
            return true;
        }
        if (data.startsWith(PREFIX_EDIT_START + "|")) {
            String[] p = data.split("\\|");
            if (p.length == 3) {
                try {
                    UUID shiftId = UUID.fromString(p[1]);
                    LocalTime t = parseHmDigits(p[2]);
                    if (t != null) {
                        applyPut(bot, chatId, shiftId, null, t, null);
                    }
                } catch (IllegalArgumentException e) {
                    log.debug("bad sxp {}", data);
                }
            }
            return true;
        }
        return true;
    }

    public boolean handleText(TelegramLongPollingBot bot, long chatId, String text) throws TelegramApiException {
        var opt = pendingStore.get(chatId);
        if (opt.isEmpty() || opt.get().kind() != ShiftQuickEditPendingStore.Kind.AWAIT_DATE) {
            return false;
        }
        String v = text.trim();
        if (!DATE_DMY.matcher(v).matches()) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Нужна дата в формате ДД.ММ.ГГГГ.")
                    .build());
            return true;
        }
        try {
            LocalDate d = LocalDate.from(DATE_RU.parse(v));
            applyPut(bot, chatId, opt.get().shiftId(), d, null, null);
            return true;
        } catch (DateTimeParseException e) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось разобрать дату.")
                    .build());
            return true;
        }
    }

    private void sendPartnerDeptStep(TelegramLongPollingBot bot, long chatId, UUID shiftId) throws TelegramApiException {
        String sid = shiftId.toString();
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(
                        ib("Отделение 1", PREFIX_EDIT_PARTNER_DEPT + "|" + sid + "|1"),
                        ib("Отделение 2", PREFIX_EDIT_PARTNER_DEPT + "|" + sid + "|2")),
                List.of(ib("Отмена", PREFIX_EDIT_CANCEL)));
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Выберите отделение напарника по смене.")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void sendPartnerColleagueStep(TelegramLongPollingBot bot, long chatId, UUID shiftId, int dept, int page)
            throws TelegramApiException {
        BotShiftItemDto shiftSnap;
        try {
            shiftSnap = coreProfileClient.getShift(chatId, shiftId);
        } catch (CoreProfileClient.CoreProfileException e) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(editErrorHint(e.getStatus()))
                    .build());
            return;
        }
        if (shiftSnap.endTime() != null) {
            pendingStore.clear(chatId);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Смена уже завершена, добавить напарника нельзя.")
                    .build());
            return;
        }
        Set<UUID> alreadyInShift = participantsToExcludeFromPicker(shiftSnap);
        try {
            var resp = coreProfileClient.getColleagues(chatId, dept, page);
            List<BotColleagueItemDto> items = resp.items() == null ? List.of() : resp.items();
            if (items.isEmpty()) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("В выбранном отделении нет других сотрудников в строю. Выберите другое отделение («Отмена»).")
                        .build());
                return;
            }
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            boolean anyPickable = false;
            for (BotColleagueItemDto it : items) {
                if (it.serviceInfoId() == null) {
                    continue;
                }
                if (alreadyInShift.contains(it.serviceInfoId())) {
                    continue;
                }
                String label = it.label() == null || it.label().isBlank() ? "Сотрудник" : it.label();
                rows.add(List.of(ib(label, PREFIX_EDIT_PARTNER_COL + "|" + it.serviceInfoId())));
                anyPickable = true;
            }
            if (resp.hasMore()) {
                int next = page + 1;
                rows.add(List.of(ib("Ещё…", PREFIX_EDIT_PARTNER_MORE + "|" + next)));
            }
            rows.add(List.of(ib("Отмена", PREFIX_EDIT_CANCEL)));
            String intro = "Добавить напарника (отделение " + dept + "). Уже в смене не показываются.";
            if (!anyPickable) {
                String hint = resp.hasMore()
                        ? "На этой странице все в списке уже участвуют в смене — нажмите «Ещё…» или выберите другое отделение."
                        : "В этом отделении больше некого добавить (остальные уже в смене).";
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(intro + "\n\n" + hint)
                        .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                        .build());
                return;
            }
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(intro)
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(editErrorHint(e.getStatus()))
                    .build());
        }
    }

    private static Set<UUID> participantsToExcludeFromPicker(BotShiftItemDto shift) {
        Set<UUID> out = new HashSet<>();
        if (shift.otherParticipantServiceInfoIds() != null) {
            out.addAll(shift.otherParticipantServiceInfoIds());
        } else if (shift.partnerServiceInfoId() != null) {
            out.add(shift.partnerServiceInfoId());
        }
        return out;
    }

    private void sendMenu(TelegramLongPollingBot bot, long chatId, UUID shiftId) throws TelegramApiException {
        BotShiftItemDto cur;
        try {
            cur = coreProfileClient.getShift(chatId, shiftId);
            if (cur.endTime() != null) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Редактировать можно только активную (незавершённую) смену.")
                        .build());
                return;
            }
        } catch (CoreProfileClient.CoreProfileException e) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(editErrorHint(e.getStatus()))
                    .build());
            return;
        }
        String sid = shiftId.toString();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(ib("Дата смены", PREFIX_EDIT_DATE + "|" + sid)));
        rows.add(List.of(
                ib("09:00", PREFIX_EDIT_START + "|" + sid + "|0900"),
                ib("10:00", PREFIX_EDIT_START + "|" + sid + "|1000"),
                ib("11:00", PREFIX_EDIT_START + "|" + sid + "|1100")));
        rows.add(List.of(ib("Добавить напарника", PREFIX_EDIT_PARTNER + "|" + sid)));
        rows.add(List.of(ib("Отмена", PREFIX_EDIT_CANCEL)));
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Что изменить в смене? (остальные поля — в веб-клиенте.)")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void applyPut(TelegramLongPollingBot bot, long chatId, UUID shiftId, LocalDate newDate, LocalTime newStart,
                          UUID partnerToSetOrNull)
            throws TelegramApiException {
        try {
            BotShiftItemDto cur = coreProfileClient.getShift(chatId, shiftId);
            if (cur.endTime() != null) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Редактировать можно только активную (незавершённую) смену.")
                        .build());
                pendingStore.clear(chatId);
                return;
            }
            LocalDate date = newDate != null ? newDate : cur.date();
            LocalTime start = newStart != null ? newStart : cur.startTime();
            if (date == null || start == null || cur.route() == null || cur.typeOfShiftId() == null) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("В учёте не хватает данных для сохранения. Откройте смену в веб-клиенте.")
                        .build());
                pendingStore.clear(chatId);
                return;
            }
            BotShiftUpsertRequestDto body = new BotShiftUpsertRequestDto(
                    date,
                    cur.route(),
                    cur.typeOfShiftId(),
                    start,
                    cur.endTime(),
                    partnerToSetOrNull,
                    cur.countOfStatements(),
                    cur.countOfClaims(),
                    cur.ibdWithMigrant(),
                    cur.ibdWithoutMigrant());
            BotShiftItemDto saved = coreProfileClient.updateShift(chatId, shiftId, body);
            pendingStore.clear(chatId);
            boolean partnerOnly = partnerToSetOrNull != null && newDate == null && newStart == null;
            if (partnerOnly) {
                if (!responseContainsParticipant(saved, partnerToSetOrNull)) {
                    BotShiftItemDto again = coreProfileClient.getShift(chatId, shiftId);
                    if (!responseContainsParticipant(again, partnerToSetOrNull)) {
                        bot.execute(SendMessage.builder()
                                .chatId(chatId)
                                .text("Сервер не подтвердил добавление напарника. Проверьте смену в веб-клиенте или повторите позже.")
                                .build());
                        return;
                    }
                }
            }
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(partnerOnly ? "Напарник добавлен в смену." : "Смена обновлена в учёте.")
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            pendingStore.clear(chatId);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(editErrorHint(e.getStatus()))
                    .build());
        }
    }

    private static boolean responseContainsParticipant(BotShiftItemDto dto, UUID serviceInfoId) {
        if (serviceInfoId == null) {
            return false;
        }
        if (dto.otherParticipantServiceInfoIds() != null && dto.otherParticipantServiceInfoIds().contains(serviceInfoId)) {
            return true;
        }
        return serviceInfoId.equals(dto.partnerServiceInfoId());
    }

    private static String editErrorHint(int status) {
        return switch (status) {
            case 400 -> "Запрос отклонён: проверьте дату, время и выбранного напарника.";
            case 403 -> "Нет доступа к этой смене.";
            case 404 -> "Смена не найдена.";
            case 401 -> "Ошибка API-ключа к Core.";
            default -> "Ошибка запроса к Core (код " + status + ").";
        };
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
