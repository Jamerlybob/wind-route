package io.github.jamerlybob.windroute;

import android.content.Context;
import io.github.jamerlybob.windroute.poi.OverpassClient;

/** Both screens give the same partial-result and retry explanation. */
final class PlacesLookupText {
    private PlacesLookupText() { }
    static String result(Context context, OverpassClient.Result result) {
        if (!result.failed()) return result.places.isEmpty() ? context.getString(R.string.places_none,500)
                : context.getString(R.string.places_results,result.places.size());
        String error=context.getString(result.busyFailures>0
                ? (result.networkFailures>0 ? R.string.places_busy_and_network : R.string.places_service_busy)
                : R.string.places_network_error);
        return context.getString(R.string.places_partial,result.places.size(),result.busyFailures+result.networkFailures,result.total,error);
    }
}
