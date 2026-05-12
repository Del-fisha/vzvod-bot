package com.company.shtopor.vzvodbot.session;

/**
 * Пошаговое редактирование адреса регистрации или проживания в Telegram.
 */
public final class AddressEditSession {

    public enum Target {
        REGISTRATION,
        HABITATION
    }

    private final Target target;
    /** 0–5: текстовые шаги; 6: ждём callback типа жилья; 7: ждём callback статуса. */
    private int step;

    private String index;
    private String city;
    private String street;
    private String houseNumber;
    private String body;
    private String flat;
    private String typeOfHousingId;
    private String statusOfHousingId;

    public AddressEditSession(Target target) {
        this.target = target;
        this.step = 0;
    }

    public Target getTarget() {
        return target;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getHouseNumber() {
        return houseNumber;
    }

    public void setHouseNumber(String houseNumber) {
        this.houseNumber = houseNumber;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getFlat() {
        return flat;
    }

    public void setFlat(String flat) {
        this.flat = flat;
    }

    public String getTypeOfHousingId() {
        return typeOfHousingId;
    }

    public void setTypeOfHousingId(String typeOfHousingId) {
        this.typeOfHousingId = typeOfHousingId;
    }

    public String getStatusOfHousingId() {
        return statusOfHousingId;
    }

    public void setStatusOfHousingId(String statusOfHousingId) {
        this.statusOfHousingId = statusOfHousingId;
    }
}
