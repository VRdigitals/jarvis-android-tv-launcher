package com.vr.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Current weather from Open-Meteo (free, no key). The place comes from Config, or, if
 * Config.WEATHER_CITY is empty, from the internet connection's approximate location (ipapi.co,
 * city level, looked up at most once a week). Last result is cached for offline starts.
 */
public final class Weather {
    private static final String FORECAST = "https://api.open-meteo.com/v1/forecast"
            + "?current=temperature_2m,weather_code,is_day"
            + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto";
    private static final String LOCATE = "https://ipapi.co/json/";
    private static final long RELOCATE_MS = 7L * 24 * 60 * 60_000;
    private static final String PREFS = "weather";

    public final String city;
    public final int temp, high, low, code;
    public final boolean day;

    private Weather(String city, int temp, int high, int low, int code, boolean day) {
        this.city = city;
        this.temp = temp;
        this.high = high;
        this.low = low;
        this.code = code;
        this.day = day;
    }

    /** Blocking network call; run off the main thread. Returns null on failure. */
    public static Weather fetch(Context ctx) {
        try {
            String[] place = place(ctx);
            if (place == null) return null;
            JSONObject json = getJson(FORECAST + "&latitude=" + place[1] + "&longitude=" + place[2]);
            {
                JSONObject cur = json.getJSONObject("current");
                JSONObject daily = json.getJSONObject("daily");
                Weather w = new Weather(place[0],
                        (int) Math.round(cur.getDouble("temperature_2m")),
                        (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)),
                        (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)),
                        cur.getInt("weather_code"),
                        cur.getInt("is_day") == 1);
                w.save(ctx);
                return w;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** {city, lat, lon}: from Config, or detected from the connection and cached for a week. */
    private static String[] place(Context ctx) throws Exception {
        if (!Config.WEATHER_CITY.isEmpty()) {
            return new String[]{Config.WEATHER_CITY, String.valueOf(Config.WEATHER_LAT), String.valueOf(Config.WEATHER_LON)};
        }
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - p.getLong("locAt", 0) > RELOCATE_MS) {
            try {
                JSONObject loc = getJson(LOCATE);
                p.edit().putString("locCity", loc.getString("city"))
                        .putString("locLat", String.valueOf(loc.getDouble("latitude")))
                        .putString("locLon", String.valueOf(loc.getDouble("longitude")))
                        .putLong("locAt", System.currentTimeMillis()).apply();
            } catch (Exception e) {
                // keep any earlier location
            }
        }
        if (!p.contains("locLat")) return null;
        return new String[]{p.getString("locCity", ""), p.getString("locLat", "0"), p.getString("locLon", "0")};
    }

    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "JarvisTVLauncher/1.0");
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new JSONObject(out.toString("UTF-8"));
        } finally {
            conn.disconnect();
        }
    }

    public static Weather cached(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains("temp")) return null;
        return new Weather(p.getString("city", ""), p.getInt("temp", 0), p.getInt("high", 0), p.getInt("low", 0),
                p.getInt("code", 0), p.getBoolean("day", true));
    }

    private void save(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("city", city).putInt("temp", temp).putInt("high", high).putInt("low", low)
                .putInt("code", code).putBoolean("day", day).apply();
    }

    /** WMO weather code to a short description. */
    public String description() {
        if (code == 0) return day ? "Sunny" : "Clear";
        if (code <= 2) return "Partly Cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Foggy";
        if (code <= 57) return "Drizzle";
        if (code <= 67 || (code >= 80 && code <= 82)) return "Rain";
        if (code <= 77 || code == 85 || code == 86) return "Snow";
        if (code >= 95) return "Thunderstorm";
        return "Cloudy";
    }

    public Icons.Type icon() {
        if (code == 0) return day ? Icons.Type.SUN : Icons.Type.MOON;
        if (code <= 2) return day ? Icons.Type.PARTLY_DAY : Icons.Type.PARTLY_NIGHT;
        if (code == 45 || code == 48) return Icons.Type.FOG;
        if (code >= 51 && code <= 67 || code >= 80 && code <= 82) return Icons.Type.RAIN;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return Icons.Type.SNOW;
        if (code >= 95) return Icons.Type.STORM;
        return Icons.Type.CLOUD;
    }
}
