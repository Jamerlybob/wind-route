package io.github.jamerlybob.windroute.poi;

import org.json.JSONObject;

/** A kind of useful stop that can be found beside a cycling route. */
public enum PoiKind {
    WATER(new String[][]{{"amenity", "drinking_water"}}),
    FOOD(new String[][]{
            {"shop", "supermarket"},
            {"shop", "convenience"},
            {"amenity", "cafe"},
            {"amenity", "restaurant"},
            {"amenity", "fast_food"}
    }),
    CAMPING(new String[][]{
            {"tourism", "camp_site"},
            {"tourism", "caravan_site"}
    }),
    BIKE_SHOP(new String[][]{{"shop", "bicycle"}}),
    TOILETS(new String[][]{{"amenity", "toilets"}}),
    SHELTER(new String[][]{{"amenity", "shelter"}});

    private final String[][] tags;

    PoiKind(String[][] tags) {
        this.tags = tags;
    }

    int tagCount() {
        return tags.length;
    }

    String tagKey(int index) {
        return tags[index][0];
    }

    String tagValue(int index) {
        return tags[index][1];
    }

    boolean matches(JSONObject elementTags) {
        for (String[] tag : tags) {
            if (tag[1].equals(elementTags.optString(tag[0]))) {
                return true;
            }
        }
        return false;
    }
}
