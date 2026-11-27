package com.journey.api_rest_v1.dto.campaign;

import java.util.List;

/**
 * What bringing a character into a campaign would do. When {@code removals} is not empty, the import only goes
 * ahead with {@code accept_removals: true}, so the player can pick another character or make a new one instead.
 *
 * @param canImport      false when the character cannot be brought in at all ({@code message} says why)
 * @param removals       what the campaign version would lose (the original keeps everything)
 * @param refundedExp    EXP given back on the campaign version for removed traits and powers
 * @param becomesDraft   true when a removed race, job, or sheet leaves the campaign version unfinished
 */
public record ImportCheckDto(
    String characterId,
    String characterName,
    boolean canImport,
    String message,
    List<ContentIssueDto> removals,
    int refundedExp,
    boolean becomesDraft
) {
}
