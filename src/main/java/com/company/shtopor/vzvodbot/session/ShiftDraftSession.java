package com.company.shtopor.vzvodbot.session;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Мастер создания смены в Telegram (только создание; правка — через список и отдельный сценарий при необходимости).
 */
public class ShiftDraftSession {

    private int step;
    private LocalDate date;
    private String routeId;
    private String typeOfShiftId;
    private Integer partnerDepartment;
    private int colleaguePage;
    private UUID partnerServiceInfoId;
    private String partnerLabel;
    private LocalTime startTime;

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

    public UUID getPartnerServiceInfoId() {
        return partnerServiceInfoId;
    }

    public void setPartnerServiceInfoId(UUID partnerServiceInfoId) {
        this.partnerServiceInfoId = partnerServiceInfoId;
    }

    public String getPartnerLabel() {
        return partnerLabel;
    }

    public void setPartnerLabel(String partnerLabel) {
        this.partnerLabel = partnerLabel;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public boolean isReadyToSubmit() {
        return date != null
                && routeId != null
                && typeOfShiftId != null
                && partnerServiceInfoId != null
                && startTime != null;
    }
}
