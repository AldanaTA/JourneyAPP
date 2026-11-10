package com.journey.api_rest_v1.dto.power;

import java.util.List;

import com.journey.api_rest_v1.domain.powers.PowerCategory;

public record PowerFilterRequest(
    List<PowerCategory> categories,
    Boolean matchAll,
    Integer page,
    Integer size,
    Integer tpCost,
    String tpOperator,
    Integer hpCost,
    String hpOperator,
    Integer mpCost,
    String mpOperator,
    Integer epCost,
    String epOperator
) {
    public boolean getMatchAll() {
        return matchAll != null && matchAll;
    }

    public int getPage() {
        return page != null ? page : 0;
    }

    public int getSize() {
        return size != null ? size : 5;
    }

    public String getTpOperator() {
        return normalizeOperator(tpOperator);
    }

    public String getHpOperator() {
        return normalizeOperator(hpOperator);
    }

    public String getMpOperator() {
        return normalizeOperator(mpOperator);
    }

    public String getEpOperator() {
        return normalizeOperator(epOperator);
    }

    private static String normalizeOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return "lte";
        }
        return operator.trim().toLowerCase();
    }
}
