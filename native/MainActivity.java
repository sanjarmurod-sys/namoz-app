package uz.namoz.vaqtlari;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import com.getcapacitor.BridgeActivity;
import java.util.Locale;

public class MainActivity extends BridgeActivity {
    private static final int RQ_ALL = 4242, RQ_LOC = 4243;
    private final Handler hd = new Handler(Looper.getMainLooper());
    private LocationListener ll;
    private boolean located;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ilova tizim tugmalari (pastki panel) ostiga kirib ketmasin
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getBridge().getWebView().addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void schedule(String json) { AlarmHelper.scheduleAll(getApplicationContext(), json); }

            @JavascriptInterface
            public void test() { AlarmHelper.once(getApplicationContext(), 15); }

            @JavascriptInterface
            public void soundSettings() { open(new Intent(Settings.ACTION_SOUND_SETTINGS)); }

            @JavascriptInterface
            public void locationSettings() { open(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }

            @JavascriptInterface
            public void appSettings() {
                open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            }

            @JavascriptInterface
            public void battery() {
                runOnUiThread(() -> {
                    try {
                        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            i.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        }
                    } catch (Exception e) { }
                });
            }

            // Ruxsatlar holati: JSON
            @JavascriptInterface
            public String status() {
                boolean loc = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION);
                boolean notif = NotificationManagerCompat.from(MainActivity.this).areNotificationsEnabled();
                boolean on = false, batt = false;
                try {
                    LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
                    on = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                } catch (Exception e) { }
                try {
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    batt = pm.isIgnoringBatteryOptimizations(getPackageName());
                } catch (Exception e) { }
                return "{\"notif\":" + notif + ",\"loc\":" + loc + ",\"locOn\":" + on + ",\"batt\":" + batt + "}";
            }

            // Joylashuv + bildirishnoma ruxsatlarini so'rash
            @JavascriptInterface
            public void requestPerms() {
                runOnUiThread(() -> {
                    java.util.ArrayList<String> need = new java.util.ArrayList<>();
                    if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) need.add(Manifest.permission.ACCESS_FINE_LOCATION);
                    if (!has(Manifest.permission.ACCESS_COARSE_LOCATION)) need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
                    if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) need.add(Manifest.permission.POST_NOTIFICATIONS);
                    if (need.isEmpty()) js("window.__perm&&window.__perm()");
                    else ActivityCompat.requestPermissions(MainActivity.this, need.toArray(new String[0]), RQ_ALL);
                });
            }

            @JavascriptInterface
            public void locate() { runOnUiThread(() -> doLocate()); }
        }, "AndroidX");
    }

    private boolean has(String p) { return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED; }

    private void open(Intent i) {
        runOnUiThread(() -> { try { startActivity(i); } catch (Exception e) { } });
    }

    private void js(String code) {
        try { getBridge().getWebView().post(() -> getBridge().getWebView().evaluateJavascript(code, null)); } catch (Exception e) { }
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(rc, p, g);
        if (rc == RQ_ALL) js("window.__perm&&window.__perm()");
        if (rc == RQ_LOC) {
            if (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)) doLocate();
            else js("window.__gpsErr&&window.__gpsErr('denied')");
        }
    }

    private void stopLoc(LocationManager lm) {
        try { if (ll != null) lm.removeUpdates(ll); } catch (Exception e) { }
    }

    private void send(Location l) {
        js(String.format(Locale.US, "window.__gps&&window.__gps(%f,%f,%f)", l.getLatitude(), l.getLongitude(), (double) l.getAccuracy()));
    }

    // Joylashuvni o'zimiz aniqlaymiz (Capacitor plagini kerak emas)
    private void doLocate() {
        if (!(has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION))) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, RQ_LOC);
            return;
        }
        final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean gpsOn = false, netOn = false;
        try { gpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Exception e) { }
        try { netOn = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch (Exception e) { }
        if (!gpsOn && !netOn) { js("window.__gpsErr&&window.__gpsErr('off')"); return; }
        located = false;
        Location best = null;
        try {
            for (String pr : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(pr);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException e) { }
        final Location fallback = best;
        stopLoc(lm);
        ll = new LocationListener() {
            @Override public void onLocationChanged(@NonNull Location l) {
                if (located) return;
                located = true;
                stopLoc(lm);
                send(l);
            }
            @Override public void onProviderEnabled(@NonNull String s) { }
            @Override public void onProviderDisabled(@NonNull String s) { }
            @Override public void onStatusChanged(String s, int i, Bundle b) { }
        };
        try {
            if (netOn) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, ll, Looper.getMainLooper());
            if (gpsOn) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, ll, Looper.getMainLooper());
        } catch (SecurityException e) { js("window.__gpsErr&&window.__gpsErr('denied')"); return; }
        hd.postDelayed(() -> {
            if (located) return;
            located = true;
            stopLoc(lm);
            if (fallback != null) send(fallback); else js("window.__gpsErr&&window.__gpsErr('timeout')");
        }, 25000);
    }
}
