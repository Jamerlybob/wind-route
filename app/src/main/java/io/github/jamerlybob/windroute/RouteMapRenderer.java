package io.github.jamerlybob.windroute;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import io.github.jamerlybob.windroute.elevation.ElevationProfile;
import io.github.jamerlybob.windroute.elevation.ClimbWind;
import io.github.jamerlybob.windroute.route.CyclingWarnings;
import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.MarkerLayout;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.poi.PoiAlongRoute;
import io.github.jamerlybob.windroute.poi.PoiKind;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.units.UnitText;
import io.github.jamerlybob.windroute.units.UnitFormatter;
import io.github.jamerlybob.windroute.wind.DirectionComparison;
import io.github.jamerlybob.windroute.wind.GustWarnings;
import io.github.jamerlybob.windroute.wind.RouteWind;
import io.github.jamerlybob.windroute.wind.WindEffect;

/** Draws the route, gust outlines, summary, and tappable stretch facts. */
public final class RouteMapRenderer {
    private static final float ROUTE_WIDTH_PX = 16f;
    private static final float CASING_WIDTH_PX = 24f;

    private final Activity activity;
    private final GoogleMap map;
    private final View root;
    private final View searchCard;
    private final View summaryCard;
    private RideAnalysis analysis;
    private Settings settings;
    private List<GeoPoint> lastFittedPoints;
    private com.google.android.gms.maps.model.Marker startMarker, finishMarker;
    private String startLabel = "", finishLabel = "";
    private boolean startLabelShown, finishLabelShown;
    private LatLng stopPosition;
    private final List<LatLng> markerPositions = new ArrayList<>();

    public RouteMapRenderer(Activity activity, GoogleMap map, View root,
                            View searchCard, View summaryCard) {
        this.activity = activity;
        this.map = map;
        this.root = root;
        this.searchCard = searchCard;
        this.summaryCard = summaryCard;
        map.setOnPolylineClickListener(this::showStretchFacts);
        map.setOnMapClickListener(point -> activity.findViewById(R.id.stretch_card)
                .setVisibility(View.GONE));
    }

    public void show(RideAnalysis analysis, Settings settings, String windCost,
                     int collapsedSheetHeight) {
        this.analysis = analysis;
        this.settings = settings;
        Route route = analysis.route;
        map.clear();
        startMarker = null;
        finishMarker = null;
        markerPositions.clear();
        startLabelShown = false;
        finishLabelShown = false;
        List<LatLng> all = new ArrayList<>();
        for (GeoPoint point : route.points) {
            all.add(new LatLng(point.lat, point.lng));
        }
        // The neutral, wider casing stays below every coloured segment. It
        // separates the route from similarly coloured roads on the base map.
        map.addPolyline(new PolylineOptions().addAll(all)
                .color(ContextCompat.getColor(activity,R.color.route_casing))
                .width(dp(8)).jointType(JointType.ROUND)
                .startCap(new RoundCap()).endCap(new RoundCap()));

        drawWindRuns(all, analysis.wind);
        ((WindFlowView) activity.findViewById(R.id.wind_flow)).setRouteGeometry(route.points);
        redrawArrows();

        String startName = ((TextView) activity.findViewById(R.id.origin)).getText().toString();
        String endName = ((TextView) activity.findViewById(R.id.destination)).getText().toString();
        startLabel = io.github.jamerlybob.windroute.places.PlaceNameFormatter.endpoint(startName,
                route.source == Route.Source.GPX,activity.getString(R.string.marker_start));
        finishLabel = io.github.jamerlybob.windroute.places.PlaceNameFormatter.endpoint(endName,
                route.source == Route.Source.GPX,activity.getString(R.string.marker_end));
        startMarker = map.addMarker(new MarkerOptions().position(all.get(0)).title(startLabel).anchor(.5f,48f/70f)
                .icon(MapIcons.endpoint(activity,ContextCompat.getColor(activity,R.color.wind_tailwind),"")));
        finishMarker = map.addMarker(new MarkerOptions().position(all.get(all.size()-1)).title(finishLabel).anchor(.5f,48f/70f)
                .icon(MapIcons.endpoint(activity,ContextCompat.getColor(activity,R.color.wind_headwind),"")));
        markerPositions.add(all.get(0));
        markerPositions.add(all.get(all.size()-1));
        redrawArrows();
        for (io.github.jamerlybob.windroute.wind.GustWarnings.Warning warning : analysis.gustWarnings) {
            io.github.jamerlybob.windroute.route.GeoPoint point = io.github.jamerlybob.windroute.trip.StopPicker.pointAt(route.points, warning.startMeters);
            LatLng position = new LatLng(point.lat, point.lng);
            markerPositions.add(position);
            map.addMarker(new MarkerOptions().position(position).anchor(.5f,.5f).icon(MapIcons.gust(activity))
                    .title(activity.getString(R.string.gust_warnings_title)));
        }
        redrawArrows();
        fillSummary(analysis, settings, route.durationSeconds, windCost);
        summaryCard.setVisibility(View.VISIBLE);
        if (rideMode) {
            map.setPadding(0, 0, 0, 0);
        } else if (lastFittedPoints != route.points) {
            lastFittedPoints = route.points;
            fitRoute(all, collapsedSheetHeight);
        } else {
            updatePadding(collapsedSheetHeight);
        }
    }

