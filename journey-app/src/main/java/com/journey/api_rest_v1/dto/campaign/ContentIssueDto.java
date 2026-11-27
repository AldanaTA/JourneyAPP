package com.journey.api_rest_v1.dto.campaign;

/**
 * Something in a character that the campaign does not allow, and that is removed when the character is brought in.
 *
 * @param kind      RACE, JOB, SHEET, TRAIT, POWER, ITEM, or SOURCE
 * @param id        the compendium id (the character's item id for ITEM)
 * @param name      what the player sees (their own name for an edited trait or renamed item)
 * @param source    the content source it comes from
 * @param reason    NOT_ALLOWED (its source is not allowed), RACE_REMOVED or JOB_REMOVED (a racial or job trait that
 *                  goes with a removed race or job)
 * @param expSpent  EXP spent on it, given back when it is removed (traits and powers)
 * @param quantity  how many (items)
 */
public record ContentIssueDto(
    String kind,
    String id,
    String name,
    String source,
    String reason,
    int expSpent,
    int quantity
) {
}
