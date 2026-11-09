package com.company.shtopor.vzvodbot.session;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Мастер создания смены в Telegram (только создание; правка — через список и отдельный сценарий при необходимости).
 */
public class ShiftDraftSession {

    public enum ConfirmPartnerPicker {
        IDLE,
        DEPARTMENT,
        COLLEAGUE
    }

    public record DraftParticipant(UUID serviceInfoId, String label, Integer department) {
    }

    private int step;
    private LocalDate date;
    private String routeId;
    private String typeOfShiftId;
    private Integer partnerDepartment;
    private int colleaguePage;
    private final List<DraftParticipant> participants = new ArrayList<>();
    private LocalTime startTime;
    private ConfirmPartnerPicker confirmPartnerPicker = ConfirmPartnerPicker.IDLE;

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getTypeOfShiftId() {
        return typeOfShiftId;
    }

    public void setTypeOfShiftId(String typeOfShiftId) {
        this.typeOfShiftId = typeOfShiftId;
    }

    public Integer getPartnerDepartment() {
        return partnerDepartment;
    }

    public void setPartnerDepartment(Integer partnerDepartment) {
        this.partnerDepartment = partnerDepartment;
    }

    public int getColleaguePage() {
        return colleaguePage;
    }

    public void setColleaguePage(int colleaguePage) {
        this.colleaguePage = colleaguePage;
    }

    public List<DraftParticipant> getParticipants() {
        return List.copyOf(participants);
    }

    public void clearParticipants() {
        participants.clear();
    }

    public boolean hasParticipant(UUID serviceInfoId) {
        if (serviceInfoId == null) {
            return false;
        }
        for (DraftParticipant p : participants) {
            if (serviceInfoId.equals(p.serviceInfoId())) {
                return true;
            }
        }
        return false;
    }

    public void setPrimaryParticipant(UUID serviceInfoId, String label, Integer department) {
        participants.clear();
        if (serviceInfoId != null) {
            participants.add(new DraftParticipant(serviceInfoId, label, department));
        }
    }

    public boolean addParticipant(UUID serviceInfoId, String label, Integer department) {
        if (serviceInfoId == null || hasParticipant(serviceInfoId)) {
            return false;
        }
        participants.add(new DraftParticipant(serviceInfoId, label, department));
        return true;
    }

    public UUID getPartnerServiceInfoId() {
        return participants.isEmpty() ? null : participants.get(0).serviceInfoId();
    }

    public String getPartnerLabel() {
        return participants.isEmpty() ? null : participants.get(0).label();
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public ConfirmPartnerPicker getConfirmPartnerPicker() {
        return confirmPartnerPicker;
    }

    public void setConfirmPartnerPicker(ConfirmPartnerPicker confirmPartnerPicker) {
        this.confirmPartnerPicker = confirmPartnerPicker == null ? ConfirmPartnerPicker.IDLE : confirmPartnerPicker;
    }

    public boolean isReadyToSubmit() {
        boolean partnersOk = !participants.isEmpty() || "CHECKING".equals(typeOfShiftId);
        return date != null
                && routeId != null
                && typeOfShiftId != null
                && partnersOk
                && startTime != null;
    }
}
