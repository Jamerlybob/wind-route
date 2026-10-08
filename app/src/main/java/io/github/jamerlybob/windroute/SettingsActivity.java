package io.github.jamerlybob.windroute;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.CompoundButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.settings.SettingsStore;
import io.github.jamerlybob.windroute.units.UnitText;

/**
 * Presents the app's small fixed set of choices using ordinary Android views.
 * Conversion and persistence stay in separate classes so this Activity only
 * translates spinner selections into one Settings value.
 */
public final class SettingsActivity extends AppCompatActivity {
    private Spinner distance;
    private Spinner windSpeed;
    private Spinner ridingSpeed;
    private Spinner theme;
    private Spinner calmBelow;
    private Spinner temperature;
    private Spinner elevation;
    private Settings current;
    private CompoundButton cueTurns, cueClimbs, cueWind, cueGusts, lessTalk;
    private Spinner cueAhead;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        current = SettingsStore.load(this);

        distance = findViewById(R.id.setting_distance);
        windSpeed = findViewById(R.id.setting_wind_speed);
        ridingSpeed = findViewById(R.id.setting_riding_speed);
        theme = findViewById(R.id.setting_theme);
        calmBelow = findViewById(R.id.setting_calm_below);
        temperature = findViewById(R.id.setting_temperature);
        elevation = findViewById(R.id.setting_elevation);
        cueTurns = findViewById(R.id.cue_turns);
        cueClimbs = findViewById(R.id.cue_climbs);
        cueWind = findViewById(R.id.cue_wind);
        cueGusts = findViewById(R.id.cue_gusts);
        lessTalk = findViewById(R.id.cue_less_talk);
        cueAhead = findViewById(R.id.cue_ahead);

        keepContentClearOfSystemBars();
        findViewById(R.id.settings_back).setOnClickListener(v -> finish());
        populateChoices();
        selectCurrentValues();
        listenForChanges();
    }

    private void keepContentClearOfSystemBars() {
        View root = findViewById(R.id.settings_root);
        int normal = Math.round(16 * getResources().getDisplayMetrics().density);
        // Edge-to-edge lets the background fill the screen; insets then add
        // enough padding to keep controls below system bars on every device.
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(normal + bars.left, normal + bars.top,
                    normal + bars.right, normal + bars.bottom);
            return insets;
        });
    }

    private void populateChoices() {
        setChoices(distance, Arrays.asList(getResources().getStringArray(
                R.array.distance_options)));
        setChoices(windSpeed, Arrays.asList(getResources().getStringArray(
                R.array.wind_speed_options)));
        setChoices(theme, Arrays.asList(getResources().getStringArray(R.array.theme_options)));
        setChoices(temperature, Arrays.asList(getResources().getStringArray(
                R.array.temperature_options)));
        setChoices(elevation, Arrays.asList(getResources().getStringArray(
                R.array.elevation_options)));
        setChoices(cueAhead, Arrays.asList(getResources().getStringArray(
                R.array.cue_ahead_options)));

        UnitText units = new UnitText(this, current);
        List<String> ridingChoices = new ArrayList<>();
        ridingChoices.add(getString(R.string.riding_speed_google));
        for (int speed = 10; speed <= 40; speed++) {
            ridingChoices.add(units.speed(speed, Settings.WindSpeedUnit.KMH));
        }
        setChoices(ridingSpeed, ridingChoices);

        List<String> calmChoices = new ArrayList<>();
        for (int speed = 0; speed <= 15; speed++) {
            calmChoices.add(units.speed(speed, Settings.WindSpeedUnit.KMH));
        }
        setChoices(calmBelow, calmChoices);
    }

    private void setChoices(Spinner spinner, List<String> choices) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void selectCurrentValues() {
        distance.setSelection(current.distanceUnit.ordinal());
        windSpeed.setSelection(current.windSpeedUnit.ordinal());
        ridingSpeed.setSelection(current.ridingSpeedKmh == Settings.USE_GOOGLE_RIDING_SPEED
                ? 0 : current.ridingSpeedKmh - 9);
        theme.setSelection(current.theme.ordinal());
        calmBelow.setSelection(current.calmBelowKmh);
        temperature.setSelection(current.temperatureUnit.ordinal());
        elevation.setSelection(current.elevationUnit.ordinal());
        cueTurns.setChecked(current.cueTurns);
        cueClimbs.setChecked(current.cueClimbs);
        cueWind.setChecked(current.cueWind);
        cueGusts.setChecked(current.cueGusts);
        lessTalk.setChecked(current.lessTalk);
        cueAhead.setSelection(current.cueAheadMeters <= 200 ? 0
                : current.cueAheadMeters <= 300 ? 1 : 2);
    }

    private void listenForChanges() {
        // Spinners also call this listener for their initial selection. The
        // equality check in saveSelections prevents those callbacks writing.
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveSelections();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        distance.setOnItemSelectedListener(listener);
        windSpeed.setOnItemSelectedListener(listener);
        ridingSpeed.setOnItemSelectedListener(listener);
        theme.setOnItemSelectedListener(listener);
        calmBelow.setOnItemSelectedListener(listener);
        temperature.setOnItemSelectedListener(listener);
        elevation.setOnItemSelectedListener(listener);
        cueAhead.setOnItemSelectedListener(listener);
        CompoundButton.OnCheckedChangeListener checked = (button, value) -> saveSelections();
        cueTurns.setOnCheckedChangeListener(checked);
        cueClimbs.setOnCheckedChangeListener(checked);
        cueWind.setOnCheckedChangeListener(checked);
        cueGusts.setOnCheckedChangeListener(checked);
        lessTalk.setOnCheckedChangeListener(checked);
    }

    private void saveSelections() {
        int riding = ridingSpeed.getSelectedItemPosition() == 0
                ? Settings.USE_GOOGLE_RIDING_SPEED
                : ridingSpeed.getSelectedItemPosition() + 9;
        int[] aheadChoices = {200, 300, 400};
        Settings changed = new Settings(
                Settings.DistanceUnit.values()[distance.getSelectedItemPosition()],
                Settings.WindSpeedUnit.values()[windSpeed.getSelectedItemPosition()],
                riding,
                Settings.Theme.values()[theme.getSelectedItemPosition()],
                calmBelow.getSelectedItemPosition(),
                Settings.TemperatureUnit.values()[temperature.getSelectedItemPosition()],
                Settings.ElevationUnit.values()[elevation.getSelectedItemPosition()],
                cueTurns.isChecked(), cueClimbs.isChecked(), cueWind.isChecked(),
                cueGusts.isChecked(), aheadChoices[cueAhead.getSelectedItemPosition()],
                lessTalk.isChecked());
        if (changed.equals(current)) {
            return;
        }
        boolean themeChanged = changed.theme != current.theme;
        current = changed;
        SettingsStore.save(this, changed);
        if (themeChanged) {
            SettingsStore.applyTheme(changed);
        }
    }
}
