package com.example.vigilancia;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
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
    private static final int NOTIF_ID = 101;
    private SpeechRecognizer recognizer;
    private Intent recIntent;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean ativo = true;

    @Override
    public void onCreate() {
        super.onCreate();
        criarNotificacao();
        iniciarReconhecimento();
        // watchdog: garante que volte a escutar se parar
        handler.postDelayed(verificador, 4000);
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
                .setContentTitle("Assistente ativo")
                .setContentText("Ouvindo comandos de voz…")
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, n);
    }

    /** cria apenas um recognizer e mantém reiniciando */
    private void iniciarReconhecimento() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { stopSelf(); return; }
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
                if (!ativo) return;
                ArrayList<String> falas = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (falas != null && !falas.isEmpty()) {
                    processarComando(falas.get(0).toLowerCase(Locale.ROOT));
                }
                recognizer.startListening(recIntent); // mesma instância
            }

            @Override
            public void onError(int error) {
                if (ativo) recognizer.startListening(recIntent);
            }
        });

        recognizer.startListening(recIntent);
    }

    /** watchdog: a cada 4 s tenta reiniciar se nada estiver ouvindo */
    private final Runnable verificador = new Runnable() {
        @Override public void run() {
            if (ativo) {
                try { recognizer.startListening(recIntent); } catch (Exception ignored) {}
                handler.postDelayed(this, 4000);
            }
        }
    };

    private void processarComando(String comando) {
        // Broadcast local
        Intent i = new Intent("IA_COMANDO");
        i.putExtra("texto", comando);
        i.setPackage(getPackageName());
        sendBroadcast(i);

        // --- comandos diretos ---
        if (comando.contains("segundo plano")) {
            Intent telaHome = new Intent(Intent.ACTION_MAIN);
            telaHome.addCategory(Intent.CATEGORY_HOME);
            telaHome.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(telaHome);
            return;
        }

        if (comando.contains("abrir vigilancia") || comando.contains("abrir vigilância")
                || comando.equals("vigilancia") || comando.equals("vigilância")) {
            abrirAppVigilancia();
        } else if (comando.equals("encerrar") || comando.contains("sair") || comando.contains("encerrar aplicativo")) {
            Intent fechar = new Intent("FECHAR_APP");
            sendBroadcast(fechar);
        } else if (comando.contains("encerrar assistente") || comando.contains("desligar assistente")
                || comando.contains("parar assistente") || comando.contains("desativar assistente")) {
            pararAssistente();
            Toast.makeText(this, "Assistente encerrado", Toast.LENGTH_SHORT).show();
        }
    }

    private void abrirAppVigilancia() {
        try {
            PackageManager pm = getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(getPackageName());
            if (launchIntent != null) {
                // mantém task existente; se não existir, reabre app
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(launchIntent);
                Toast.makeText(this, "🔹 Abrindo Vigilância", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Não foi possível abrir o aplicativo.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao abrir: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void pararAssistente() {
        ativo = false;
        handler.removeCallbacks(verificador);
        try {
            if (recognizer != null) {
                recognizer.cancel();
                recognizer.destroy();
            }
        } catch (Exception ignored) {}
        stopForeground(true);
        stopSelf();
    }

    @Override public int onStartCommand(@Nullable Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public void onDestroy() { pararAssistente(); super.onDestroy(); }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}