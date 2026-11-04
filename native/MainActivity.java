package uz.namoz.vaqtlari;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import androidx.core.view.WindowCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ilova tizim tugmalari (pastki panel) ostiga kirib ketmasin
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getBridge().getWebView().addJavascriptInterface(new Object() {
            // Azon signallarini o'rnatish (JSON ro'yxat)
            @JavascriptInterface
            public void schedule(String json) { AlarmHelper.scheduleAll(getApplicationContext(), json); }

            // Haqiqiy sinov: bir necha soniyadan keyin azon
            @JavascriptInterface
            public void test() { AlarmHelper.once(getApplicationContext(), 15); }

            // Signal (budilnik) ovozi sozlamalarini ochish
            @JavascriptInterface
            public void soundSettings() {
                runOnUiThread(() -> { try { startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)); } catch (Exception e) { } });
            }

            // Fonda ishlash ruxsati
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
        }, "AndroidX");
    }
}