    private float dp(float value) { return value * activity.getResources().getDisplayMetrics().density; }

    private final List<com.google.android.gms.maps.model.Marker> arrows = new ArrayList<>();

    public void setStopPosition(LatLng position) {
        stopPosition = position;
        redrawArrows();
    }

    private void redrawEndpointLabels() {
        if (startMarker == null || finishMarker == null || root.getWidth() == 0) return;
        com.google.android.gms.maps.Projection projection = map.getProjection();
        MarkerLayout.Box viewport = new MarkerLayout.Box(
                dp(8),dp(8),root.getWidth()-dp(8),root.getHeight()-dp(8));
        MarkerLayout.Box search = viewBox(searchCard);
        MarkerLayout.Box sheet = viewBox(summaryCard);
        android.graphics.Point start = projection.toScreenLocation(startMarker.getPosition());
        android.graphics.Point finish = projection.toScreenLocation(finishMarker.getPosition());
        MarkerLayout.Box startBox = labelBox(start,startLabel);
        MarkerLayout.Box finishBox = labelBox(finish,finishLabel);
        boolean showStart = MarkerLayout.labelFits(startBox,viewport,search,sheet,null);
        boolean showFinish = MarkerLayout.labelFits(
                finishBox,viewport,search,sheet,showStart ? startBox : null);
        // Change icons only when visibility changes, not on every camera idle.
        if (showStart != startLabelShown) {
            startMarker.setIcon(MapIcons.endpoint(activity,ContextCompat.getColor(activity,R.color.wind_tailwind),showStart ? startLabel : ""));
            startLabelShown = showStart;
        }
        if (showFinish != finishLabelShown) {
            finishMarker.setIcon(MapIcons.endpoint(activity,ContextCompat.getColor(activity,R.color.wind_headwind),showFinish ? finishLabel : ""));
            finishLabelShown = showFinish;
        }
    }

    private MarkerLayout.Box viewBox(View view) {
        if (view.getVisibility()!=View.VISIBLE) return new MarkerLayout.Box(0,0,0,0);
        return new MarkerLayout.Box(view.getX(),view.getY(),
                view.getX()+view.getWidth(),view.getY()+view.getHeight());
    }

    private MarkerLayout.Box labelBox(android.graphics.Point ring, String text) {
        float width = MapIcons.endpointLabelWidth(activity,text);
        return new MarkerLayout.Box(
                ring.x-width/2,ring.y-dp(48),ring.x+width/2,ring.y-dp(21));
    }

