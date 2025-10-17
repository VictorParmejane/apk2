package com.example.vigilancia;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

public class VoiceService extends Service {

    private static final int NOTIF_ID = 101;
    private static final String CHANNEL_ID = "IA_VOZ_CANAL";

    @Override
    public void onCreate() {
        super.onCreate();
        criarNotificacaoCompat();
        Toast.makeText(this, "IA de voz pronta 🔊", Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
        Toast.makeText(this, "IA desligada ❌", Toast.LENGTH_SHORT).show();
    }

    private void criarNotificacaoCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CHANNEL_ID, "IA de Voz", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(canal);
        }

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("IA de voz ativa")
                .setContentText("Reconhecimento ativo na tabela")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(NOTIF_ID, notif);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}