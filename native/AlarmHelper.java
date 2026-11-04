package uz.namoz.vaqtlari;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public class AlarmHelper {
    static SharedPreferences sp(Context c) { return c.getSharedPreferences("adhan", 0); }

    static PendingIntent showIntent(Context c) {
        return PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // JS dan kelgan ro'yxat: [{id,t,title,body,snd}]
    public static void scheduleAll(Context c, String json) {
        cancelAll(c);
        sp(c).edit().putString("list", json).apply();
        arm(c);
    }

    public static void cancelAll(Context c) {
        try {
            JSONArray a = new JSONArray(sp(c).getString("list", "[]"));
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            for (int i = 0; i < a.length(); i++) {
                int id = a.getJSONObject(i).getInt("id");
                PendingIntent pi = PendingIntent.getBroadcast(c, id, new Intent(c, AdhanReceiver.class),
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pi != null) { am.cancel(pi); pi.cancel(); }
            }
        } catch (Exception e) { }
    }

    // Saqlangan ro'yxatni signal sifatida o'rnatadi (telefon qayta yonganda ham chaqiriladi)
    public static void arm(Context c) {
        try {
            JSONArray a = new JSONArray(sp(c).getString("list", "[]"));
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            long now = System.currentTimeMillis();
            PendingIntent show = showIntent(c);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                long t = o.getLong("t");
                if (t <= now) continue;
                set(c, am, show, o.getInt("id"), t, o.optString("title"), o.optString("body"), o.optInt("snd", 1));
            }
        } catch (Exception e) { }
    }

    // Sinov uchun: bir necha soniyadan keyin azon
    public static void once(Context c, int sec) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        set(c, am, showIntent(c), 999999, System.currentTimeMillis() + sec * 1000L, "Azon sinovi", "Sinov muvaffaqiyatli", 1);
    }

    private static void set(Context c, AlarmManager am, PendingIntent show, int id, long t, String title, String body, int snd) {
        Intent in = new Intent(c, AdhanReceiver.class);
        in.putExtra("title", title);
        in.putExtra("body", body);
        in.putExtra("snd", snd);
        PendingIntent pi = PendingIntent.getBroadcast(c, id, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // setAlarmClock: Doze rejimida ham aniq vaqtda ishlaydi, maxsus ruxsat talab qilmaydi
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, show), pi);
    }
}