    /** Screen spacing keeps the wind direction marks legible at every zoom. */
    public void redrawArrows() {
        for (com.google.android.gms.maps.model.Marker arrow : arrows) arrow.remove();
        arrows.clear();
        if (analysis == null || rideMode) return;
        redrawEndpointLabels();
        List<android.graphics.Point> protectedMarkers = new ArrayList<>();
        for (LatLng position : markerPositions) protectedMarkers.add(map.getProjection().toScreenLocation(position));
        if (stopPosition != null) protectedMarkers.add(map.getProjection().toScreenLocation(stopPosition));
        android.graphics.Point previous = null;
        for (RouteWind.Stretch stretch : analysis.wind.stretches) {
            GeoPoint point = analysis.route.points.get((stretch.fromIndex + stretch.toIndex)/2);
            LatLng position = new LatLng(point.lat,point.lng);
            android.graphics.Point screen = map.getProjection().toScreenLocation(position);
            if (screen.x<0 || screen.y<0 || screen.x>root.getWidth() || screen.y>root.getHeight()) continue;
            if (previous != null && Math.hypot(screen.x-previous.x,screen.y-previous.y)<dp(140)) continue;
            GeoPoint nextPoint = analysis.route.points.get(stretch.toIndex);
            android.graphics.Point nextScreen = map.getProjection().toScreenLocation(new LatLng(nextPoint.lat,nextPoint.lng));
            double dx = nextScreen.x - screen.x;
            double dy = nextScreen.y - screen.y;
            double length = Math.max(1,Math.hypot(dx,dy));
            LatLng arrowPosition = map.getProjection().fromScreenLocation(new android.graphics.Point(
                    (int)(screen.x - dy / length * dp(15)), (int)(screen.y + dx / length * dp(15))));
            android.graphics.Point arrowScreen = map.getProjection().toScreenLocation(arrowPosition);
            boolean blocked = false;
            for (android.graphics.Point marker : protectedMarkers) {
                if (MarkerLayout.nearMarker(
                        arrowScreen.x,arrowScreen.y,marker.x,marker.y,dp(40))) { blocked=true; break; }
            }
            if (blocked) continue;
            arrows.add(map.addMarker(new MarkerOptions().position(arrowPosition).anchor(.5f,.5f).flat(true)
                    .rotation((float)((stretch.windFromDegrees+180)%360))
                    .icon(MapIcons.arrow(activity,ContextCompat.getColor(activity,colorFor(stretch.effect))))
                    .zIndex(4)));
            previous=screen;
        }
    }

    private boolean rideMode;

    /** The ride screen owns the camera; route redraws must leave GPS following alone. */
    public void setRideMode(boolean rideMode) {
        boolean leaving = this.rideMode && !rideMode;
        this.rideMode = rideMode;
        if (rideMode) map.setPadding(0, 0, 0, 0);
        // Ride mode leaves the camera zoomed in, tilted and turned to the
        // rider's heading. Go back to the whole route, north up, so the
        // summary sheet is describing what the map shows.
        if (leaving && lastFitPoints != null) fitRoute(lastFitPoints, lastFitSheetHeight);
    }

    private List<LatLng> lastFitPoints;
    private int lastFitSheetHeight;

