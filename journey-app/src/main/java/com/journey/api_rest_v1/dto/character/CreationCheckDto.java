package com.journey.api_rest_v1.dto.character;

import java.util.List;

/**
 * Whether a character meets the character creation rules; problems lists what is still wrong.
 */
public record CreationCheckDto(
    boolean ready,
    List<String> problems
) {
}
