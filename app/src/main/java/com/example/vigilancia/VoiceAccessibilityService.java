package com.example.vigilancia;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Permite abrir o app mesmo em segundo plano.
 * Ative manualmente: Configurações → Acessibilidade → Vigilância → Ativar.
 */
public class VoiceAccessibilityService extends AccessibilityService {

    private static VoiceAccessibilityService instancia;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instancia = this;
        Log.i("VoiceAccessibility", "Serviço de acessibilidade conectado.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    /** Chamada pelo PorcupineWakeService para abrir o app */
    public static void abrirVigilancia() {
        if (instancia == null) {
            Log.e("VoiceAccessibility", "Serviço de acessibilidade não está ativo.");
            return;
        }
        try {
            Intent i = new Intent(instancia, Table.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            instancia.startActivity(i);
            Log.i("VoiceAccessibility", "App Vigilância aberto via AccessibilityService.");
        } catch (Exception e) {
            Log.e("VoiceAccessibility", "Erro ao abrir app: " + e.getMessage(), e);
        }
    }
}