package io.github.jamerlybob.windroute;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.jamerlybob.windroute.places.PlaceNameFormatter;

/**
 * Owns suggestion behaviour for both address fields so the Activity only has
 * to deal with completed text. Geocoding is debounced and isolated from route
 * loading because an unreliable platform Geocoder must never delay Show wind.
 */
public final class PlaceSuggestionsController {
    /** Lets suggestions favour the part of the world currently visible. */
    public interface BoundsProvider {
        SearchBounds currentBounds();
    }

    /** Plain coordinates keep the bounds decision separate from Google Maps. */
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
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final Handler mainThread;
    private final BoundsProvider boundsProvider;
    private final String myLocationText;
    private final Field origin;
    private final Field destination;
    private boolean destroyed;

    public PlaceSuggestionsController(Context context,
                                      MaterialAutoCompleteTextView originView,
                                      MaterialAutoCompleteTextView destinationView,
                                      RouteStore store, Handler mainThread,
                                      BoundsProvider boundsProvider,
                                      String myLocationText, Runnable onOriginEdited,
                                      Runnable onDestinationEdited) {
        this.context = context;
        this.store = store;
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
        background.shutdownNow();
    }

    /** Holds the independent debounce and stale-result state for one field. */
    private final class Field {
        final MaterialAutoCompleteTextView view;
        final UnfilteredArrayAdapter adapter;
        final Runnable edited;
        // Each edit advances the generation. A slow result carrying an older
        // number is discarded rather than replacing suggestions for newer text.
        int generation;
        Runnable pending = () -> { };

        Field(MaterialAutoCompleteTextView view, Runnable edited) {
            this.view = view;
            this.edited = edited;
            adapter = new UnfilteredArrayAdapter(context,
                    android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
            view.setAdapter(adapter);
            view.setThreshold(0);
            // TextWatcher sees typing, programmatic restores and selected
            // suggestions through one API, keeping coordinate state in sync.
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

    /**
     * Keeps the exact list selected by recent-place matching and the Geocoder.
     *
     * <p>AutoCompleteTextView calls its adapter's Filter after every edit. The
     * normal ArrayAdapter filter would silently remove an address such as
     * "12 Ponsonby Road" for the query "ponsonby rd", even though this
     * controller deliberately chose it. This pass-through filter reports every
     * current row and leaves that choice unchanged.
     */
    private static final class UnfilteredArrayAdapter extends ArrayAdapter<String> {
        private final Filter unfiltered = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                results.count = getCount();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }
        };

        UnfilteredArrayAdapter(Context context, int resource, List<String> values) {
            super(context, resource, values);
        }

        @Override
        public Filter getFilter() {
            return unfiltered;
        }
    }
}
