package io.github.jamerlybob.windroute.wind;

import java.util.List;
import io.github.jamerlybob.windroute.weather.WindForecast;

/** A south-to-north, west-to-east rectangular grid; forecasts retain their hours. */
public final class WindField {
    public final double south, west, north, east;
    private final int rows, columns;
    private final List<WindForecast> forecasts;
    public WindField(double south, double west, double north, double east,
                     int rows, int columns, List<WindForecast> forecasts) {
        if (rows < 2 || columns < 2 || forecasts.size() != rows * columns
                || north <= south || east <= west) throw new IllegalArgumentException("Invalid wind grid");
        this.south = south; this.west = west; this.north = north; this.east = east;
        this.rows = rows; this.columns = columns; this.forecasts = forecasts;
    }
    public boolean covers(double lat, double lng) {
        double longitude = longitudeInGrid(lng);
        return lat >= south && lat <= north && longitude >= west && longitude <= east;
    }
    private double longitudeInGrid(double longitude) {
        // A grid across the date line can use 170..190 internally while the
        // Maps projection and weather API use -180..180.
        while (longitude < west) longitude += 360;
        while (longitude > east && longitude - 360 >= west) longitude -= 360;
        return longitude;
    }

    public WindVector at(double lat, double lng, long time) {
        double x = Math.max(0, Math.min(columns - 1, (longitudeInGrid(lng) - west) / (east - west) * (columns - 1)));
        double y = Math.max(0, Math.min(rows - 1, (lat - south) / (north - south) * (rows - 1)));
        int col = Math.min(columns - 2, (int) x), row = Math.min(rows - 2, (int) y);
        // Mixing components avoids the circular discontinuity at north (350/10 degrees).
        WindVector bottom = forecasts.get(row * columns + col).windAt(time)
                .mix(forecasts.get(row * columns + col + 1).windAt(time), x - col);
        WindVector top = forecasts.get((row + 1) * columns + col).windAt(time)
                .mix(forecasts.get((row + 1) * columns + col + 1).windAt(time), x - col);
        return bottom.mix(top, y - row);
    }
}
