package com.vr.tvhome;

/**
 * Your personal settings. Edit, then run ./install.sh to rebuild and install.
 */
final class Config {
    private Config() {
    }

    /**
     * Weather card. Leave WEATHER_CITY empty ("") to detect your city from your internet
     * connection (approximate, via ipapi.co). Or set a city name and its coordinates
     * (look them up on any map), e.g. "Dubai", 25.2048, 55.2708.
     */
    static final String WEATHER_CITY = "";
    static final double WEATHER_LAT = 0;
    static final double WEATHER_LON = 0;

    /**
     * Big cards in the "Featured" row, in order: {package name, subtitle}. Apps that aren't
     * installed are skipped. Find a package name with: adb shell pm list packages | grep -i netflix
     */
    static final String[][] FEATURED = {
            {"com.netflix.ninja", "Series • Movies"},
            {"com.amazon.amazonvideo.livingroom", "Movies • Originals"},
            {"com.disney.disneyplus", "Movies • Series"},
            {"in.startv.hotstar", "Sports • Shows"},
            {"com.google.android.youtube.tv", "Videos • Creators"},
            {"com.google.android.youtube.tvmusic", "Music • Podcasts"},
    };
}
