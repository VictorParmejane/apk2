package com.example.vigilancia;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceService extends Service {

    private static final String CHANNEL_ID = "IA_VOZ_CANAL";
    private static final int NOTIFICATION_ID = 101;

    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();

        // 🔹 1º passo: iniciar notificação imediatamente
        criarNotificacao();

        // 🔹 2º passo: iniciar reconhecimento de voz no MainLooper
        handler.postDelayed(this::iniciarReconhecimento, 1000);
        Toast.makeText(this, "🎤 IA de voz iniciada", Toast.LENGTH_SHORT).show();
    }

    private void criarNotificacao() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "IA de Voz",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("IA de voz ativa")
                .setContentText("Ouvindo comandos de voz…")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notif);
    }

    /** 🔹 Configura o reconhecimento */
    private void iniciarReconhecimento() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Reconhecimento indisponível neste dispositivo.",
                    Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    String comando = list.get(0).toLowerCase(Locale.ROOT);
                    enviarBroadcast(comando);
                }
                reiniciarEscuta();
            }

            @Override
            public void onError(int error) {
                reiniciarEscuta();
            }
        });

        iniciarEscuta();
    }

    private void iniciarEscuta() {
        try {
            recognizer.startListening(recognizerIntent);
        } catch (Exception e) {
            handler.postDelayed(this::iniciarEscuta, 800);
        }
    }

    private void reiniciarEscuta() {
        handler.postDelayed(this::iniciarEscuta, 1000);
    }

    private void enviarBroadcast(String texto) {
        Intent i = new Intent("IA_COMANDO");
        i.putExtra("texto", texto);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (recognizer != null) recognizer.destroy();
        } catch (Exception ignored) {}
        stopForeground(true);
        Toast.makeText(this, "IA de voz encerrada ❌", Toast.LENGTH_SHORT).show();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}