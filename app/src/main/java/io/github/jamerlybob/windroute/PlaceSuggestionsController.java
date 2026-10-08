package io.github.jamerlybob.windroute;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import io.github.jamerlybob.windroute.places.PlaceNameFormatter;

/** Debounced, background Geocoder suggestions for the two address fields. */
public final class PlaceSuggestionsController {
    public interface BoundsProvider {
        SearchBounds currentBounds();
    }

    public static final class SearchBounds {
        final double south;
        final double west;
        final double north;
        final double east;

        public SearchBounds(double south, double west, double north, double east) {
            this.south = south;
            this.west = west;
            this.north = north;
            this.east = east;
        }

        boolean canGeocode() {
            // The Geocoder overload cannot represent a box crossing the date line.
            return west <= east;
        }
    }

    private static final long WAIT_AFTER_TYPING_MS = 400;
    private static final int MIN_QUERY_LENGTH = 3;
    private static final int MAX_GEOCODER_RESULTS = 5;

    private final Context context;
    private final RouteStore store;
    private final ExecutorService background;
    private final Handler mainThread;
    private final BoundsProvider boundsProvider;
    private final String myLocationText;
    private final Field origin;
    private final Field destination;
    private boolean destroyed;

    public PlaceSuggestionsController(Context context,
                                      MaterialAutoCompleteTextView originView,
                                      MaterialAutoCompleteTextView destinationView,
                                      RouteStore store, ExecutorService background,
                                      Handler mainThread, BoundsProvider boundsProvider,
                                      String myLocationText, Runnable onOriginEdited,
                                      Runnable onDestinationEdited) {
        this.context = context;
        this.store = store;
        this.background = background;
        this.mainThread = mainThread;
        this.boundsProvider = boundsProvider;
        this.myLocationText = myLocationText;
        origin = new Field(originView, onOriginEdited);
        destination = new Field(destinationView, onDestinationEdited);
    }

    public void destroy() {
        destroyed = true;
        mainThread.removeCallbacks(origin.pending);
        mainThread.removeCallbacks(destination.pending);
    }

    private final class Field {
        final MaterialAutoCompleteTextView view;
        final ArrayAdapter<String> adapter;
        final Runnable edited;
        int generation;
        Runnable pending = () -> { };

        Field(MaterialAutoCompleteTextView view, Runnable edited) {
            this.view = view;
            this.edited = edited;
            adapter = new ArrayAdapter<>(context,
                    android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
            view.setAdapter(adapter);
            view.setThreshold(0);
            view.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count,
                                                        int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before,
                                                    int count) {
                    edited.run();
                    schedule(Field.this, s.toString());
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            view.setOnFocusChangeListener((ignored, hasFocus) -> {
                if (hasFocus && view.getText().length() == 0) {
                    show(Field.this, store.recentPlaces(), "");
                }
            });
            view.setOnClickListener(v -> {
                if (view.getText().length() == 0) {
                    show(Field.this, store.recentPlaces(), "");
                }
            });
        }
    }

    private void schedule(Field field, String rawText) {
        int requestGeneration = ++field.generation;
        mainThread.removeCallbacks(field.pending);
        String query = rawText.trim();
        List<String> recent = matchingRecent(query);
        if (query.isEmpty()) {
            show(field, store.recentPlaces(), rawText);
            return;
        }
        if (query.length() < MIN_QUERY_LENGTH || query.equals(myLocationText)) {
            show(field, recent, rawText);
            return;
        }
        SearchBounds bounds = boundsProvider.currentBounds();
        field.pending = () -> background.execute(() -> geocode(
                field, query, rawText, recent, bounds, requestGeneration));
        mainThread.postDelayed(field.pending, WAIT_AFTER_TYPING_MS);
    }

    @SuppressWarnings("deprecation")
    private void geocode(Field field, String query, String originalText,
                         List<String> recent, SearchBounds bounds, int generation) {
        List<String> suggestions = new ArrayList<>(recent);
        if (Geocoder.isPresent()) {
            try {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                List<Address> addresses;
                if (bounds != null && bounds.canGeocode()) {
                    addresses = geocoder.getFromLocationName(query, MAX_GEOCODER_RESULTS,
                            bounds.south, bounds.west, bounds.north, bounds.east);
                } else {
                    addresses = geocoder.getFromLocationName(query, MAX_GEOCODER_RESULTS);
                }
                if (addresses != null) {
                    for (Address address : addresses) {
                        addDistinct(suggestions, readableAddress(address));
                    }
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // Geocoder availability is best-effort. Typed text keeps working.
            }
        }
        mainThread.post(() -> {
            if (!destroyed && generation == field.generation
                    && field.view.getText().toString().equals(originalText)) {
                show(field, suggestions, originalText);
            }
        });
    }

    private List<String> matchingRecent(String query) {
        List<String> matches = new ArrayList<>();
        String lower = query.toLowerCase(Locale.getDefault());
        for (String recent : store.recentPlaces()) {
            if (recent.toLowerCase(Locale.getDefault()).contains(lower)) {
                matches.add(recent);
            }
        }
        return matches;
    }

    private static String readableAddress(Address address) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
            lines.add(address.getAddressLine(i));
        }
        return PlaceNameFormatter.format(lines, address.getFeatureName(),
                address.getLocality(), address.getAdminArea(), address.getCountryName());
    }

    private static void addDistinct(List<String> values, String candidate) {
        if (candidate.isEmpty()) {
            return;
        }
        for (String value : values) {
            if (value.equalsIgnoreCase(candidate)) {
                return;
            }
        }
        values.add(candidate);
    }

    private void show(Field field, List<String> values, String textAtRequest) {
        field.adapter.clear();
        field.adapter.addAll(values);
        field.adapter.notifyDataSetChanged();
        if (!values.isEmpty() && field.view.hasFocus()
                && field.view.getText().toString().equals(textAtRequest)
                && field.view.getWindowVisibility() == View.VISIBLE) {
            field.view.showDropDown();
        }
    }
}
