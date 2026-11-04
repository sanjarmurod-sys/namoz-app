package uz.namoz.vaqtlari;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

public class AdhanService extends Service {
    private MediaPlayer mp;
    private AudioManager am;
    private AudioFocusRequest fr;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        if (in != null && "STOP".equals(in.getAction())) { stopAll(); return START_NOT_STICKY; }
        String title = in != null ? in.getStringExtra("title") : null;
        String body = in != null ? in.getStringExtra("body") : null;
        boolean snd = in == null || in.getIntExtra("snd", 1) == 1;
        if (title == null) title = "Namoz vaqti";
        if (body == null) body = "";

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            // Ovozli azon: bildirishnomaning o'zi jim (ovozni MediaPlayer chaladi, signal ovozi bilan)
            NotificationChannel play = new NotificationChannel("azon_play", "Azon (ijro)", NotificationManager.IMPORTANCE_HIGH);
            play.setSound(null, null);
            play.enableVibration(false);
            nm.createNotificationChannel(play);
            NotificationChannel note = new NotificationChannel("azon_note", "Namoz eslatmasi", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(note);
        }
        int icon = getResources().getIdentifier("ic_fg", "drawable", getPackageName());
        if (icon == 0) icon = android.R.drawable.ic_lock_idle_alarm;
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, AdhanService.class).setAction("STOP"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, snd ? "azon_play" : "azon_note")
                : new Notification.Builder(this);
        b.setSmallIcon(icon).setContentTitle(title).setContentText(body).setContentIntent(open)
                .setCategory(Notification.CATEGORY_ALARM).setAutoCancel(!snd);
        if (snd) b.addAction(android.R.drawable.ic_media_pause, "To'xtatish", stop).setOngoing(true);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(77, n);

        if (!snd) {
            // Ovozsiz rejim: oddiy bildirishnoma qoladi, xizmat to'xtaydi
            stopForeground(Service.STOP_FOREGROUND_DETACH);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            release();
            AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
            am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                fr = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(aa).build();
                am.requestAudioFocus(fr);
            }
            mp = new MediaPlayer();
            mp.setAudioAttributes(aa);
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            AssetFileDescriptor afd = getResources().openRawResourceFd(getResources().getIdentifier("azon", "raw", getPackageName()));
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setOnCompletionListener(x -> stopAll());
            mp.prepare();
            mp.start();
            h.postDelayed(this::stopAll, 8 * 60 * 1000L);
        } catch (Exception e) {
            stopAll();
        }
        return START_NOT_STICKY;
    }

    private void release() {
        try { if (mp != null) { mp.release(); mp = null; } } catch (Exception e) { }
        try { if (fr != null && am != null && Build.VERSION.SDK_INT >= 26) am.abandonAudioFocusRequest(fr); } catch (Exception e) { }
    }

    private void stopAll() {
        h.removeCallbacksAndMessages(null);
        release();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() { release(); super.onDestroy(); }
}
