package com.company.shtopor.vzvodbot.session;

import com.company.shtopor.vzvodbot.core.BotEnumOptionDto;
import com.company.shtopor.vzvodbot.core.BotViolationOptionsResponseDto;

import java.util.List;
import java.util.UUID;

public class ViolationDraftSession {

    public enum Kind {
        ADMINISTRATIVE,
        CRIMINAL
    }

    public enum Step {
        ARTICLE,
        TYPE,
        IMPACT
    }

    private UUID shiftId;
    private Kind kind;
    private Step step;
    private Integer articleId;
    private Integer typeId;
    private List<BotEnumOptionDto> impacts;
    private List<BotEnumOptionDto> articles;
    private List<BotEnumOptionDto> types;

    public UUID getShiftId() {
        return shiftId;
    }

    public void setShiftId(UUID shiftId) {
        this.shiftId = shiftId;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public Step getStep() {
        return step;
    }

    public void setStep(Step step) {
        this.step = step;
    }

    public Integer getArticleId() {
        return articleId;
    }

    public void setArticleId(Integer articleId) {
        this.articleId = articleId;
    }

    public Integer getTypeId() {
        return typeId;
    }

    public void setTypeId(Integer typeId) {
        this.typeId = typeId;
    }

    public List<BotEnumOptionDto> getImpacts() {
        return impacts;
    }

    public void setOptions(BotViolationOptionsResponseDto options) {
        this.impacts = options.impacts();
        this.articles = options.administrativeArticles();
        this.types = options.criminalTypes();
    }

    public List<BotEnumOptionDto> getArticles() {
        return articles;
    }

    public List<BotEnumOptionDto> getTypes() {
        return types;
    }
}