    /** Adds small, kind-coloured OSM markers after the route redraw clears the map. */
    public void showPlaces(List<PoiAlongRoute> places) {
        for (PoiAlongRoute place : places) {
            markerPositions.add(new LatLng(place.poi.position.lat,place.poi.position.lng));
            float hue = place.poi.kind == PoiKind.WATER ? BitmapDescriptorFactory.HUE_AZURE
                    : place.poi.kind == PoiKind.FOOD ? BitmapDescriptorFactory.HUE_ORANGE
                    : place.poi.kind == PoiKind.CAMPING ? BitmapDescriptorFactory.HUE_GREEN
                    : BitmapDescriptorFactory.HUE_VIOLET;
            String kind = poiKind(place.poi.kind);
            String title = place.poi.name == null || place.poi.name.isEmpty()
                    ? activity.getString(R.string.place_unnamed, kind) : place.poi.name;
            String snippet = place.poi.openingHours == null ? kind
                    : activity.getString(R.string.place_marker_details,
                            kind, place.poi.openingHours);
            map.addMarker(new MarkerOptions()
                    .position(new LatLng(place.poi.position.lat, place.poi.position.lng))
                    .title(title).snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue)));
        }
    }

    private void drawWindRuns(List<LatLng> all, RouteWind wind) {
        for (RouteWind.Stretch stretch : wind.stretches) {
            int base = ContextCompat.getColor(activity, colorFor(stretch.effect));
            int shaded = io.github.jamerlybob.windroute.wind.WindPalette.blend(0xfff4f6f6, base,
                    .35 + .65 * Math.min(1, stretch.windSpeedKmh / 35));
            map.addPolyline(new PolylineOptions().addAll(all.subList(stretch.fromIndex, stretch.toIndex + 1))
                    .color((base & 0x00ffffff) | 0x18000000).width(dp(19)).zIndex(-2)
                    .jointType(JointType.ROUND).startCap(new RoundCap()).endCap(new RoundCap()));
            map.addPolyline(new PolylineOptions().addAll(all.subList(stretch.fromIndex, stretch.toIndex + 1))
                    .color((base & 0x00ffffff) | 0x40000000).width(dp(13)).zIndex(-1)
                    .jointType(JointType.ROUND).startCap(new RoundCap()).endCap(new RoundCap()));
            map.addPolyline(new PolylineOptions().addAll(all.subList(stretch.fromIndex, stretch.toIndex + 1))
                    .color(shaded).width(dp(5)).jointType(JointType.ROUND)
                    .startCap(new RoundCap()).endCap(new RoundCap()).zIndex(1));
        }

        // Google reports which polyline was tapped but not the tapped point.
        // Transparent hit targets preserve the efficient merged drawing above
        // while still identifying each individual 400 m analysis stretch.
        double stretchStart = 0;
        for (int i = 0; i < wind.stretches.size(); i++) {
            RouteWind.Stretch stretch = wind.stretches.get(i);
            Polyline target = map.addPolyline(new PolylineOptions()
                    .addAll(all.subList(stretch.fromIndex, stretch.toIndex + 1))
                    .color(android.graphics.Color.TRANSPARENT).width(40f)
                    .zIndex(3f).clickable(true));
            target.setTag(new WindRun(i, i, stretchStart));
            stretchStart += stretch.lengthMeters;
        }
    }

    private void showStretchFacts(Polyline polyline) {
        if (!(polyline.getTag() instanceof WindRun) || analysis == null) {
            return;
        }
        WindRun run = (WindRun) polyline.getTag();
        double length = 0;
        double head = 0;
        double speed = 0;
        double gust = 0;
        for (int i = run.firstStretch; i <= run.lastStretch; i++) {
            RouteWind.Stretch stretch = analysis.wind.stretches.get(i);
            length += stretch.lengthMeters;
            head += stretch.headwindKmh * stretch.lengthMeters;
            speed += stretch.windSpeedKmh * stretch.lengthMeters;
            gust = Math.max(gust, stretch.gustKmh);
        }
        head = length > 0 ? head / length : 0;
        speed = length > 0 ? speed / length : 0;
        double cross = Math.sqrt(Math.max(0, speed * speed - head * head));
        long arrival = analysis.departureEpochSeconds + Math.round(
                analysis.route.durationSeconds * run.startMeters / analysis.route.distanceMeters);
        UnitText units = new UnitText(activity, settings);
        String push = head >= 0
                ? activity.getString(R.string.net_headwind, units.windSpeed(Math.abs(head)))
                : activity.getString(R.string.net_tailwind, units.windSpeed(Math.abs(head)));
        String gradient = analysis.elevation == null ? ""
                : activity.getString(R.string.stretch_gradient,
                        gradientAt(analysis.elevation, run.startMeters));
        String facts = activity.getString(R.string.stretch_facts,
                units.distance(run.startMeters),
                activity.getString(R.string.arrive_time,
                        DateFormat.getTimeInstance(DateFormat.SHORT)
                                .format(new Date(arrival * 1000))),
                units.windSpeed(speed), push, units.windSpeed(cross),
                units.windSpeed(gust), gradient);
        ((TextView) activity.findViewById(R.id.stretch_facts)).setText(facts);
        activity.findViewById(R.id.stretch_card).setVisibility(View.VISIBLE);
    }

    private void fillSummary(RideAnalysis analysis, Settings settings,
                             long durationSeconds, String windCost) {
        Route route = analysis.route;
        RouteWind wind = analysis.wind;
        RouteWind reverse = analysis.reverseWind;
        UnitText units = new UnitText(activity, settings);
        ((TextView) activity.findViewById(R.id.headline)).setText(headlineFor(wind));
        WindBadgeView badge = activity.findViewById(R.id.wind_badge);
        badge.setWind(ContextCompat.getColor(activity, colorFor(
                        io.github.jamerlybob.windroute.wind.WindPresentation.verdict(wind))),
                io.github.jamerlybob.windroute.wind.WindPresentation.routeArrow(route, wind),
                units.windSpeed(wind.averageWindKmh));
        WindEffect verdict = io.github.jamerlybob.windroute.wind.WindPresentation.verdict(wind);
        int sentence = verdict == WindEffect.HEADWIND ? R.string.verdict_headwind
                : verdict == WindEffect.TAILWIND ? R.string.verdict_tailwind
                : verdict == WindEffect.CROSSWIND ? R.string.verdict_crosswind : R.string.verdict_calm;
        ((TextView)activity.findViewById(R.id.verdict_sentence)).setText(activity.getString(sentence,
                (int)Math.round(wind.share(verdict)*100)));
        String push = wind.averageHeadwindKmh > 0.5
                ? activity.getString(R.string.net_headwind,
                        units.windSpeed(Math.abs(wind.averageHeadwindKmh)))
                : wind.averageHeadwindKmh < -0.5
                        ? activity.getString(R.string.net_tailwind,
                                units.windSpeed(Math.abs(wind.averageHeadwindKmh)))
                        : activity.getString(R.string.net_neutral);
        String ascent = "";
        if (analysis.elevation != null) {
            double value = UnitFormatter.elevation(analysis.elevation.totalAscentMeters,
                    settings.elevationUnit);
            String amount = activity.getString(settings.elevationUnit
                    == Settings.ElevationUnit.FEET ? R.string.ascent_feet
                    : R.string.ascent_metres, value);
            // Resource-leading spaces are trimmed by Android; add the separator space here.
            ascent = " " + activity.getString(R.string.details_ascent, amount);
        }
        ((TextView) activity.findViewById(R.id.details_primary)).setText(
                activity.getString(R.string.details_primary, units.distance(route.distanceMeters),
                        formatDuration(durationSeconds), ascent));
        ((TextView) activity.findViewById(R.id.details_secondary)).setText(
                activity.getString(R.string.details_secondary, push,
                        units.windSpeed(wind.maxGustKmh), windCost));
        int intoWind = 0;
        for (ClimbWind climb : analysis.climbWinds) {
            if (climb.isClimbIntoHeadwind) intoWind++;
        }
        TextView climbWarning = activity.findViewById(R.id.details_headwind);
        climbWarning.setVisibility(intoWind == 0 ? View.GONE : View.VISIBLE);
        if (intoWind > 0) {
            climbWarning.setText(activity.getResources().getQuantityString(
                    R.plurals.climbs_into_wind, intoWind, intoWind));
        }
        setShare(R.id.bar_headwind, wind.share(WindEffect.HEADWIND));
        setShare(R.id.bar_crosswind, wind.share(WindEffect.CROSSWIND));
        setShare(R.id.bar_tailwind, wind.share(WindEffect.TAILWIND));
        setShare(R.id.bar_calm, wind.share(WindEffect.CALM));

        ((WindShareLegendView)activity.findViewById(R.id.share_legend)).setWind(wind,route.distanceMeters,settings);
        TextView comparisonView = activity.findViewById(R.id.reverse_comparison);
        DirectionComparison comparison = DirectionComparison.compare(wind, reverse);
        if (comparison.meaningful) {
            comparisonView.setText(activity.getString(R.string.reverse_comparison,
                    (int) Math.round(comparison.share * 100), effectName(comparison.effect)));
            comparisonView.setVisibility(View.VISIBLE);
        } else {
            comparisonView.setVisibility(View.GONE);
        }
        TextView warnings = activity.findViewById(R.id.warnings);
        if (route.source == Route.Source.GOOGLE) {
            List<String> notices = CyclingWarnings.combine(
                    activity.getString(R.string.cycling_notice), route.warnings);
            warnings.setText(String.join("\n", notices));
            warnings.setVisibility(View.VISIBLE);
        } else {
            warnings.setVisibility(View.GONE);
        }
    }

    private void fitRoute(List<LatLng> points, int collapsedSheetHeight) {
        lastFitPoints = points;
        lastFitSheetHeight = collapsedSheetHeight;
        summaryCard.post(() -> {
            if (rideMode || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            LatLngBounds.Builder bounds = new LatLngBounds.Builder();
            for (LatLng point : points) {
                bounds.include(point);
            }
            updatePadding(collapsedSheetHeight);
            int edge = Math.round(32 * activity.getResources().getDisplayMetrics().density);
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), edge));
        });
    }

    private void updatePadding(int collapsedSheetHeight) {
        int measured = activity.findViewById(R.id.summary_collapsed).getHeight();
        // Padding changes Google's visible rectangle, keeping the fitted road
        // out from under both the search card and collapsed details sheet.
        map.setPadding(0, searchCard.getBottom(), 0,
                Math.max(measured, collapsedSheetHeight));
    }

    private static int indexAt(double[] cumulative, double meters) {
        int closest = 0;
        double gap = Double.MAX_VALUE;
        for (int i = 0; i < cumulative.length; i++) {
            double candidate = Math.abs(cumulative[i] - meters);
            if (candidate < gap) {
                gap = candidate;
                closest = i;
            }
        }
        return closest;
    }

    private static double gradientAt(ElevationProfile profile, double meters) {
        int closest = 0;
        double gap = Double.MAX_VALUE;
        for (int i = 0; i < profile.size(); i++) {
            double candidate = Math.abs(profile.distanceMeters[i] - meters);
            if (candidate < gap) {
                gap = candidate;
                closest = i;
            }
        }
        return profile.gradientPercent[closest];
    }

    private String headlineFor(RouteWind wind) {
        if (wind.share(WindEffect.HEADWIND) > 0.5) return activity.getString(R.string.headline_headwind);
        if (wind.share(WindEffect.TAILWIND) > 0.5) return activity.getString(R.string.headline_tailwind);
        if (wind.share(WindEffect.CROSSWIND) > 0.5) return activity.getString(R.string.headline_crosswind);
        if (wind.share(WindEffect.CALM) > 0.5) return activity.getString(R.string.headline_calm);
        return activity.getString(R.string.headline_mixed);
    }

    private void setShare(int barId, double share) {
        View bar = activity.findViewById(barId);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) bar.getLayoutParams();
        params.weight = (float) share;
        bar.setLayoutParams(params);
    }

    private String formatDuration(long seconds) {
        long minutes = Math.round(seconds / 60.0);
        return minutes >= 60
                ? activity.getString(R.string.duration_h_min, minutes / 60, minutes % 60)
                : activity.getString(R.string.duration_min, minutes);
    }

    private String effectName(WindEffect effect) {
        switch (effect) {
            case HEADWIND: return activity.getString(R.string.effect_headwind);
            case TAILWIND: return activity.getString(R.string.effect_tailwind);
            case CROSSWIND: return activity.getString(R.string.effect_crosswind);
            default: return activity.getString(R.string.effect_calm);
        }
    }

    private String poiKind(PoiKind kind) {
        switch (kind) {
            case TOWN: return activity.getString(R.string.poi_town);
            case WATER: return activity.getString(R.string.poi_water);
            case FOOD: return activity.getString(R.string.poi_food);
            case CAMPING: return activity.getString(R.string.poi_camping);
            case BIKE_SHOP: return activity.getString(R.string.poi_bike_shop);
            case TOILETS: return activity.getString(R.string.poi_toilets);
            default: return activity.getString(R.string.poi_shelter);
        }
    }

    private static int colorFor(WindEffect effect) {
        switch (effect) {
            case HEADWIND: return R.color.wind_headwind;
            case TAILWIND: return R.color.wind_tailwind;
            case CROSSWIND: return R.color.wind_crosswind;
            default: return R.color.wind_calm;
        }
    }

    private static final class WindRun {
        final int firstStretch;
        final int lastStretch;
        final double startMeters;

        WindRun(int firstStretch, int lastStretch, double startMeters) {
            this.firstStretch = firstStretch;
            this.lastStretch = lastStretch;
            this.startMeters = startMeters;
        }
    }
}
