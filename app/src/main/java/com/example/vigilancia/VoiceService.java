package com.example.vigilancia;
import android.os.IBinder;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceService extends Service {

    private static final String CHANNEL_ID = "IA_VOZ_CANAL";
    private static final int NOTIF_ID = 101;
    private SpeechRecognizer recognizer;
    private Intent recIntent;

    @Override
    public void onCreate() {
        super.onCreate();
        criarNotificacao();
        iniciarRecognizer();
    }

    private void criarNotificacao() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "IA de Voz", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Assistente escutando...")
                .setContentText("Aguardando comando de voz")
                .setOngoing(false)
                .build();
        startForeground(NOTIF_ID, n);
    }

    private void iniciarRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf();
            return;
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        recIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

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
                ArrayList<String> falas = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (falas != null && !falas.isEmpty()) {
                    String comando = falas.get(0).toLowerCase(Locale.ROOT);
                    enviarComando(comando);
                }
                stopSelf();
            }

            @Override
            public void onError(int error) { stopSelf(); }
        });

        recognizer.startListening(recIntent);
    }

    private void enviarComando(String comando) {
        Intent i = new Intent("IA_COMANDO");
        i.putExtra("texto", comando);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    @Override
    public void onDestroy() {
        try {
            if (recognizer != null) {
                recognizer.cancel();
                recognizer.destroy();
            }
        } catch (Exception ignored) {}
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}