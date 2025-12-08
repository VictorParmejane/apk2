package com.example.vigilancia;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.util.concurrent.atomic.AtomicBoolean;
import ai.picovoice.porcupine.Porcupine;

public class PorcupineWakeService extends Service {

    private static final String CHANNEL_ID = "IA_HOTWORD";
    private static final int NOTIF_ID = 202;
    // Sua chave original
    private static final String ACCESS_KEY = "BK9GBVW46qk2YFYYShA4tgbGEDFeeZeaOR6pm+QgtXOkGq7RPJEAzw==";

    private Thread detectorThread;
    private final AtomicBoolean ativo = new AtomicBoolean(false);
    private Porcupine porcupine;
    private AudioRecord recorder;

    @Override
    public void onCreate() {
        super.onCreate();
        criarNotificacao();
        iniciarDeteccao();
    }

    private void criarNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Modo Sentinela", NotificationManager.IMPORTANCE_LOW);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Intent i = new Intent(this, Table.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Vigilância Ativa")
                .setContentText("Aguardando 'Assistente'...")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, n);
    }

    private void iniciarDeteccao() {
        detectorThread = new Thread(() -> {
            try {
                porcupine = new Porcupine.Builder()
                        .setAccessKey(ACCESS_KEY)
                        .setModelPath("porcupine/porcupine_params_pt.pv")
                        .setKeywordPaths(new String[]{"porcupine/Assistente_pt_android_v3_0_0.ppn"})
                        .build(getApplicationContext());

                int frame = porcupine.getFrameLength();
                int rate = porcupine.getSampleRate();
                short[] buffer = new short[frame];

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    stopSelf(); return;
                }

                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, frame * 2);
                recorder.startRecording();
                ativo.set(true);

                while (ativo.get()) {
                    int n = recorder.read(buffer, 0, frame);
                    if (n > 0) {
                        int result = porcupine.process(buffer);
                        if (result >= 0) {
                            // ACORDA O APP SEM RESETAR A TELA
                            executarLogicaAntiLoop();
                            Thread.sleep(1500);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("Porcupine", "Erro: " + e.getMessage());
            } finally {
                liberarRecursos();
            }
        });
        detectorThread.start();
    }

    private void executarLogicaAntiLoop() {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // 1. Permissão de Sobreposição (Android 10+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Permita sobreposição!", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return;
                }

                // 2. A MÁGICA: Pega a tarefa existente (WebView ou Table) e traz pra frente
                Intent i = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if (i != null) {
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(i);
                }

                // 3. Delay para evitar Crash no Android 14 e iniciar microfone
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        Intent voiceIntent = new Intent(PorcupineWakeService.this, VoiceService.class);
                        ContextCompat.startForegroundService(PorcupineWakeService.this, voiceIntent);
                    } catch (Exception e) {}
                }, 600);

            } catch (Exception e) {
                Log.e("Porcupine", "Erro ativação: " + e.getMessage());
            }
        });
    }

    private void liberarRecursos() {
        try {
            ativo.set(false);
            if (recorder != null) { recorder.stop(); recorder.release(); recorder = null; }
            if (porcupine != null) { porcupine.delete(); porcupine = null; }
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        ativo.set(false);
        liberarRecursos();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}