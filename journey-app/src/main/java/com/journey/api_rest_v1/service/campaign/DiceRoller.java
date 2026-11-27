package com.journey.api_rest_v1.service.campaign;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Rolls dice on the server, so the chat shows results nobody can fake.
 *
 * <p>Formulas are dice and numbers added or subtracted: {@code 1d100}, {@code 2d6+3}, {@code 1d20 - 1 + 1d4}, or
 * {@code d8} (one die). Skill rolls use the d100 and the Degrees of Success rules.</p>
 */
@Component
public class DiceRoller {

    static final int MAX_TERMS = 20;
    static final int MAX_DICE = 100;
    static final int MAX_SIDES = 1000;
    static final int MAX_CONSTANT = 10_000;

    private static final Pattern TERM = Pattern.compile("([+-])?\\s*(?:(\\d*)\\s*[dD]\\s*(\\d+)|(\\d+))\\s*");

    /** One group of dice and what each die came up, e.g. 2d6 -> [4, 1]. A negative group was subtracted. */
    public record DiceGroup(int count, int sides, boolean negative, List<Integer> results) {
    }

    public record Roll(String formula, List<DiceGroup> dice, int modifier, int total) {
    }

    /** Degrees of Success, from best to worst. */
    public enum Degree {
        CRITICAL, SUPERIOR, MAJOR, SUCCESS, FAILURE, FUMBLE
    }

    private final RandomGenerator random;

    public DiceRoller() {
        this(new SecureRandom());
    }

    DiceRoller(RandomGenerator random) {
        this.random = random;
    }

    /** Rolls a formula; 400 with what is wrong when it cannot be read or is too big. */
    public Roll roll(String formula) {
        String text = formula == null ? "" : formula.trim();
        if (text.isEmpty()) throw bad("Give a dice formula, e.g. 1d100 or 2d6+3");
        Matcher m = TERM.matcher(text);
        int at = 0;
        List<DiceGroup> dice = new ArrayList<>();
        long modifier = 0;
        long total = 0;
        int terms = 0;
        int diceRolled = 0;
        while (at < text.length()) {
            if (!m.find(at) || m.start() != at) throw bad("Cannot read the dice formula \"" + text + "\"");
            if (terms > 0 && m.group(1) == null) throw bad("Join the parts of the formula with + or -");
            if (++terms > MAX_TERMS) throw bad("A formula can have at most " + MAX_TERMS + " parts");
            boolean negative = "-".equals(m.group(1));
            if (m.group(3) != null) {
                int count = m.group(2).isEmpty() ? 1 : parse(m.group(2));
                int sides = parse(m.group(3));
                if (count < 1) throw bad("Roll at least one die");
                if (sides < 2 || sides > MAX_SIDES) throw bad("Dice have 2 to " + MAX_SIDES + " sides");
                diceRolled += count;
                if (diceRolled > MAX_DICE) throw bad("A roll can have at most " + MAX_DICE + " dice");
                List<Integer> results = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    int r = random.nextInt(1, sides + 1);
                    results.add(r);
                    total += negative ? -r : r;
                }
                dice.add(new DiceGroup(count, sides, negative, results));
            } else {
                int value = parse(m.group(4));
                if (value > MAX_CONSTANT) throw bad("Numbers in a formula go up to " + MAX_CONSTANT);
                modifier += negative ? -value : value;
                total += negative ? -value : value;
            }
            at = m.end();
        }
        if (dice.isEmpty()) throw bad("A formula needs at least one die, e.g. 1d6");
        return new Roll(normalize(dice, modifier), dice, (int) modifier, (int) total);
    }

    /** A d100 against a skill rating. */
    public int rollPercent() {
        return random.nextInt(1, 101);
    }

    /**
     * Degrees of Success: 96-100 fumbles and 5 or below is critical whatever the rating; otherwise over the rating
     * fails, up to the rating succeeds, below half is a major and below a quarter a superior success.
     */
    public static Degree degree(int roll, int rating) {
        if (roll >= 96) return Degree.FUMBLE;
        if (roll <= 5) return Degree.CRITICAL;
        if (roll > rating) return Degree.FAILURE;
        if (roll * 4 < rating) return Degree.SUPERIOR;
        if (roll * 2 < rating) return Degree.MAJOR;
        return Degree.SUCCESS;
    }

    private static String normalize(List<DiceGroup> dice, long modifier) {
        StringBuilder out = new StringBuilder();
        for (DiceGroup g : dice) {
            if (!out.isEmpty() || g.negative()) out.append(g.negative() ? " - " : " + ");
            out.append(g.count()).append('d').append(g.sides());
        }
        if (modifier != 0) out.append(modifier < 0 ? " - " : " + ").append(Math.abs(modifier));
        return out.toString().trim();
    }

    private static int parse(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            throw bad("That number is too big");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
