package io.github.jamerlybob.windroute.nav;

import io.github.jamerlybob.windroute.settings.Settings;

/** Turns navigation facts into short sentences suitable for speech. */
public final class CueBuilder {
    private final String[] words;
    private final Settings.DistanceUnit unit;

    /** Resource formats keep speech wording out of the calculation layer. */
    public CueBuilder(String[] words, Settings.DistanceUnit unit) {
        this.words = words.clone();
        this.unit = unit;
    }

    public String distance(double meters) {
        return RideDistanceFormatter.format(meters, unit, words);
    }

    public String approachingTurn(double meters, String instruction) {
        return String.format(words[4], distance(meters), lowerFirst(instruction));
    }

    public String climb(double metersAhead, double lengthMeters, double gradient,
                               boolean headwind) {
        String result = String.format(words[5], distance(metersAhead), distance(lengthMeters),
                Math.round(gradient));
        return headwind ? result + " " + words[6] : result;
    }

    public String wind(String verdict, double lengthMeters, boolean toFinish) {
        if (toFinish) return String.format(words[8], title(verdict));
        return String.format(words[7], title(verdict), distance(lengthMeters));
    }

    public String gust(boolean fromLeft, double lengthMeters) {
        return String.format(words[9], words[fromLeft ? 10 : 11], distance(lengthMeters));
    }

    public String chain(String first, String second) {
        String clean = first.endsWith(".") ? first.substring(0, first.length() - 1) : first;
        return String.format(words[12], clean, lowerFirst(second));
    }

    public String top() { return words[13]; }
    public String fallback() { return words[14]; }
    public String windLabel(int index) { return words[15 + index]; }
    public String immediate(String maneuver, String instruction) {
        if (maneuver.equals("TURN_LEFT")) return words[18];
        if (maneuver.equals("TURN_RIGHT")) return words[19];
        return instruction;
    }

    private String lowerFirst(String text) {
        if (text == null || text.isEmpty()) return fallback();
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private static String title(String text) {
        if (text == null || text.isEmpty()) return "Wind change";
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
