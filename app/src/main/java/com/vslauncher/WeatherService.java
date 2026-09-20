package com.vslauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class WeatherService {
    interface Callback {
        void onWeather(String text);
    }

    private static final long CACHE_TTL_MS = 20L * 60L * 1000L;
    private static final long MAX_LOCATION_AGE_MS = 30L * 60L * 1000L;
    private static final String PREFS = "weather_cache";
    private static final String KEY_TIME = "updated_at";
    private static final String KEY_TEMP = "temperature";
    private static final String KEY_CODE = "weather_code";

    private final Activity activity;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vs-weather");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    WeatherService(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    boolean hasLocationPermission() {
        return activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    void publishCacheAndRefresh() {
        WeatherSnapshot cached = readCache();
        if (cached != null) callback.onWeather(cached.displayText());

        if (cached != null && System.currentTimeMillis() - cached.updatedAt < CACHE_TTL_MS) {
            return;
        }
        refreshNow();
    }

    void refreshNow() {
        if (!hasLocationPermission()) {
            callback.onWeather("Tap for weather");
            return;
        }
        findLocationAndFetch();
    }

    private void findLocationAndFetch() {
        LocationManager manager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            callback.onWeather("Weather unavailable");
            return;
        }

        try {
            Location best = newest(
                    manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER),
                    manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            );
            long now = System.currentTimeMillis();
            if (best != null
                    && best.getTime() <= now
                    && now - best.getTime() <= MAX_LOCATION_AGE_MS) {
                fetch(best);
                return;
            }

            String provider = null;
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                provider = LocationManager.NETWORK_PROVIDER;
            } else if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                provider = LocationManager.GPS_PROVIDER;
            }

            if (provider == null) {
                callback.onWeather("Location off");
                return;
            }

            manager.requestSingleUpdate(provider, new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    fetch(location);
                }

                @Override public void onProviderDisabled(String provider) {
                    callback.onWeather("Location off");
                }

                @Override public void onProviderEnabled(String provider) { }

                @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            }, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException error) {
            callback.onWeather("Weather unavailable");
        }
    }

    private static Location newest(Location first, Location second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.getTime() >= second.getTime() ? first : second;
    }

    private void fetch(Location location) {
        final double latitude = Math.round(location.getLatitude() * 100.0) / 100.0;
        final double longitude = Math.round(location.getLongitude() * 100.0) / 100.0;

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String endpoint = String.format(
                        Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code&timezone=auto",
                        latitude,
                        longitude
                );
                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setConnectTimeout(4_000);
                connection.setReadTimeout(4_000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "VS-Launcher/0.6");

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("HTTP " + status);
                }

                byte[] bytes;
                try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
                     ByteArrayOutputStream output = new ByteArrayOutputStream(1024)) {
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    bytes = output.toByteArray();
                }

                JSONObject current = new JSONObject(new String(bytes, StandardCharsets.UTF_8))
                        .getJSONObject("current");
                double temperature = current.getDouble("temperature_2m");
                int code = current.getInt("weather_code");
                long now = System.currentTimeMillis();

                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putLong(KEY_TIME, now)
                        .putFloat(KEY_TEMP, (float) temperature)
                        .putInt(KEY_CODE, code)
                        .apply();

                WeatherSnapshot snapshot = new WeatherSnapshot(now, temperature, code);
                main.post(() -> callback.onWeather(snapshot.displayText()));
            } catch (Exception error) {
                WeatherSnapshot cached = readCache();
                String fallback = cached == null ? "Weather unavailable" : cached.displayText();
                main.post(() -> callback.onWeather(fallback));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private WeatherSnapshot readCache() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long updatedAt = prefs.getLong(KEY_TIME, 0L);
        if (updatedAt <= 0L) return null;
        return new WeatherSnapshot(
                updatedAt,
                prefs.getFloat(KEY_TEMP, 0f),
                prefs.getInt(KEY_CODE, -1)
        );
    }

    void close() {
        executor.shutdownNow();
    }

    private static final class WeatherSnapshot {
        final long updatedAt;
        final double temperature;
        final int code;

        WeatherSnapshot(long updatedAt, double temperature, int code) {
            this.updatedAt = updatedAt;
            this.temperature = temperature;
            this.code = code;
        }

        String displayText() {
            return Math.round(temperature) + "° · " + describe(code);
        }

        private static String describe(int code) {
            if (code == 0) return "Clear";
            if (code == 1) return "Mostly clear";
            if (code == 2) return "Partly cloudy";
            if (code == 3) return "Overcast";
            if (code == 45 || code == 48) return "Fog";
            if (code >= 51 && code <= 57) return "Drizzle";
            if (code >= 61 && code <= 67) return "Rain";
            if (code >= 71 && code <= 77) return "Snow";
            if (code >= 80 && code <= 82) return "Showers";
            if (code >= 85 && code <= 86) return "Snow showers";
            if (code >= 95 && code <= 99) return "Thunderstorm";
            return "Weather";
        }
    }
}
