package uz.namoz.vaqtlari;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Intent s = new Intent(c, AdhanService.class);
        if (i.getExtras() != null) s.putExtras(i.getExtras());
        ContextCompat.startForegroundService(c, s);
    }
}
