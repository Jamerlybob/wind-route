package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TemperatureRangeFormatterTest {
    @Test
    public void identicalRoundedValuesAreSaidOnce() {
        assertEquals("14 degrees.", TemperatureRangeFormatter.format(
                13.6, 14.4, "%1$d degrees.", "%1$.0f to %2$.0f degrees."));
    }

    @Test
    public void differentRoundedValuesRemainARange() {
        assertEquals("14 to 16 degrees.", TemperatureRangeFormatter.format(
                13.6, 15.6, "%1$d degrees.", "%1$.0f to %2$.0f degrees."));
    }
}
