package com.example.vigilancia;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.util.ArrayList;
import java.util.Locale;

public class VoiceService extends Service {

    private static final String CHANNEL_ID = "IA_COMANDO";
    private static final int NOTIF_ID = 303;
    private SpeechRecognizer recognizer;
    private Intent recIntent;

    @Override
    public void onCreate() {
        super.onCreate();
        criarNotificacao();
        iniciarOuvinte();
    }

    private void criarNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Comando de Voz", NotificationManager.IMPORTANCE_HIGH);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Ouvindo...")
                .setContentText("Pode falar...")
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, n);
    }

    private void iniciarOuvinte() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
            recIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            recIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());

            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}

                @Override
                public void onError(int error) { stopSelf(); }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        enviarComando(matches.get(0));
                    }
                    stopSelf();
                }
            });
            recognizer.startListening(recIntent);
        } else {
            stopSelf();
        }
    }

    private void enviarComando(String comando) {
        String cmd = comando.toLowerCase(Locale.ROOT);

        // Manda o texto exato para as Activities (Table ou WebView) processarem com sua lógica
        Intent broadcast = new Intent("IA_COMANDO");
        broadcast.putExtra("texto", cmd);
        broadcast.setPackage(getPackageName());
        sendBroadcast(broadcast);

        // Ações globais (sistema)
        if (cmd.contains("segundo plano") || cmd.contains("minimizar")) {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        }
        else if (cmd.contains("encerrar") || cmd.contains("fechar aplicativo")) {
            Intent fechar = new Intent("FECHAR_APP");
            fechar.setPackage(getPackageName());
            sendBroadcast(fechar);
            stopService(new Intent(this, PorcupineWakeService.class));
        }
        // Só tenta abrir se foi solicitado explicitamente
        else if (cmd.contains("abrir vigilancia") || cmd.contains("abrir vigilância")) {
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if(i!=null) {
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }
            } catch (Exception e){}
        }
    }

    @Override
    public void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}