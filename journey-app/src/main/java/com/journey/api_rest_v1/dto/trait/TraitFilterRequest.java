package com.journey.api_rest_v1.dto.trait;

import java.util.List;

import com.journey.api_rest_v1.domain.traits.TraitCategory;

/**
 * Request body for the trait filter endpoint.
 * Every field is optional; null values are ignored so the client only sends what the user set.
 * Numeric filters are paired with an operator (lte, lt, eq, gt, gte) that defaults to lte.
 */
public record TraitFilterRequest(
    List<TraitCategory> categories,
    Boolean matchAll,
    Integer page,
    Integer size,
    Integer lvl,
    String lvlOperator,
    Integer purchaseCost,
    String purchaseOperator,
    Integer tpCost,
    String tpOperator,
    Integer mpCost,
    String mpOperator,
    Integer epCost,
    String epOperator,
    String name
) {
    /** Returns true only when the client explicitly asked for traits matching every selected category. */
    public boolean getMatchAll() {
        return matchAll != null && matchAll;
    }

    /** Returns the requested page, defaulting to the first page. */
    public int getPage() {
        return page != null && page >= 0 ? page : 0;
    }

    /** Returns the requested page size, defaulting to 5 like the power filter. */
    public int getSize() {
        return size != null && size > 0 ? size : 5;
    }

    /** Returns the normalized level comparison operator. */
    public String getLvlOperator() {
        return normalizeOperator(lvlOperator);
    }

    /** Returns the normalized purchase cost comparison operator. */
    public String getPurchaseOperator() {
        return normalizeOperator(purchaseOperator);
    }

    /** Returns the normalized TP cost comparison operator. */
    public String getTpOperator() {
        return normalizeOperator(tpOperator);
    }

    /** Returns the normalized MP cost comparison operator. */
    public String getMpOperator() {
        return normalizeOperator(mpOperator);
    }

    /** Returns the normalized EP cost comparison operator. */
    public String getEpOperator() {
        return normalizeOperator(epOperator);
    }

    /** Lowercases/trims an operator and falls back to "lte" when none was provided. */
    private static String normalizeOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return "lte";
        }
        return operator.trim().toLowerCase();
    }
}
