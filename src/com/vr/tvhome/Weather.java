package com.vr.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Current weather from Open-Meteo (free, no key). Last result is cached for offline starts. */
public final class Weather {
    private static final String URL_STR = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=" + Config.WEATHER_LAT + "&longitude=" + Config.WEATHER_LON
            + "&current=temperature_2m,weather_code,is_day"
            + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto";
    private static final String PREFS = "weather";

    public final int temp, high, low, code;
    public final boolean day;

    private Weather(int temp, int high, int low, int code, boolean day) {
        this.temp = temp;
        this.high = high;
        this.low = low;
        this.code = code;
        this.day = day;
    }

    /** Blocking network call; run off the main thread. Returns null on failure. */
    public static Weather fetch(Context ctx) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(URL_STR).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                JSONObject json = new JSONObject(out.toString("UTF-8"));
                JSONObject cur = json.getJSONObject("current");
                JSONObject daily = json.getJSONObject("daily");
                Weather w = new Weather(
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
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static Weather cached(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains("temp")) return null;
        return new Weather(p.getInt("temp", 0), p.getInt("high", 0), p.getInt("low", 0),
                p.getInt("code", 0), p.getBoolean("day", true));
    }

    private void save(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("temp", temp).putInt("high", high).putInt("low", low)
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
