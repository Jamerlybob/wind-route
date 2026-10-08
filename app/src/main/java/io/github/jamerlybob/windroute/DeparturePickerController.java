package io.github.jamerlybob.windroute;

import android.text.format.DateFormat;

import androidx.fragment.app.FragmentManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Owns the two departure chips and the date-then-time picker sequence. */
public final class DeparturePickerController {
    public interface Listener {
        void onDepartureChanged(long epochSeconds);
    }

    private final MainActivity activity;
    private final FragmentManager fragments;
    private final ChipGroup chips;
    private final Chip choose;
    private final Listener listener;
    private long selectedEpochSeconds;
    private boolean changingChip;

    public DeparturePickerController(MainActivity activity, Listener listener) {
        this.activity = activity;
        this.fragments = activity.getSupportFragmentManager();
        this.listener = listener;
        chips = activity.findViewById(R.id.depart_chips);
        choose = activity.findViewById(R.id.depart_choose);
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (changingChip || ids.isEmpty()) {
                return;
            }
            if (ids.get(0) == R.id.depart_choose) {
                openDatePicker();
            } else {
                selectedEpochSeconds = System.currentTimeMillis() / 1000;
                listener.onDepartureChanged(selectedEpochSeconds);
            }
        });
    }

    public long departureEpochSeconds() {
        if (chips.getCheckedChipId() == R.id.depart_now) {
            selectedEpochSeconds = System.currentTimeMillis() / 1000;
        }
        return selectedEpochSeconds;
    }

    public void select(long epochSeconds) {
        selectedEpochSeconds = epochSeconds;
        choose.setText(formatChip(epochSeconds));
        changingChip = true;
        // Programmatic selection would otherwise reopen the picker through the
        // ChipGroup listener, so distinguish it from a rider's tap.
        chips.check(R.id.depart_choose);
        changingChip = false;
        listener.onDepartureChanged(epochSeconds);
    }

    private void openDatePicker() {
        long today = MaterialDatePicker.todayInUtcMilliseconds();
        Calendar end = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        end.setTimeInMillis(today);
        end.add(Calendar.DAY_OF_YEAR, 7);
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(today).setEnd(end.getTimeInMillis()).setOpenAt(today).build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.depart_choose)
                .setSelection(today)
                .setCalendarConstraints(constraints)
                .build();
        picker.addOnPositiveButtonClickListener(day -> openTimePicker(day));
        picker.addOnDismissListener(dialog -> restoreChipAfterCancel());
        picker.show(fragments, "departure_date");
    }

    private void openTimePicker(long utcDay) {
        Calendar now = Calendar.getInstance();
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(activity)
                        ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(now.get(Calendar.HOUR_OF_DAY))
                .setMinute(now.get(Calendar.MINUTE))
                .setTitleText(R.string.depart_choose)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            // The date picker returns UTC midnight as a stable calendar date.
            // Rebuild it in the phone's zone before adding the chosen clock time.
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.setTimeInMillis(utcDay);
            Calendar local = Calendar.getInstance();
            local.clear();
            local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH),
                    utc.get(Calendar.DAY_OF_MONTH), picker.getHour(), picker.getMinute());
            select(local.getTimeInMillis() / 1000);
        });
        picker.addOnDismissListener(dialog -> restoreChipAfterCancel());
        picker.show(fragments, "departure_time");
    }

    private void restoreChipAfterCancel() {
        if (selectedEpochSeconds == 0) {
            changingChip = true;
            chips.check(R.id.depart_now);
            changingChip = false;
        }
    }

    private String formatChip(long epochSeconds) {
        String pattern = DateFormat.is24HourFormat(activity) ? "EEE HH:mm" : "EEE h:mm a";
        return new SimpleDateFormat(pattern, Locale.getDefault())
                .format(new Date(epochSeconds * 1000));
    }
}
