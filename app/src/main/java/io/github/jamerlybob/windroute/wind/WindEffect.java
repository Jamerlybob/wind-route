package io.github.jamerlybob.windroute.wind;

/** How the wind is treating the rider on one stretch of road. */
public enum WindEffect {
    HEADWIND,
    TAILWIND,
    CROSSWIND,
    /** Too little wind to matter, whatever its direction. */
    CALM
}
