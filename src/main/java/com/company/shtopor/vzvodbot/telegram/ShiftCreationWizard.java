package com.company.shtopor.vzvodbot.telegram;

import com.company.shtopor.vzvodbot.core.BotColleagueItemDto;
import com.company.shtopor.vzvodbot.core.BotShiftUpsertRequestDto;
import com.company.shtopor.vzvodbot.core.CoreProfileClient;
import com.company.shtopor.vzvodbot.session.AddressEditSessionStore;
import com.company.shtopor.vzvodbot.session.ShiftDraftSession;
import com.company.shtopor.vzvodbot.session.ShiftDraftSessionStore;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Мастер создания смены: дата → маршрут → тип → отделение напарника → коллега → время начала → подтверждение, затем POST в Core.
 */
@Component
public class ShiftCreationWizard {

    private static final Logger log = LoggerFactory.getLogger(ShiftCreationWizard.class);

    private static final int TOTAL_STEPS = 6;

    static final String CB_SHIFT_NEW = "shift_new";
    private static final String PREFIX_SHIFT_DATE = "sdt";
    private static final String PREFIX_SHIFT_ROUTE = "srv";
    private static final String PREFIX_SHIFT_TYPE = "sty";
    private static final String PREFIX_SHIFT_DEPT = "sdd";
    private static final String PREFIX_SHIFT_COLLEAGUE = "sdp";
    private static final String PREFIX_SHIFT_COLPAGE = "sdn";
    private static final String PREFIX_SHIFT_TSTART = "sds";
    private static final String CB_SHIFT_SUBMIT = "sdok";
    private static final String CB_SHIFT_ABORT = "sdno";
    private static final String CB_SHIFT_ADD_PARTNER = "sdap";
    private static final String PREFIX_SHIFT_ADD_DEPT = "sadd";
    private static final String PREFIX_SHIFT_ADD_COL = "sdac";
    private static final String PREFIX_SHIFT_ADD_PAGE = "sdan";
    private static final String CB_SHIFT_ADD_PARTNER_CANCEL = "sdax";

    private static final String[] SHIFT_ROUTE_IDS = {
            "МП 28", "МП 30", "МП 31", "МП 32", "МП 5", "МП 6", "СП 3", "СП 18", "Another"
    };

    private static final String[] SHIFT_ROUTE_LABELS = {
            "МП 28", "МП 30", "МП 31", "МП 32", "МП 5", "МП 6", "СП 3", "СП 18", "Другое"
    };

