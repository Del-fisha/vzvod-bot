package com.company.shtopor.vzvodbot.session;

import java.time.LocalTime;
import java.util.UUID;

public class ShiftEndConfirmSession {

    public enum EditStep {
        AWAIT_MANUAL_TIME,
        AWAIT_EDIT_MANUAL_TIME,
        CONFIRM,
        EDIT_IBD,
        EDIT_AP,
        EDIT_CRIMINAL,
        EDIT_STATEMENTS,
        EDIT_TIME
    }

    private UUID shiftId;
    private LocalTime pendingEndTime;
    private EditStep step = EditStep.CONFIRM;
    /** Последнее введённое число для шагов ИБДР / заявки */
    private Integer pendingCountInput;

    public UUID getShiftId() {
        return shiftId;
    }

    public void setShiftId(UUID shiftId) {
        this.shiftId = shiftId;
    }

    public LocalTime getPendingEndTime() {
        return pendingEndTime;
    }

    public void setPendingEndTime(LocalTime pendingEndTime) {
        this.pendingEndTime = pendingEndTime;
    }

    public EditStep getStep() {
        return step;
    }

    public void setStep(EditStep step) {
        this.step = step == null ? EditStep.CONFIRM : step;
    }

    public Integer getPendingCountInput() {
        return pendingCountInput;
    }

    public void setPendingCountInput(Integer pendingCountInput) {
        this.pendingCountInput = pendingCountInput;
    }
}
