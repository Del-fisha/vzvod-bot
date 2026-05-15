package com.company.shtopor.vzvodbot.session;

import com.company.shtopor.vzvodbot.telegram.ShiftCreationWizard;
import com.company.shtopor.vzvodbot.telegram.ShiftEndTimeHandler;
import com.company.shtopor.vzvodbot.telegram.ShiftQuickEditHandler;
import com.company.shtopor.vzvodbot.telegram.ViolationCreationWizard;
import org.springframework.stereotype.Component;

@Component
public class TelegramBotSessionCleaner {

    private final TelegramSessionRegistry sessions;
    private final PendingBotInputRegistry pendingInputs;
    private final AddressEditSessionStore addressSessions;
    private final ShiftCreationWizard shiftWizard;
    private final ShiftEndTimeHandler shiftEndTimeHandler;
    private final ShiftQuickEditHandler shiftQuickEditHandler;
    private final ViolationCreationWizard violationWizard;

    public TelegramBotSessionCleaner(
            TelegramSessionRegistry sessions,
            PendingBotInputRegistry pendingInputs,
            AddressEditSessionStore addressSessions,
            ShiftCreationWizard shiftWizard,
            ShiftEndTimeHandler shiftEndTimeHandler,
            ShiftQuickEditHandler shiftQuickEditHandler,
            ViolationCreationWizard violationWizard
    ) {
        this.sessions = sessions;
        this.pendingInputs = pendingInputs;
        this.addressSessions = addressSessions;
        this.shiftWizard = shiftWizard;
        this.shiftEndTimeHandler = shiftEndTimeHandler;
        this.shiftQuickEditHandler = shiftQuickEditHandler;
        this.violationWizard = violationWizard;
    }

    public void clear(long chatId) {
        sessions.remove(chatId);
        pendingInputs.clear(chatId);
        addressSessions.clear(chatId);
        shiftWizard.clear(chatId);
        shiftEndTimeHandler.clearAwaiting(chatId);
        shiftQuickEditHandler.clear(chatId);
        violationWizard.clear(chatId);
    }
}
