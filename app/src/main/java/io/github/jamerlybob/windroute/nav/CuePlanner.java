package io.github.jamerlybob.windroute.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.github.jamerlybob.windroute.route.RouteStep;
import io.github.jamerlybob.windroute.settings.Settings;

/** Stateful, pure-Java scheduler that emits each ride cue at most once. */
public final class CuePlanner {
    public static final double TURN_EARLY_METERS = 300;
    public static final double TURN_NOW_METERS = 40;
    public static final double CLIMB_EARLY_METERS = 400;
    public static final double GUST_EARLY_METERS = 300;
    public static final double MIN_WIND_LENGTH_METERS = 2000;
    public static final long NON_TURN_GAP_SECONDS = 10;

    public enum Kind { TURN_EARLY, TURN_NOW, CLIMB, CLIMB_TOP, WIND, GUST }

    public static final class Event {
        public final Kind kind;
        public final double atMeters;
        public final double endMeters;
        public final String text;

        public Event(Kind kind, double atMeters, double endMeters, String text) {
            this.kind = kind;
            this.atMeters = atMeters;
            this.endMeters = endMeters;
            this.text = text;
        }

        boolean isTurn() {
            return kind == Kind.TURN_EARLY || kind == Kind.TURN_NOW;
        }
    }

    private final List<Event> events;
    private final boolean[] spoken;
    private long lastNonTurnEpochSeconds = Long.MIN_VALUE;

    public CuePlanner(List<Event> events) {
        this.events = new ArrayList<>(events);
        Collections.sort(this.events, Comparator.comparingDouble(event -> event.atMeters));
        spoken = new boolean[events.size()];
    }

    /** Returns null when no still-relevant cue is due at this fix. */
    public String next(double progressMeters, long nowEpochSeconds) {
        Event event = nextEvent(progressMeters, nowEpochSeconds, false);
        return event == null ? null : event.text;
    }

    /** Turns can interrupt a non-turn; all other cues wait until speech ends. */
    public Event nextEvent(double progressMeters, long nowEpochSeconds, boolean turnsOnly) {
        Event winner = null;
        int winnerIndex = -1;
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            if (spoken[i] || progressMeters < event.atMeters) continue;
            // A queued cue is discarded after its feature has passed. This is
            // what prevents delayed speech describing a condition no longer true.
            if (progressMeters > event.endMeters) {
                spoken[i] = true;
                continue;
            }
            if (turnsOnly && !event.isTurn()) continue;
            if (!event.isTurn() && lastNonTurnEpochSeconds != Long.MIN_VALUE
                    && nowEpochSeconds - lastNonTurnEpochSeconds
                    < NON_TURN_GAP_SECONDS) continue;
            if (winner == null || event.isTurn() && !winner.isTurn()
                    || event.kind == Kind.TURN_NOW && winner.kind != Kind.TURN_NOW) {
                winner = event;
                winnerIndex = i;
            }
        }
        if (winner == null) return null;
        spoken[winnerIndex] = true;
        if (!winner.isTurn()) lastNonTurnEpochSeconds = nowEpochSeconds;
        return winner;
    }

    public static List<Event> turnEvents(List<RouteStep> steps, List<Double> stepStarts,
                                         CueBuilder words, double earlyMeters) {
        List<Event> result = new ArrayList<>();
        for (int i = 0; i < steps.size() && i < stepStarts.size(); i++) {
            RouteStep step = steps.get(i);
            double turn = stepStarts.get(i);
            String instruction = step.instruction.isEmpty() ? words.fallback()
                    : step.instruction;
            if (step.maneuver.equals("DEPART") || step.instruction.isEmpty()) continue;
            // Two nearby manoeuvres are one spoken pair. Suppress the second
            // standalone announcement so the rider does not hear it twice.
            if (i > 0 && stepStarts.get(i) - stepStarts.get(i - 1) <= TURN_NOW_METERS
                    && !steps.get(i - 1).maneuver.equals("DEPART")) continue;
            String immediate = words.immediate(step.maneuver, instruction);
            String earlyInstruction = instruction;
            if (i + 1 < steps.size() && i + 1 < stepStarts.size()
                    && stepStarts.get(i + 1) - turn <= TURN_NOW_METERS) {
                RouteStep second = steps.get(i + 1);
                immediate = words.chain(immediate, words.immediate(second.maneuver, second.instruction));
                earlyInstruction = words.chain(instruction, second.instruction);
            }
            result.add(new Event(Kind.TURN_EARLY, Math.max(0, turn - earlyMeters),
                    Math.max(0, turn - TURN_NOW_METERS - 1),
                    words.approachingTurn(Math.min(turn, earlyMeters), earlyInstruction)));
            result.add(new Event(Kind.TURN_NOW, Math.max(0, turn - TURN_NOW_METERS),
                    turn + 25, immediate));
        }
        return result;
    }

    public void nonTurnFinished(long nowSeconds) { lastNonTurnEpochSeconds = nowSeconds; }
}
