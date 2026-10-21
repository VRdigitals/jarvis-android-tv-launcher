package com.vr.tvhome;

/**
 * Your personal settings. Edit, then run ./install.sh to rebuild and install.
 */
final class Config {
    private Config() {
    }

    /** Weather card: city name shown on screen, and its coordinates (look them up on any map). */
    static final String WEATHER_CITY = "Dubai";
    static final double WEATHER_LAT = 25.2048;
    static final double WEATHER_LON = 55.2708;

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
