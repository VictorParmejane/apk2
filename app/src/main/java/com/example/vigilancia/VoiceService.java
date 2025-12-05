package com.example.vigilancia;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
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
        // Watchdog para garantir que o serviço não morra
        handler.postDelayed(watchdog, 4000);
    }

    private void criarNotificacao() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "IA de Voz",
                    NotificationManager.IMPORTANCE_LOW
            );
            if (nm != null) {
                nm.createNotificationChannel(ch);
            }
        }

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Certifique-se de ter um ícone válido
                .setContentTitle("Vigilância Ativa")
                .setContentText("Ouvindo comandos...")
                .setOngoing(true)
                .build();

        startForeground(NOTIF_ID, n);
    }

    private void iniciarReconhecimento() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf();
            return;
        }

        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Exception ignored) {}
        }

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
            public void onError(int error) {
                // Reinicia se der erro (ex: tempo esgotado ou barulho)
                if (ativo) {
                    reiniciarOuvinte(1000);
                }
            }

            @Override
            public void onResults(Bundle results) {
                if (!ativo) return;

                ArrayList<String> falas = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (falas != null && !falas.isEmpty()) {
                    String texto = falas.get(0).toLowerCase(Locale.ROOT).trim();
                    processarComando(texto);
                }
                // Continua ouvindo
                reiniciarOuvinte(500);
            }
        });

        try {
            recognizer.startListening(recIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Auxiliar para reiniciar sem travar a thread
    private void reiniciarOuvinte(int delay) {
        handler.postDelayed(() -> {
            if (ativo && recognizer != null) {
                try {
                    recognizer.startListening(recIntent);
                } catch (Exception ignored) {}
            }
        }, delay);
    }

    // Watchdog: verifica periodicamente se o recognizer precisa ser "acordado"
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (ativo) {
                try {
                    // Tenta iniciar novamente apenas para garantir
                    // (O SpeechRecognizer ignora se já estiver ouvindo)
                    if (recognizer != null) recognizer.startListening(recIntent);
                } catch (Exception ignored) {}
                handler.postDelayed(this, 5000); // Verifica a cada 5 seg
            }
        }
    };

    private void processarComando(String comando) {
        // 1. Envia comando para as Activities (Table ou WebViewPG) processarem campos e navegação
        Intent intent = new Intent("IA_COMANDO");
        intent.putExtra("texto", comando);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);

        // --- Comandos Globais (Gerenciados pelo Serviço) ---

        // A. Segundo Plano
        if (comando.contains("segundo plano") || comando.contains("minimizar")) {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
            return;
        }

        // B. Abrir Vigilância (Traz o app para frente)
        if (comando.contains("abrir vigilancia") ||
                comando.contains("abrir vigilância") ||
                comando.equals("vigilancia") ||
                comando.equals("vigilância")) {
            abrirAppVigilancia();
            return;
        }

        // C. Encerrar Tudo
        if (comando.equals("encerrar") ||
                comando.contains("sair") ||
                comando.contains("fechar aplicativo") ||
                comando.contains("encerrar aplicativo")) {

            // Avisa activities para fecharem
            Intent fechar = new Intent("FECHAR_APP");
            fechar.setPackage(getPackageName());
            sendBroadcast(fechar);

            pararAssistente();
            return;
        }

        // D. Parar Apenas o Assistente
        if (comando.contains("encerrar assistente") ||
                comando.contains("desligar assistente") ||
                comando.contains("parar assistente") ||
                comando.contains("desativar assistente")) {

            pararAssistente();

            // Avisa a Table para atualizar o botão/ícone
            Intent desligado = new Intent("ASSISTENTE_DESATIVADO");
            desligado.setPackage(getPackageName());
            sendBroadcast(desligado);

            Toast.makeText(this, "Assistente encerrado", Toast.LENGTH_SHORT).show();
        }
    }

    // --- CORREÇÃO DO COMANDO ABRIR VIGILÂNCIA ---
    private void abrirAppVigilancia() {
        try {
            // Verifica permissão "Sobrepor a outros apps" (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Para abrir por voz, permita 'Sobrepor a outros apps'", Toast.LENGTH_LONG).show();

                // Abre a tela de configuração para o usuário dar permissão
                Intent intentSettings = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                intentSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intentSettings);
                return;
            }

            // Abre a Activity Table diretamente
            Intent i = new Intent(this, Table.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); // Traz para frente se já existir
            i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Erro ao abrir: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void pararAssistente() {
        ativo = false;
        handler.removeCallbacks(watchdog);
        try {
            if (recognizer != null) {
                recognizer.cancel();
                recognizer.destroy();
                recognizer = null;
            }
        } catch (Exception ignored) {}
        stopForeground(true);
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        // START_STICKY faz o serviço tentar reiniciar se o Android matá-lo por falta de memória
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        pararAssistente();
        super.onDestroy();
    }
}