    private static final DateTimeFormatter DATE_RU = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru-RU"));
    private static final DateTimeFormatter TIME_SHORT = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("ru-RU"));
    private static final Pattern DATE_DMY = Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{4}$");

    private final ShiftDraftSessionStore store;
    private final CoreProfileClient coreProfileClient;
    private final AddressEditSessionStore addressSessions;
    private final ShiftEndAwaitingStore shiftEndAwaitingStore;

    public ShiftCreationWizard(
            ShiftDraftSessionStore store,
            CoreProfileClient coreProfileClient,
            AddressEditSessionStore addressSessions,
            ShiftEndAwaitingStore shiftEndAwaitingStore
    ) {
        this.store = store;
        this.coreProfileClient = coreProfileClient;
        this.addressSessions = addressSessions;
        this.shiftEndAwaitingStore = shiftEndAwaitingStore;
    }

    public boolean isActive(long chatId) {
        return store.get(chatId).isPresent();
    }

    public void clear(long chatId) {
        store.clear(chatId);
    }

    public void onBack(TelegramLongPollingBot bot, long chatId) throws TelegramApiException {
        Optional<ShiftDraftSession> opt = store.get(chatId);
        if (opt.isEmpty()) {
            return;
        }
        ShiftDraftSession s = opt.get();
        if (s.getStep() == 6 && s.getConfirmPartnerPicker() != ShiftDraftSession.ConfirmPartnerPicker.IDLE) {
            if (s.getConfirmPartnerPicker() == ShiftDraftSession.ConfirmPartnerPicker.COLLEAGUE) {
                s.setConfirmPartnerPicker(ShiftDraftSession.ConfirmPartnerPicker.DEPARTMENT);
                store.put(chatId, s);
                sendConfirmAddPartnerDeptStep(bot, chatId);
                return;
            }
            s.setConfirmPartnerPicker(ShiftDraftSession.ConfirmPartnerPicker.IDLE);
            store.put(chatId, s);
            sendConfirm(bot, chatId, s);
            return;
        }
        if (s.getStep() <= 0) {
            store.clear(chatId);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Создание смены отменено.")
                    .build());
            return;
        }
        int was = s.getStep();
        s.setStep(was - 1);
        switch (was) {
            case 5 -> s.setStartTime(null);
            case 4 -> {
                s.clearParticipants();
                s.setColleaguePage(0);
            }
            case 3 -> {
                s.setPartnerDepartment(null);
                s.clearParticipants();
                s.setColleaguePage(0);
            }
            default -> {
            }
        }
        store.put(chatId, s);
        resendStep(bot, chatId, s);
    }

    public void onText(TelegramLongPollingBot bot, long chatId, String text) throws TelegramApiException {
        Optional<ShiftDraftSession> opt = store.get(chatId);
        if (opt.isEmpty()) {
            return;
        }
        ShiftDraftSession s = opt.get();
        String v = text.trim();
        try {
            switch (s.getStep()) {
                case 0 -> {
                    if (!DATE_DMY.matcher(v).matches()) {
                        bot.execute(SendMessage.builder()
                                .chatId(chatId)
                                .text("Нужна дата в формате ДД.ММ.ГГГГ или кнопка «Сегодня».")
                                .build());
                        return;
                    }
                    s.setDate(LocalDate.from(DATE_RU.parse(v)));
                    s.setStep(1);
                    store.put(chatId, s);
                    sendRouteStep(bot, chatId);
                }
                case 5 -> {
                    LocalTime t = parseTimeHm(v);
                    if (t == null) {
                        bot.execute(SendMessage.builder()
                                .chatId(chatId)
                                .text("Время начала в формате ЧЧ:ММ (например 09:00) или кнопка ниже.")
                                .build());
                        return;
                    }
                    s.setStartTime(t);
                    s.setStep(6);
                    store.put(chatId, s);
                    sendConfirm(bot, chatId, s);
                }
                default -> bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("На этом шаге выберите вариант кнопкой ниже.")
                        .build());
            }
        } catch (DateTimeParseException e) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось разобрать дату или время. Проверьте формат.")
                    .build());
        }
    }

    /**
     * @return true если колбэк обработан (в т.ч. игнор)
     */
    public boolean handleCallback(TelegramLongPollingBot bot, long chatId, String data, String cqId)
            throws TelegramApiException {
        if (CB_SHIFT_NEW.equals(data)) {
            answer(bot, cqId);
            if (addressSessions.get(chatId).isPresent()) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Сначала завершите редактирование адреса (или нажмите «Назад» / главное меню).")
                        .build());
                return true;
            }
            startWizard(bot, chatId);
            return true;
        }
        Optional<ShiftDraftSession> opt = store.get(chatId);
        if (opt.isEmpty()) {
            if (data.startsWith(PREFIX_SHIFT_DATE + "|")
                    || data.startsWith(PREFIX_SHIFT_ROUTE + "|")
                    || data.startsWith(PREFIX_SHIFT_TYPE + "|")
                    || data.startsWith(PREFIX_SHIFT_DEPT + "|")
                    || data.startsWith(PREFIX_SHIFT_COLLEAGUE + "|")
                    || data.startsWith(PREFIX_SHIFT_COLPAGE + "|")
                    || data.startsWith(PREFIX_SHIFT_TSTART + "|")
                    || CB_SHIFT_SUBMIT.equals(data)
                    || CB_SHIFT_ABORT.equals(data)
                    || CB_SHIFT_ADD_PARTNER.equals(data)
                    || data.startsWith(PREFIX_SHIFT_ADD_DEPT + "|")
                    || data.startsWith(PREFIX_SHIFT_ADD_COL + "|")
                    || data.startsWith(PREFIX_SHIFT_ADD_PAGE + "|")
                    || CB_SHIFT_ADD_PARTNER_CANCEL.equals(data)) {
                answer(bot, cqId);
                return true;
            }
            return false;
        }
        ShiftDraftSession s = opt.get();
        if (CB_SHIFT_ABORT.equals(data)) {
            answer(bot, cqId);
            store.clear(chatId);
            bot.execute(SendMessage.builder().chatId(chatId).text("Создание смены отменено.").build());
            return true;
        }
        if (CB_SHIFT_SUBMIT.equals(data)) {
            answer(bot, cqId);
            if (s.getStep() != 6 || s.getConfirmPartnerPicker() != ShiftDraftSession.ConfirmPartnerPicker.IDLE) {
                return true;
            }
            submit(bot, chatId, s);
            return true;
        }
        if (CB_SHIFT_ADD_PARTNER.equals(data)) {
            answer(bot, cqId);
            if (s.getStep() != 6) {
                return true;
            }
            s.setConfirmPartnerPicker(ShiftDraftSession.ConfirmPartnerPicker.DEPARTMENT);
            s.setColleaguePage(0);
            store.put(chatId, s);
            sendConfirmAddPartnerDeptStep(bot, chatId);
            return true;
        }
        if (CB_SHIFT_ADD_PARTNER_CANCEL.equals(data)) {
            answer(bot, cqId);
            if (s.getStep() != 6) {
                return true;
            }
            s.setConfirmPartnerPicker(ShiftDraftSession.ConfirmPartnerPicker.IDLE);
            store.put(chatId, s);
            sendConfirm(bot, chatId, s);
            return true;
        }
        if (data.startsWith(PREFIX_SHIFT_ADD_DEPT + "|")) {
            answer(bot, cqId);
            if (s.getStep() != 6
                    || s.getConfirmPartnerPicker() != ShiftDraftSession.ConfirmPartnerPicker.DEPARTMENT) {
                return true;
            }
            try {
                int dep = Integer.parseInt(data.substring((PREFIX_SHIFT_ADD_DEPT + "|").length()));
                if (dep != 1 && dep != 2) {
                    return true;
                }
                s.setPartnerDepartment(dep);
                s.setColleaguePage(0);
                s.setConfirmPartnerPicker(ShiftDraftSession.ConfirmPartnerPicker.COLLEAGUE);
                store.put(chatId, s);
                sendConfirmAddPartnerColleagueStep(bot, chatId, s);
            } catch (NumberFormatException e) {
                log.debug("bad sadd {}", data);
            }
            return true;
        }
        if (data.startsWith(PREFIX_SHIFT_ADD_PAGE + "|")) {
            answer(bot, cqId);
            if (s.getStep() != 6
                    || s.getConfirmPartnerPicker() != ShiftDraftSession.ConfirmPartnerPicker.COLLEAGUE) {
                return true;
            }
            String[] p = data.split("\\|");
            if (p.length != 3) {
                return true;
            }
            try {
                int dep = Integer.parseInt(p[1]);
                int page = Integer.parseInt(p[2]);
                if ((dep != 1 && dep != 2) || page < 0) {
                    return true;
                }
                s.setPartnerDepartment(dep);
                s.setColleaguePage(page);
                store.put(chatId, s);
                sendConfirmAddPartnerColleagueStep(bot, chatId, s);
            } catch (NumberFormatException e) {
                log.debug("bad sdan {}", data);
            }
            return true;
        }
        if (data.startsWith(PREFIX_SHIFT_ADD_COL + "|")) {
            answer(bot, cqId);
            if (s.getStep() != 6
                    || s.getConfirmPartnerPicker() != ShiftDraftSession.ConfirmPartnerPicker.COLLEAGUE) {
                return true;
            }
            try {
                UUID id = UUID.fromString(data.substring((PREFIX_SHIFT_ADD_COL + "|").length()));
                if (s.hasParticipant(id)) {
                    bot.execute(SendMessage.builder()
                            .chatId(chatId)
                            .text("Этот сотрудник уже указан в смене.")
                            .build());
                    return true;
                }
                String label = resolveColleagueLabel(chatId, s.getPartnerDepartment(), s.getColleaguePage(), id);
                s.addParticipant(id, label, s.getPartnerDepartment());
                s.setConfirmPartnerPicker(ShiftDraftSession.ConfirmPartnerPicker.IDLE);
                store.put(chatId, s);
                sendConfirm(bot, chatId, s);
            } catch (IllegalArgumentException e) {
                log.debug("bad sdac {}", data);
            }
            return true;
        }
        if (data.startsWith(PREFIX_SHIFT_DATE + "|")) {
            answer(bot, cqId);
            if (s.getStep() != 0) {
                return true;
            }
            String[] p = data.split("\\|", 2);
            if (p.length < 2 || !"0".equals(p[1])) {
                return true;
            }
            s.setDate(LocalDate.now());
            s.setStep(1);
            store.put(chatId, s);
            sendRouteStep(bot, chatId);
            return true;
        }
        if (data.startsWith(PREFIX_SHIFT_ROUTE + "|")) {
            answer(bot, cqId);
            if (s.getStep() != 1) {
                return true;
            }
            try {
                int idx = Integer.parseInt(data.substring((PREFIX_SHIFT_ROUTE + "|").length()));
                if (idx < 0 || idx >= SHIFT_ROUTE_IDS.length) {
                    return true;
                }
                s.setRouteId(SHIFT_ROUTE_IDS[idx]);
                s.setStep(2);
                store.put(chatId, s);
                sendTypeStep(bot, chatId);
            } catch (NumberFormatException e) {
                log.debug("bad route cb {}", data);
            }
            return true;
        }
        if (data.startsWith(PREFIX_SHIFT_TYPE + "|")) {
            answer(bot, cqId);
            if (s.getStep() != 2) {
                return true;
            }
            String typeId = data.substring((PREFIX_SHIFT_TYPE + "|").length());
            if (typeId.isBlank()) {
                return true;
            }
            s.setTypeOfShiftId(typeId);
            s.setStep(3);
            store.put(chatId, s);
            sendDepartmentStep(bot, chatId);
            return true;
        }
        if (data.startsWith(PREFIX_SHIFT_DEPT + "|")) {
            answer(bot, cqId);
            if (s.getStep() != 3) {
                return true;
            }
            try {
                int dep = Integer.parseInt(data.substring((PREFIX_SHIFT_DEPT + "|").length()));
                if (dep != 1 && dep != 2) {
                    return true;
                }
                s.setPartnerDepartment(dep);
                s.setColleaguePage(0);
                s.clearParticipants();
                s.setStep(4);
                store.put(chatId, s);
                sendColleagueStep(bot, chatId, s);
            } catch (NumberFormatException e) {
                log.debug("bad dept cb {}", data);
            }
            return true;
        }
        if (data.startsWith(PREFIX_SHIFT_COLPAGE + "|")) {
            answer(bot, cqId);
            if (s.getStep() != 4) {
                return true;
            }
            String[] p = data.split("\\|");
            if (p.length != 3) {
                return true;
            }
            try {
                int dep = Integer.parseInt(p[1]);
                int page = Integer.parseInt(p[2]);
                if ((dep != 1 && dep != 2) || page < 0) {
                    return true;
                }
                s.setPartnerDepartment(dep);
                s.setColleaguePage(page);
                store.put(chatId, s);
                sendColleagueStep(bot, chatId, s);
            } catch (NumberFormatException e) {
                log.debug("bad colpage cb {}", data);
            }
            return true;
        }
        if (data.startsWith(PREFIX_SHIFT_COLLEAGUE + "|")) {
            answer(bot, cqId);
            if (s.getStep() != 4) {
                return true;
            }
            try {
                UUID id = UUID.fromString(data.substring((PREFIX_SHIFT_COLLEAGUE + "|").length()));
                String label = resolveColleagueLabel(chatId, s.getPartnerDepartment(), s.getColleaguePage(), id);
                s.setPrimaryParticipant(id, label, s.getPartnerDepartment());
                s.setStep(5);
                store.put(chatId, s);
                sendStartTimeStep(bot, chatId);
            } catch (IllegalArgumentException e) {
                log.debug("bad colleague uuid cb {}", data);
            }
            return true;
        }
        if (data.startsWith(PREFIX_SHIFT_TSTART + "|")) {
            answer(bot, cqId);
            if (s.getStep() != 5) {
                return true;
            }
            LocalTime t = parseHmDigits(data.substring(PREFIX_SHIFT_TSTART.length() + 1));
            if (t == null) {
                return true;
            }
            s.setStartTime(t);
            s.setStep(6);
            store.put(chatId, s);
            sendConfirm(bot, chatId, s);
            return true;
        }
        return false;
    }

    public void startWizard(TelegramLongPollingBot bot, long chatId) throws TelegramApiException {
        shiftEndAwaitingStore.clear(chatId);
        ShiftDraftSession s = new ShiftDraftSession();
        s.setStep(0);
        store.put(chatId, s);
        sendDateStep(bot, chatId);
    }

    private void resendStep(TelegramLongPollingBot bot, long chatId, ShiftDraftSession s) throws TelegramApiException {
        switch (s.getStep()) {
            case 0 -> sendDateStep(bot, chatId);
            case 1 -> sendRouteStep(bot, chatId);
            case 2 -> sendTypeStep(bot, chatId);
            case 3 -> sendDepartmentStep(bot, chatId);
            case 4 -> sendColleagueStep(bot, chatId, s);
            case 5 -> sendStartTimeStep(bot, chatId);
            case 6 -> sendConfirm(bot, chatId, s);
            default -> {
            }
        }
    }

    private void sendDateStep(TelegramLongPollingBot bot, long chatId) throws TelegramApiException {
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(ib("Сегодня", PREFIX_SHIFT_DATE + "|0")));
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Новая смена.\n\nШаг 1/" + TOTAL_STEPS + ": дата смены.\nВыберите кнопку или введите дату в формате ДД.ММ.ГГГГ.")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void sendRouteStep(TelegramLongPollingBot bot, long chatId) throws TelegramApiException {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < SHIFT_ROUTE_IDS.length; i += 3) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (int j = 0; j < 3 && i + j < SHIFT_ROUTE_IDS.length; j++) {
                int idx = i + j;
                row.add(ib(SHIFT_ROUTE_LABELS[idx], PREFIX_SHIFT_ROUTE + "|" + idx));
            }
            rows.add(row);
        }
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Шаг 2/" + TOTAL_STEPS + ": маршрут (номер смены).")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void sendTypeStep(TelegramLongPollingBot bot, long chatId) throws TelegramApiException {
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(
                        ib("Пост батальона", PREFIX_SHIFT_TYPE + "|BAT_POST"),
                        ib("Маршрут взвода", PREFIX_SHIFT_TYPE + "|VZVOD_ROUTE")),
                List.of(ib("Усиление", PREFIX_SHIFT_TYPE + "|Strengthening")));
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Шаг 3/" + TOTAL_STEPS + ": тип наряда.")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void sendDepartmentStep(TelegramLongPollingBot bot, long chatId) throws TelegramApiException {
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(ib("Отделение 1", PREFIX_SHIFT_DEPT + "|1"), ib("Отделение 2", PREFIX_SHIFT_DEPT + "|2")));
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Шаг 4/" + TOTAL_STEPS + ": отделение напарника по смене.")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void sendColleagueStep(TelegramLongPollingBot bot, long chatId, ShiftDraftSession s) throws TelegramApiException {
        Integer dept = s.getPartnerDepartment();
        if (dept == null) {
            sendDepartmentStep(bot, chatId);
            return;
        }
        try {
            var resp = coreProfileClient.getColleagues(chatId, dept, s.getColleaguePage());
            List<BotColleagueItemDto> items = resp.items() == null ? List.of() : resp.items();
            if (items.isEmpty()) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("В выбранном отделении нет других сотрудников в строю. Выберите другое отделение («Назад»).")
                        .build());
                return;
            }
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            for (BotColleagueItemDto it : items) {
                if (it.serviceInfoId() == null) {
                    continue;
                }
                String label = it.label() == null || it.label().isBlank() ? "Сотрудник" : it.label();
                rows.add(List.of(ib(label, PREFIX_SHIFT_COLLEAGUE + "|" + it.serviceInfoId())));
            }
            if (resp.hasMore()) {
                int next = s.getColleaguePage() + 1;
                rows.add(List.of(ib("Ещё…", PREFIX_SHIFT_COLPAGE + "|" + dept + "|" + next)));
            }
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Шаг 5/" + TOTAL_STEPS + ": напарник (отделение " + dept + "). Выберите сотрудника.")
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(profileErrorHint(e.getStatus()))
                    .build());
        }
    }

    private void sendStartTimeStep(TelegramLongPollingBot bot, long chatId) throws TelegramApiException {
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(
                        ib("09:00", PREFIX_SHIFT_TSTART + "|0900"),
                        ib("10:00", PREFIX_SHIFT_TSTART + "|1000"),
                        ib("11:00", PREFIX_SHIFT_TSTART + "|1100")));
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Шаг 6/" + TOTAL_STEPS + ": время начала (ЧЧ:ММ). Окончание укажется при завершении смены.")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void sendConfirmAddPartnerDeptStep(TelegramLongPollingBot bot, long chatId) throws TelegramApiException {
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(
                        ib("Отделение 1", PREFIX_SHIFT_ADD_DEPT + "|1"),
                        ib("Отделение 2", PREFIX_SHIFT_ADD_DEPT + "|2")),
                List.of(ib("К подтверждению", CB_SHIFT_ADD_PARTNER_CANCEL)));
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Добавить напарника: выберите отделение.")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void sendConfirmAddPartnerColleagueStep(TelegramLongPollingBot bot, long chatId, ShiftDraftSession s)
            throws TelegramApiException {
        Integer dept = s.getPartnerDepartment();
        if (dept == null) {
            sendConfirmAddPartnerDeptStep(bot, chatId);
            return;
        }
        Set<UUID> alreadyInShift = participantsToExclude(s);
        try {
            var resp = coreProfileClient.getColleagues(chatId, dept, s.getColleaguePage());
            List<BotColleagueItemDto> items = resp.items() == null ? List.of() : resp.items();
            if (items.isEmpty()) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("В выбранном отделении нет других сотрудников в строю. Выберите другое отделение.")
                        .build());
                return;
            }
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            boolean anyPickable = false;
            for (BotColleagueItemDto it : items) {
                if (it.serviceInfoId() == null || alreadyInShift.contains(it.serviceInfoId())) {
                    continue;
                }
                String label = it.label() == null || it.label().isBlank() ? "Сотрудник" : it.label();
                rows.add(List.of(ib(label, PREFIX_SHIFT_ADD_COL + "|" + it.serviceInfoId())));
                anyPickable = true;
            }
            if (resp.hasMore()) {
                int next = s.getColleaguePage() + 1;
                rows.add(List.of(ib("Ещё…", PREFIX_SHIFT_ADD_PAGE + "|" + dept + "|" + next)));
            }
            rows.add(List.of(ib("К подтверждению", CB_SHIFT_ADD_PARTNER_CANCEL)));
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
                    .text(profileErrorHint(e.getStatus()))
                    .build());
        }
    }

    private static Set<UUID> participantsToExclude(ShiftDraftSession s) {
        Set<UUID> out = new HashSet<>();
        for (ShiftDraftSession.DraftParticipant p : s.getParticipants()) {
            if (p.serviceInfoId() != null) {
                out.add(p.serviceInfoId());
            }
        }
        return out;
    }

    private String resolveColleagueLabel(long chatId, Integer dept, int page, UUID serviceInfoId) {
        if (dept == null) {
            return "—";
        }
        try {
            var resp = coreProfileClient.getColleagues(chatId, dept, page);
            if (resp.items() != null) {
                for (BotColleagueItemDto it : resp.items()) {
                    if (serviceInfoId.equals(it.serviceInfoId()) && it.label() != null && !it.label().isBlank()) {
                        return it.label();
                    }
                }
            }
        } catch (CoreProfileClient.CoreProfileException e) {
            log.debug("colleague label fetch chatId={} status={}", chatId, e.getStatus());
        }
        return "—";
    }

    private void sendConfirm(TelegramLongPollingBot bot, long chatId, ShiftDraftSession s) throws TelegramApiException {
        String d = s.getDate() != null ? s.getDate().format(DATE_RU) : "—";
        String t0 = s.getStartTime() != null ? s.getStartTime().format(TIME_SHORT) : "—";
        String partnerLine = partnerConfirmLine(s);
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(ib("Сохранить в Core", CB_SHIFT_SUBMIT), ib("Отмена", CB_SHIFT_ABORT)));
        rows.add(List.of(ib("Добавить напарника", CB_SHIFT_ADD_PARTNER)));
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Проверьте и сохраните:\n"
                        + "• Дата: " + d + "\n"
                        + "• Маршрут: " + humanRoute(s.getRouteId()) + "\n"
                        + "• Тип: " + humanType(s.getTypeOfShiftId()) + "\n"
                        + "• Начало: " + t0 + "\n"
                        + partnerLine
                        + "\nОтделение на дату проставится автоматически, как в веб-клиенте.")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private static String partnerConfirmLine(ShiftDraftSession s) {
        List<ShiftDraftSession.DraftParticipant> participants = s.getParticipants();
        if (participants.isEmpty()) {
            return "• Напарники: —";
        }
        if (participants.size() == 1) {
            ShiftDraftSession.DraftParticipant p = participants.get(0);
            String lbl = p.label() == null || p.label().isBlank() ? "—" : p.label();
            Integer dep = p.department();
            String depPart = dep == null ? "" : " (отд. " + dep + ")";
            return "• Напарник: " + lbl + depPart;
        }
        StringBuilder sb = new StringBuilder("• Напарники:");
        for (ShiftDraftSession.DraftParticipant p : participants) {
            String lbl = p.label() == null || p.label().isBlank() ? "—" : p.label();
            Integer dep = p.department();
            String depPart = dep == null ? "" : " (отд. " + dep + ")";
            sb.append("\n  — ").append(lbl).append(depPart);
        }
        return sb.toString();
    }

    private static String humanRoute(String id) {
        if (id == null) {
            return "—";
        }
        if ("Another".equals(id)) {
            return "Другое";
        }
        return id;
    }

    private static String humanType(String id) {
        if (id == null) {
            return "—";
        }
        return switch (id) {
            case "BAT_POST" -> "Пост батальона";
            case "VZVOD_ROUTE" -> "Маршрут взвода";
            case "Strengthening" -> "Усиление";
            default -> id;
        };
    }

    private void submit(TelegramLongPollingBot bot, long chatId, ShiftDraftSession s) throws TelegramApiException {
        if (!s.isReadyToSubmit()) {
            bot.execute(SendMessage.builder().chatId(chatId).text("Заполните все шаги мастера.").build());
            return;
        }
        List<ShiftDraftSession.DraftParticipant> participants = s.getParticipants();
        BotShiftUpsertRequestDto body = new BotShiftUpsertRequestDto(
                s.getDate(),
                s.getRouteId(),
                s.getTypeOfShiftId(),
                s.getStartTime(),
                null,
                participants.get(0).serviceInfoId(),
                null,
                null,
                null,
                null);
        try {
            var saved = coreProfileClient.createShift(chatId, body);
            UUID shiftId = saved.id();
            for (int i = 1; i < participants.size(); i++) {
                ShiftDraftSession.DraftParticipant extra = participants.get(i);
                BotShiftUpsertRequestDto addBody = new BotShiftUpsertRequestDto(
                        saved.date(),
                        saved.route(),
                        saved.typeOfShiftId(),
                        saved.startTime(),
                        saved.endTime(),
                        extra.serviceInfoId(),
                        saved.countOfStatements(),
                        saved.countOfClaims(),
                        saved.ibdWithMigrant(),
                        saved.ibdWithoutMigrant());
                saved = coreProfileClient.updateShift(chatId, shiftId, addBody);
            }
            store.clear(chatId);
            String t0 = saved.startTime() != null ? saved.startTime().format(TIME_SHORT) : "—";
            String endPart = saved.endTime() != null
                    ? "–" + saved.endTime().format(TIME_SHORT)
                    : " (окончание — при завершении смены)";
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Смена сохранена в учётной системе.\n"
                            + saved.date().format(DATE_RU) + " · " + humanRoute(saved.route()) + " · " + nnz(saved.shiftType())
                            + " · с " + t0 + endPart)
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(profileErrorHint(e.getStatus()))
                    .build());
        } catch (Exception e) {
            log.error("create shift chatId={}", chatId, e);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не удалось сохранить смену (Core).")
                    .build());
        }
    }

    private static String nnz(String x) {
        return x == null || x.isBlank() ? "—" : x;
    }

    private static String profileErrorHint(int status) {
        return switch (status) {
            case 404 -> "Сначала войдите: /start и отправьте контакт.";
            case 403 -> "Доступ запрещён (не в строю).";
            case 401 -> "Ошибка API-ключа к Core. Обратитесь к администратору.";
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
