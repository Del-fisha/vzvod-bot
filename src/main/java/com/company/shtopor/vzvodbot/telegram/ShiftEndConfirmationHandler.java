package com.company.shtopor.vzvodbot.telegram;

import com.company.shtopor.vzvodbot.core.BotColleagueItemDto;
import com.company.shtopor.vzvodbot.core.BotShiftEndTimeRequestDto;
import com.company.shtopor.vzvodbot.core.BotShiftItemDto;
import com.company.shtopor.vzvodbot.core.BotShiftUpsertRequestDto;
import com.company.shtopor.vzvodbot.core.CoreProfileClient;
import com.company.shtopor.vzvodbot.session.ShiftEndConfirmSession;
import com.company.shtopor.vzvodbot.session.ShiftEndConfirmSessionStore;
import com.company.shtopor.vzvodbot.session.ViolationDraftSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Подтверждение итогов перед завершением смены и уточнение показателей.
 */
@Component
public class ShiftEndConfirmationHandler {

    private static final Logger log = LoggerFactory.getLogger(ShiftEndConfirmationHandler.class);

    static final String PREFIX_CONFIRM = "sfc";
    static final String PREFIX_EDIT = "sfx";
    /** sfk|{step}|{shiftUuid} — оставить текущее значение и перейти дальше */
    static final String PREFIX_KEEP = "sfk";
    /** sfs|{step}|{shiftUuid} — сохранить введённое число (шаги 0 и 3) */
    static final String PREFIX_SAVE_COUNT = "sfs";
    static final String PREFIX_ADD_AP = "sfa";
    static final String PREFIX_ADD_CRIMINAL = "sfu";
    static final String PREFIX_CHANGE_TIME = "sfwt";

    private static final int STEP_IBD = 0;
    private static final int STEP_AP = 1;
    private static final int STEP_CRIMINAL = 2;
    private static final int STEP_STATEMENTS = 3;
    private static final int STEP_TIME = 4;

