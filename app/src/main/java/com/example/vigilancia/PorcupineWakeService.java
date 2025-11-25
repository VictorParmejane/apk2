package com.example.vigilancia;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;

/**
 * Serviço foreground que mantém o microfone ativo para detectar a hotword "Assistente".
 * Pode abrir o app mesmo em segundo plano via AccessibilityService.
 */
public class PorcupineWakeService extends Service {

    private static final String TAG = "PorcupineWakeService";
    private static final String CHANNEL_ID = "IA_WAKE";
    private static final int NOTIF_ID = 202;

    private Thread detectorThread;
    private final AtomicBoolean ativo = new AtomicBoolean(false);
    private Porcupine porcupine;
    private AudioRecord recorder;

    @Override
    public void onCreate() {
        super.onCreate();
        criarNotificacao();
        iniciarDeteccao();
        Log.i(TAG, "Serviço Porcupine iniciado.");
    }

    private void criarNotificacao() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Detecção de Hotword", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(this, Table.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Assistente ativo")
                .setContentText("Diga \"Assistente\" para ativar voz")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();

        startForeground(NOTIF_ID, n);
    }

    private void iniciarDeteccao() {
        detectorThread = new Thread(() -> {
            try {
                porcupine = new Porcupine.Builder()
                        .setAccessKey("Vy9KR6FIP1Js1yrCvK6CFQNWJWnTYXM64GPvSdlSJMRu9V75VYpevg==")
                        .setModelPath("porcupine/porcupine_params_pt.pv")
                        .setKeywordPaths(new String[]{"porcupine/Assistente_pt_android_v3_0_0.ppn"})
                        .build(getApplicationContext());

                int frame = porcupine.getFrameLength();
                int sampleRate = porcupine.getSampleRate();
                short[] buffer = new short[frame];

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Sem permissão de microfone.");
                    stopSelf();
                    return;
                }

                recorder = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        frame * 2
                );

                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "Falha ao inicializar AudioRecord.");
                    stopSelf();
                    return;
                }

                recorder.startRecording();
                ativo.set(true);
                Log.i(TAG, "🟢 Porcupine ativo — aguardando hotword...");

                while (ativo.get()) {
                    int n = recorder.read(buffer, 0, frame);
                    if (!ativo.get()) break;
                    if (n > 0 && porcupine != null) {
                        int result = porcupine.process(buffer);
                        if (result >= 0) {
                            Log.i(TAG, "🎙 Hotword detectada!");
                            executarHotword();
                            Thread.sleep(1500);
                        }
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Erro na detecção: " + e.getMessage(), e);
            } finally {
                liberarRecursos();
            }
        });
        detectorThread.start();
    }

    private void executarHotword() {
        try {
            if (isAppInForeground()) {
                startService(new Intent(this, VoiceService.class));
            } else {
                Log.i(TAG, "App em segundo plano → tentando abrir com AccessibilityService");
                VoiceAccessibilityService.abrirVigilancia();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao executar hotword: " + e.getMessage(), e);
        }
    }

    private boolean isAppInForeground() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        List<ActivityManager.RunningAppProcessInfo> list = am.getRunningAppProcesses();
        if (list == null) return false;
        for (ActivityManager.RunningAppProcessInfo p : list) {
            if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                for (String pkg : p.pkgList)
                    if (pkg.equals(getPackageName())) return true;
            }
        }
        return false;
    }

    private void liberarRecursos() {
        try {
            ativo.set(false);
            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;
            }
            if (porcupine != null) {
                porcupine.delete();
                porcupine = null;
            }
            Log.i(TAG, "Recursos de áudio liberados.");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao liberar recursos: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        ativo.set(false);
        liberarRecursos();
        if (detectorThread != null) {
            detectorThread.interrupt();
            detectorThread = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}