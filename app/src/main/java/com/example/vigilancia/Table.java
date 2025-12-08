package com.example.vigilancia;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Table extends AppCompatActivity {

    private static final int REQ_PERMS = 10;
    private EditText searchField;
    private ListView listView;
    private FloatingActionButton fabVoice;
    private ArrayAdapter<String> adapter;
    private List<String> roteiroList;
    private BroadcastReceiver receiverIA, fecharReceiver;
    private boolean assistenteAtivo = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_table);

        searchField = findViewById(R.id.searchField);
        listView = findViewById(R.id.listView);
        fabVoice = findViewById(R.id.fabVoice);

        criarListaCompleta();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, roteiroList);
        listView.setAdapter(adapter);

        assistenteAtivo = isServiceRunning(PorcupineWakeService.class);
        atualizarIconeFab();

        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { adapter.getFilter().filter(s); }
            @Override public void afterTextChanged(Editable s) {}
        });

        listView.setOnItemClickListener((p, v, pos, id) -> {
            String item = adapter.getItem(pos);
            if (item != null && item.equalsIgnoreCase("Roteiro 1")) {
                abrirWebView("Roteiro 1", "https://protocolo.rondonopolis.mt.gov.br/embed/form/6");
            } else {
                abrirWebView(item, null);
            }
        });

        fabVoice.setOnClickListener(v -> alternarAssistente());

        // Receivers
        receiverIA = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String comando = intent.getStringExtra("texto");
                if (comando != null) processarComando(comando);
            }
        };
        registrarReceiverCompat(receiverIA, new IntentFilter("IA_COMANDO"));

        fecharReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { finishAffinity(); }
        };
        registrarReceiverCompat(fecharReceiver, new IntentFilter("FECHAR_APP"));

        verificarPermissaoSobreposicao();
    }

    private void verificarPermissaoSobreposicao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Snackbar.make(listView, "Permita sobreposição para comandos em 2º plano", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Permitir", v -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    }).show();
        }
    }

    private void alternarAssistente() {
        if (assistenteAtivo) pararIA();
        else pedirPermissoes();
    }

    private void pedirPermissoes() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            iniciarIA();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarIA();
        }
    }

    private void iniciarIA() {
        Intent i = new Intent(this, PorcupineWakeService.class);
        ContextCompat.startForegroundService(this, i);
        assistenteAtivo = true;
        atualizarIconeFab();
        Toast.makeText(this, "Modo Sentinela Ativado", Toast.LENGTH_SHORT).show();
    }

    private void pararIA() {
        stopService(new Intent(this, PorcupineWakeService.class));
        stopService(new Intent(this, VoiceService.class)); // Garante que o ouvinte também pare
        assistenteAtivo = false;
        atualizarIconeFab();
        Toast.makeText(this, "Assistente Desativado", Toast.LENGTH_SHORT).show();
    }

    private void atualizarIconeFab() {
        fabVoice.setImageDrawable(ContextCompat.getDrawable(this,
                assistenteAtivo ? android.R.drawable.ic_media_pause : android.R.drawable.ic_btn_speak_now));
    }

    private void processarComando(String comando) {
        if (comando.contains("abrir roteiro")) {
            for (String r : roteiroList) {
                String num = r.replaceAll("\\D+", "");
                if (comando.contains(num)) {
                    if (r.equals("Roteiro 1")) abrirWebView("Roteiro 1", "https://protocolo.rondonopolis.mt.gov.br/embed/form/6");
                    else abrirWebView(r, null);
                    return;
                }
            }
        }
        if (comando.contains("parar assistente") || comando.contains("desligar assistente")) {
            pararIA();
        }
    }

    private void abrirWebView(String nome, String url) {
        Intent i = new Intent(this, WebViewPG.class);
        i.putExtra("roteiro_nome", nome);
        if (url != null) i.putExtra("url_custom", url);
        startActivity(i);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo s : am.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(s.service.getClassName())) return true;
        }
        return false;
    }

    private void registrarReceiverCompat(BroadcastReceiver receiver, IntentFilter filter) {
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(receiver, filter);
        } catch (Exception e) { registerReceiver(receiver, filter); }
    }

    private void criarListaCompleta() {
        roteiroList = new ArrayList<>(Arrays.asList(
                "Roteiro 1","Roteiro 2","Roteiro 3","Roteiro 4","Roteiro 5","Roteiro 6","Roteiro 7","Roteiro 8","Roteiro 9","Roteiro 10",
                "Roteiro 11","Roteiro 12","Roteiro 13","Roteiro 14","Roteiro 15","Roteiro 16","Roteiro 17","Roteiro 18","Roteiro 19","Roteiro 20",
                "Roteiro 21","Roteiro 22","Roteiro 23","Roteiro 24","Roteiro 25","Roteiro 26","Roteiro 27","Roteiro 28","Roteiro 29","Roteiro 30",
                "Roteiro 31","Roteiro 32","Roteiro 33","Roteiro 34","Roteiro 35","Roteiro 36","Roteiro 37","Roteiro 38","Roteiro 39","Roteiro 40",
                "Roteiro 41","Roteiro 42","Roteiro 43","Roteiro 44","Roteiro 45","Roteiro 46","Roteiro 47","Roteiro 48","Roteiro 49","Roteiro 50",
                "Roteiro 62","Roteiro 63","Roteiro 64","Roteiro 65","Roteiro 66","Roteiro 67","Roteiro 68","Roteiro 69","Roteiro 73","Roteiro 74",
                "Roteiro 75","Roteiro 76","Roteiro 77","Roteiro 81","Roteiro 82","Roteiro 85","Roteiro 89","Roteiro 90","Roteiro 91","Roteiro 92",
                "Roteiro 93","Roteiro 94","Roteiro 95","Roteiro 96","Roteiro 97","Roteiro 98","Roteiro 99","Roteiro 101 - ambulâncias","Roteiro 114",
                "Roteiro 118","Roteiro 119","Roteiro 120","Roteiro 121","Roteiro 122","Roteiro 123","Roteiro 124","Roteiro 125 Consultórios","Roteiro 127",
                "Roteiro 128","Roteiro 129","Roteiro 130","Roteiro 131","Roteiro 132","Roteiro 133","Roteiro 134","Roteiro 135","Roteiro 136","Roteiro 137",
                "Roteiro 138","Roteiro 139","Roteiro 140","Roteiro 141","Roteiro 142","Roteiro 143","Roteiro 144"
        ));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(receiverIA); } catch (Exception ignored) {}
        try { unregisterReceiver(fecharReceiver); } catch (Exception ignored) {}
    }
}