    private static final DateTimeFormatter TIME_SHORT = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("ru-RU"));

    private final ShiftEndConfirmSessionStore sessionStore;
    private final CoreProfileClient coreProfileClient;
    private final MainMenuKeyboardFactory mainMenuKeyboardFactory;
    private final ViolationCreationWizard violationWizard;

    public ShiftEndConfirmationHandler(
            ShiftEndConfirmSessionStore sessionStore,
            CoreProfileClient coreProfileClient,
            MainMenuKeyboardFactory mainMenuKeyboardFactory,
            @Lazy ViolationCreationWizard violationWizard
    ) {
        this.sessionStore = sessionStore;
        this.coreProfileClient = coreProfileClient;
        this.mainMenuKeyboardFactory = mainMenuKeyboardFactory;
        this.violationWizard = violationWizard;
    }

    public boolean isActive(long chatId) {
        return sessionStore.get(chatId).isPresent();
    }

    public void clear(long chatId) {
        sessionStore.clear(chatId);
    }

    public boolean isConfirmCallback(String data) {
        if (data == null) {
            return false;
        }
        return data.startsWith(PREFIX_CONFIRM + "|")
                || data.startsWith(PREFIX_EDIT + "|")
                || data.startsWith(PREFIX_KEEP + "|")
                || data.startsWith(PREFIX_SAVE_COUNT + "|")
                || data.startsWith(PREFIX_ADD_AP + "|")
                || data.startsWith(PREFIX_ADD_CRIMINAL + "|")
                || data.startsWith(PREFIX_CHANGE_TIME + "|");
    }

    public void beginConfirmFlow(long chatId, UUID shiftId, LocalTime endTime) {
        ShiftEndConfirmSession session = new ShiftEndConfirmSession();
        session.setShiftId(shiftId);
        session.setPendingEndTime(endTime);
        session.setStep(ShiftEndConfirmSession.EditStep.CONFIRM);
        sessionStore.put(chatId, session);
    }

    public void beginAwaitingManualTime(long chatId, UUID shiftId) {
        ShiftEndConfirmSession session = new ShiftEndConfirmSession();
        session.setShiftId(shiftId);
        session.setStep(ShiftEndConfirmSession.EditStep.AWAIT_MANUAL_TIME);
        sessionStore.put(chatId, session);
    }

    public boolean handleCallback(TelegramLongPollingBot bot, long chatId, String data, String cqId)
            throws TelegramApiException {
        if (!isConfirmCallback(data)) {
            return false;
        }
        answer(bot, cqId);
        var opt = sessionStore.get(chatId);
        if (opt.isEmpty()) {
            return true;
        }
        ShiftEndConfirmSession session = opt.get();
        if (data.startsWith(PREFIX_CONFIRM + "|")) {
            UUID shiftId = parseShiftId(data, PREFIX_CONFIRM);
            if (shiftId != null && shiftId.equals(session.getShiftId())) {
                finalizeShift(bot, chatId, session);
            }
            return true;
        }
        if (data.startsWith(PREFIX_EDIT + "|")) {
            UUID shiftId = parseShiftId(data, PREFIX_EDIT);
            if (shiftId != null && shiftId.equals(session.getShiftId())) {
                session.setStep(ShiftEndConfirmSession.EditStep.EDIT_IBD);
                session.setPendingCountInput(null);
                sessionStore.put(chatId, session);
                sendEditIbdStep(bot, chatId, session);
            }
            return true;
        }
        if (data.startsWith(PREFIX_KEEP + "|")) {
            handleKeep(bot, chatId, session, data);
            return true;
        }
        if (data.startsWith(PREFIX_SAVE_COUNT + "|")) {
            handleSaveCount(bot, chatId, session, data);
            return true;
        }
        if (data.startsWith(PREFIX_ADD_AP + "|")) {
            UUID shiftId = parseShiftId(data, PREFIX_ADD_AP);
            if (shiftId != null && shiftId.equals(session.getShiftId())) {
                violationWizard.startForShiftEnd(bot, chatId, shiftId,
                        ViolationDraftSession.Kind.ADMINISTRATIVE,
                        ViolationDraftSession.AfterComplete.SHIFT_END_AP);
            }
            return true;
        }
        if (data.startsWith(PREFIX_ADD_CRIMINAL + "|")) {
            UUID shiftId = parseShiftId(data, PREFIX_ADD_CRIMINAL);
            if (shiftId != null && shiftId.equals(session.getShiftId())) {
                violationWizard.startForShiftEnd(bot, chatId, shiftId,
                        ViolationDraftSession.Kind.CRIMINAL,
                        ViolationDraftSession.AfterComplete.SHIFT_END_CRIMINAL);
            }
            return true;
        }
        if (data.startsWith(PREFIX_CHANGE_TIME + "|")) {
            UUID shiftId = parseShiftId(data, PREFIX_CHANGE_TIME);
            if (shiftId != null && shiftId.equals(session.getShiftId())) {
                session.setStep(ShiftEndConfirmSession.EditStep.EDIT_TIME);
                sessionStore.put(chatId, session);
                sendEndTimePicker(bot, chatId, shiftId, true);
            }
            return true;
        }
        return true;
    }

    /**
     * @return true если текст обработан в рамках завершения смены
     */
    public boolean handleText(TelegramLongPollingBot bot, long chatId, String text) throws TelegramApiException {
        var opt = sessionStore.get(chatId);
        if (opt.isEmpty()) {
            return false;
        }
        ShiftEndConfirmSession session = opt.get();
        if (session.getStep() == ShiftEndConfirmSession.EditStep.AWAIT_MANUAL_TIME
                || session.getStep() == ShiftEndConfirmSession.EditStep.AWAIT_EDIT_MANUAL_TIME) {
            LocalTime t = parseTimeHm(text.trim());
            if (t == null) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Нужно время в формате ЧЧ:ММ.")
                        .build());
                return true;
            }
            session.setPendingEndTime(t);
            if (session.getStep() == ShiftEndConfirmSession.EditStep.AWAIT_EDIT_MANUAL_TIME) {
                session.setStep(ShiftEndConfirmSession.EditStep.EDIT_TIME);
                sessionStore.put(chatId, session);
                sendEditTimeStep(bot, chatId, session);
            } else {
                session.setStep(ShiftEndConfirmSession.EditStep.CONFIRM);
                sessionStore.put(chatId, session);
                sendConfirmSummary(bot, chatId, session);
            }
            return true;
        }
        if (session.getStep() == ShiftEndConfirmSession.EditStep.EDIT_IBD
                || session.getStep() == ShiftEndConfirmSession.EditStep.EDIT_STATEMENTS) {
            Integer n = parseNonNegativeInt(text);
            if (n == null) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Введите целое число от 0 и выше.")
                        .build());
                return true;
            }
            session.setPendingCountInput(n);
            sessionStore.put(chatId, session);
            if (session.getStep() == ShiftEndConfirmSession.EditStep.EDIT_IBD) {
                sendEditIbdStep(bot, chatId, session);
            } else {
                sendEditStatementsStep(bot, chatId, session);
            }
            return true;
        }
        if (session.getStep() == ShiftEndConfirmSession.EditStep.EDIT_TIME) {
            LocalTime t = parseTimeHm(text.trim());
            if (t == null) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Нужно время в формате ЧЧ:ММ.")
                        .build());
                return true;
            }
            session.setPendingEndTime(t);
            sessionStore.put(chatId, session);
            sendEditTimeStep(bot, chatId, session);
            return true;
        }
        return false;
    }

    public void onTimePicked(TelegramLongPollingBot bot, long chatId, UUID shiftId, LocalTime endTime)
            throws TelegramApiException {
        var opt = sessionStore.get(chatId);
        if (opt.isEmpty()) {
            beginConfirmFlow(chatId, shiftId, endTime);
            sendConfirmSummary(bot, chatId, sessionStore.get(chatId).orElseThrow());
            return;
        }
        ShiftEndConfirmSession session = opt.get();
        if (!shiftId.equals(session.getShiftId())) {
            return;
        }
        session.setPendingEndTime(endTime);
        if (session.getStep() == ShiftEndConfirmSession.EditStep.EDIT_TIME) {
            sessionStore.put(chatId, session);
            sendEditTimeStep(bot, chatId, session);
        } else {
            session.setStep(ShiftEndConfirmSession.EditStep.CONFIRM);
            sessionStore.put(chatId, session);
            sendConfirmSummary(bot, chatId, session);
        }
    }

    public void resumeAfterViolation(long chatId, ViolationDraftSession.AfterComplete after) {
        var opt = sessionStore.get(chatId);
        if (opt.isEmpty()) {
            return;
        }
        ShiftEndConfirmSession session = opt.get();
        if (after == ViolationDraftSession.AfterComplete.SHIFT_END_AP) {
            session.setStep(ShiftEndConfirmSession.EditStep.EDIT_AP);
        } else if (after == ViolationDraftSession.AfterComplete.SHIFT_END_CRIMINAL) {
            session.setStep(ShiftEndConfirmSession.EditStep.EDIT_CRIMINAL);
        }
        sessionStore.put(chatId, session);
    }

    public void sendResumeStep(TelegramLongPollingBot bot, long chatId) throws TelegramApiException {
        var opt = sessionStore.get(chatId);
        if (opt.isEmpty()) {
            return;
        }
        ShiftEndConfirmSession session = opt.get();
        switch (session.getStep()) {
            case EDIT_AP -> sendEditApStep(bot, chatId, session);
            case EDIT_CRIMINAL -> sendEditCriminalStep(bot, chatId, session);
            default -> { }
        }
    }

    void sendEndTimePicker(TelegramLongPollingBot bot, long chatId, UUID shiftId, boolean fromEditTime)
            throws TelegramApiException {
        String sid = shiftId.toString();
        String applyPrefix = fromEditTime
                ? ShiftEndTimeHandler.PREFIX_END_APPLY_EDIT
                : ShiftEndTimeHandler.PREFIX_END_APPLY;
        String manualPrefix = fromEditTime
                ? ShiftEndTimeHandler.PREFIX_END_MANUAL_EDIT
                : ShiftEndTimeHandler.PREFIX_END_MANUAL;
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                ib("21:00", applyPrefix + "|" + sid + "|2100"),
                ib("22:00", applyPrefix + "|" + sid + "|2200"),
                ib("23:00", applyPrefix + "|" + sid + "|2300")));
        rows.add(List.of(ib("Или ввести самому", manualPrefix + "|" + sid)));
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text("Укажите время окончания смены.")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void handleKeep(TelegramLongPollingBot bot, long chatId, ShiftEndConfirmSession session, String data)
            throws TelegramApiException {
        String[] p = data.split("\\|");
        if (p.length != 3) {
            return;
        }
        try {
            int step = Integer.parseInt(p[1]);
            UUID shiftId = UUID.fromString(p[2]);
            if (!shiftId.equals(session.getShiftId())) {
                return;
            }
            session.setPendingCountInput(null);
            switch (step) {
                case STEP_IBD -> {
                    session.setStep(ShiftEndConfirmSession.EditStep.EDIT_AP);
                    sessionStore.put(chatId, session);
                    sendEditApStep(bot, chatId, session);
                }
                case STEP_AP -> {
                    session.setStep(ShiftEndConfirmSession.EditStep.EDIT_CRIMINAL);
                    sessionStore.put(chatId, session);
                    sendEditCriminalStep(bot, chatId, session);
                }
                case STEP_CRIMINAL -> {
                    session.setStep(ShiftEndConfirmSession.EditStep.EDIT_STATEMENTS);
                    sessionStore.put(chatId, session);
                    sendEditStatementsStep(bot, chatId, session);
                }
                case STEP_STATEMENTS -> {
                    session.setStep(ShiftEndConfirmSession.EditStep.EDIT_TIME);
                    sessionStore.put(chatId, session);
                    sendEditTimeStep(bot, chatId, session);
                }
                case STEP_TIME -> {
                    session.setStep(ShiftEndConfirmSession.EditStep.CONFIRM);
                    sessionStore.put(chatId, session);
                    sendConfirmSummary(bot, chatId, session);
                }
                default -> { }
            }
        } catch (IllegalArgumentException e) {
            log.debug("bad sfk {}", data);
        }
    }

    private void handleSaveCount(TelegramLongPollingBot bot, long chatId, ShiftEndConfirmSession session, String data)
            throws TelegramApiException {
        String[] p = data.split("\\|");
        if (p.length != 3) {
            return;
        }
        try {
            int step = Integer.parseInt(p[1]);
            UUID shiftId = UUID.fromString(p[2]);
            if (!shiftId.equals(session.getShiftId())) {
                return;
            }
            Integer value = session.getPendingCountInput();
            if (value == null) {
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Сначала введите новое количество числом в чат.")
                        .build());
                return;
            }
            if (step == STEP_IBD) {
                updateMetric(bot, chatId, session, value, null);
                session.setPendingCountInput(null);
                session.setStep(ShiftEndConfirmSession.EditStep.EDIT_IBD);
                sessionStore.put(chatId, session);
                sendEditIbdStep(bot, chatId, session);
            } else if (step == STEP_STATEMENTS) {
                updateMetric(bot, chatId, session, null, value);
                session.setPendingCountInput(null);
                session.setStep(ShiftEndConfirmSession.EditStep.EDIT_STATEMENTS);
                sessionStore.put(chatId, session);
                sendEditStatementsStep(bot, chatId, session);
            }
        } catch (IllegalArgumentException e) {
            log.debug("bad sfs {}", data);
        }
    }

    private void finalizeShift(TelegramLongPollingBot bot, long chatId, ShiftEndConfirmSession session)
            throws TelegramApiException {
        LocalTime endTime = session.getPendingEndTime();
        if (endTime == null) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Не указано время окончания. Выберите время заново.")
                    .build());
            return;
        }
        try {
            var saved = coreProfileClient.setShiftEndTime(
                    chatId, session.getShiftId(), new BotShiftEndTimeRequestDto(endTime));
            sessionStore.clear(chatId);
            String t0 = saved.startTime() != null ? saved.startTime().format(TIME_SHORT) : "—";
            String t1 = saved.endTime() != null ? saved.endTime().format(TIME_SHORT) : "—";
            DateTimeFormatter dateRu = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru-RU"));
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Смена завершена в учёте: "
                            + (saved.date() != null ? saved.date().format(dateRu) : "—")
                            + " · с " + t0 + " по " + t1)
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        } catch (CoreProfileClient.CoreProfileException e) {
            sessionStore.clear(chatId);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(endTimeErrorHint(e.getStatus()))
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
        }
    }

    private void updateMetric(TelegramLongPollingBot bot, long chatId, ShiftEndConfirmSession session,
                              Integer ibd, Integer statements) throws TelegramApiException {
        try {
            BotShiftItemDto cur = coreProfileClient.getShift(chatId, session.getShiftId());
            if (cur.endTime() != null) {
                sessionStore.clear(chatId);
                bot.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("Смена уже завершена.")
                        .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                        .build());
                return;
            }
            BotShiftUpsertRequestDto body = new BotShiftUpsertRequestDto(
                    cur.date(),
                    cur.route(),
                    cur.typeOfShiftId(),
                    cur.startTime(),
                    cur.endTime(),
                    null,
                    statements != null ? statements : cur.countOfStatements(),
                    cur.countOfClaims(),
                    ibd != null ? ibd : cur.ibdr(),
                    cur.migrant());
            coreProfileClient.updateShift(chatId, session.getShiftId(), body);
        } catch (CoreProfileClient.CoreProfileException e) {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(e.userHint())
                    .build());
        }
    }

    private void sendConfirmSummary(TelegramLongPollingBot bot, long chatId, ShiftEndConfirmSession session)
            throws TelegramApiException {
        BotShiftItemDto shift = loadShift(bot, chatId, session);
        if (shift == null) {
            return;
        }
        String sid = session.getShiftId().toString();
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(
                        ib("Подтвердить", PREFIX_CONFIRM + "|" + sid),
                        ib("Изменить", PREFIX_EDIT + "|" + sid)));
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text(buildSummaryText(chatId, shift, session.getPendingEndTime()))
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build());
    }

    private void sendEditIbdStep(TelegramLongPollingBot bot, long chatId, ShiftEndConfirmSession session)
            throws TelegramApiException {
        BotShiftItemDto shift = loadShift(bot, chatId, session);
        if (shift == null) {
            return;
        }
        int current = shift.ibdr() != null ? shift.ibdr() : 0;
        String sid = session.getShiftId().toString();
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text(partnersBlock(chatId, shift) + "Уточните количество ИБДР. Введите число в чат или выберите текущее.")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(
                                List.of(ib(String.valueOf(current), PREFIX_KEEP + "|" + STEP_IBD + "|" + sid)),
                                List.of(ib("Сохранить новое количество", PREFIX_SAVE_COUNT + "|" + STEP_IBD + "|" + sid))))
                        .build())
                .build());
    }

    private void sendEditApStep(TelegramLongPollingBot bot, long chatId, ShiftEndConfirmSession session)
            throws TelegramApiException {
        BotShiftItemDto shift = loadShift(bot, chatId, session);
        if (shift == null) {
            return;
        }
        int current = shift.administrativeViolationsCount() != null ? shift.administrativeViolationsCount() : 0;
        String sid = session.getShiftId().toString();
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text(partnersBlock(chatId, shift) + "Уточните количество АП. Выберите текущее или добавьте новое нарушение.")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(
                                List.of(ib(String.valueOf(current), PREFIX_KEEP + "|" + STEP_AP + "|" + sid)),
                                List.of(ib("Добавить АП", PREFIX_ADD_AP + "|" + sid))))
                        .build())
                .build());
    }

    private void sendEditCriminalStep(TelegramLongPollingBot bot, long chatId, ShiftEndConfirmSession session)
            throws TelegramApiException {
        BotShiftItemDto shift = loadShift(bot, chatId, session);
        if (shift == null) {
            return;
        }
        int current = shift.criminalViolationsCount() != null ? shift.criminalViolationsCount() : 0;
        String sid = session.getShiftId().toString();
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text(partnersBlock(chatId, shift) + "Уточните количество уголовок. Выберите текущее или добавьте новую.")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(
                                List.of(ib(String.valueOf(current), PREFIX_KEEP + "|" + STEP_CRIMINAL + "|" + sid)),
                                List.of(ib("Добавить уголовку", PREFIX_ADD_CRIMINAL + "|" + sid))))
                        .build())
                .build());
    }

    private void sendEditStatementsStep(TelegramLongPollingBot bot, long chatId, ShiftEndConfirmSession session)
            throws TelegramApiException {
        BotShiftItemDto shift = loadShift(bot, chatId, session);
        if (shift == null) {
            return;
        }
        int current = shift.countOfStatements() != null ? shift.countOfStatements() : 0;
        String sid = session.getShiftId().toString();
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text(partnersBlock(chatId, shift) + "Уточните количество заявок. Введите число в чат или выберите текущее.")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(
                                List.of(ib(String.valueOf(current),
                                        PREFIX_KEEP + "|" + STEP_STATEMENTS + "|" + sid)),
                                List.of(ib("Сохранить новое количество",
                                        PREFIX_SAVE_COUNT + "|" + STEP_STATEMENTS + "|" + sid))))
                        .build())
                .build());
    }

    private void sendEditTimeStep(TelegramLongPollingBot bot, long chatId, ShiftEndConfirmSession session)
            throws TelegramApiException {
        BotShiftItemDto shift = loadShift(bot, chatId, session);
        if (shift == null) {
            return;
        }
        String timeLabel = session.getPendingEndTime() != null
                ? session.getPendingEndTime().format(TIME_SHORT)
                : "—";
        String sid = session.getShiftId().toString();
        bot.execute(SendMessage.builder()
                .chatId(chatId)
                .text(partnersBlock(chatId, shift) + "Уточните время завершения смены.")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(
                                List.of(ib(timeLabel, PREFIX_KEEP + "|" + STEP_TIME + "|" + sid)),
                                List.of(ib("Изменить время окончания", PREFIX_CHANGE_TIME + "|" + sid))))
                        .build())
                .build());
    }

    private String buildSummaryText(long chatId, BotShiftItemDto shift, LocalTime endTime) {
        int ibd = shift.ibdr() != null ? shift.ibdr() : 0;
        int admin = shift.administrativeViolationsCount() != null ? shift.administrativeViolationsCount() : 0;
        int criminal = shift.criminalViolationsCount() != null ? shift.criminalViolationsCount() : 0;
        int statements = shift.countOfStatements() != null ? shift.countOfStatements() : 0;
        String timeStr = endTime != null ? endTime.format(TIME_SHORT) : "—";
        return "Всё ли правильно в итогах:\n\n"
                + "Напарники:\n"
                + formatPartners(chatId, shift) + "\n\n"
                + "Ибдр: " + ibd + "\n"
                + "АП: " + admin + "\n"
                + "Уголовка: " + criminal + "\n"
                + "Заявки: " + statements + "\n"
                + "Время завершения: " + timeStr;
    }

    private String partnersBlock(long chatId, BotShiftItemDto shift) {
        return "Напарники:\n" + formatPartners(chatId, shift) + "\n\n";
    }

    private String formatPartners(long chatId, BotShiftItemDto shift) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (shift.partnerServiceInfoId() != null) {
            ids.add(shift.partnerServiceInfoId());
        }
        if (shift.otherParticipantServiceInfoIds() != null) {
            ids.addAll(shift.otherParticipantServiceInfoIds());
        }
        if (ids.isEmpty()) {
            return "—";
        }
        Map<UUID, String> labels = resolveParticipantLabels(chatId, shift.id());
        StringBuilder sb = new StringBuilder();
        for (UUID id : ids) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(labels.getOrDefault(id, "Сотрудник"));
        }
        return sb.toString();
    }

    private Map<UUID, String> resolveParticipantLabels(long chatId, UUID shiftId) {
        Map<UUID, String> out = new HashMap<>();
        for (int dept : new int[] {1, 2}) {
            int page = 0;
            boolean hasMore;
            do {
                try {
                    var resp = coreProfileClient.getColleagues(chatId, dept, page, shiftId);
                    if (resp.items() != null) {
                        for (BotColleagueItemDto it : resp.items()) {
                            if (it.serviceInfoId() != null && it.label() != null) {
                                out.put(it.serviceInfoId(), it.label());
                            }
                        }
                    }
                    hasMore = resp.hasMore();
                    page++;
                } catch (Exception e) {
                    log.debug("colleagues for partners chatId={} dept={} page={}", chatId, dept, page, e);
                    hasMore = false;
                }
            } while (hasMore);
        }
        return out;
    }

    private BotShiftItemDto loadShift(TelegramLongPollingBot bot, long chatId, ShiftEndConfirmSession session)
            throws TelegramApiException {
        try {
            return coreProfileClient.getShift(chatId, session.getShiftId());
        } catch (CoreProfileClient.CoreProfileException e) {
            sessionStore.clear(chatId);
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(e.userHint())
                    .replyMarkup(mainMenuKeyboardFactory.build(chatId))
                    .build());
            return null;
        }
    }

    private static UUID parseShiftId(String data, String prefix) {
        try {
            return UUID.fromString(data.substring(prefix.length() + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer parseNonNegativeInt(String text) {
        try {
            int n = Integer.parseInt(text.trim());
            return n >= 0 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
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

    private static InlineKeyboardButton ib(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    private static void answer(TelegramLongPollingBot bot, String cqId) throws TelegramApiException {
        bot.execute(AnswerCallbackQuery.builder().callbackQueryId(cqId).build());
    }
}
