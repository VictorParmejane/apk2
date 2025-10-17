package com.example.vigilancia;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
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
import java.util.Locale;

@SuppressLint("UnspecifiedRegisterReceiverFlag")
public class Table extends AppCompatActivity {

    private static final int REQ_MIC = 10;
    private static final String PREFS = "AppPrefs";
    private static final String KEY_SNACKBAR = "snackbar_shown";

    private EditText searchField;
    private ListView listView;
    private FloatingActionButton fabVoice;
    private ArrayAdapter<String> adapter;
    private List<String> roteiroList;
    private SpeechRecognizer recognizer;

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

        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        listView.setOnItemClickListener((p, v, pos, id) -> abrirRoteiro(adapter.getItem(pos)));

        fabVoice.setVisibility(View.VISIBLE);
        fabVoice.setOnClickListener(v -> pedirPermissaoMicrofone());

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean shown = prefs.getBoolean(KEY_SNACKBAR, false);
        if (!shown) {
            mostrarSnackbarInicial();
            prefs.edit().putBoolean(KEY_SNACKBAR, true).apply();
        }

        IntentFilter filtro = new IntentFilter("IA_COMANDO");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(receiver, filtro, Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(receiver, filtro);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        if (recognizer != null) recognizer.destroy();
    }

    private void mostrarSnackbarInicial() {
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), "", Snackbar.LENGTH_INDEFINITE);
        ViewGroup layout = (ViewGroup) snackbar.getView();
        layout.setPadding(0, 0, 0, 0);

        View custom = getLayoutInflater().inflate(R.layout.snackbar_ia, null);
        AppCompatButton btnNao = custom.findViewById(R.id.btnNao);
        AppCompatButton btnAtivar = custom.findViewById(R.id.btnAtivar);

        btnNao.setOnClickListener(v -> snackbar.dismiss());
        btnAtivar.setOnClickListener(v -> {
            snackbar.dismiss();
            pedirPermissaoMicrofone();
        });

        layout.addView(custom, 0);
        snackbar.show();
    }

    private void pedirPermissaoMicrofone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        } else {
            iniciarReconhecimentoVoz();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarReconhecimentoVoz();
        } else {
            Toast.makeText(this, "Permissão de microfone negada.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    /** Nova implementação: reconhecimento de voz aqui na Activity */
    private void iniciarReconhecimentoVoz() {
        try {
            if (recognizer != null) {
                recognizer.destroy();
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao criar reconhecedor: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                Toast.makeText(Table.this, "IA ouvindo... 🎤", Toast.LENGTH_SHORT).show();
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> falas = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (falas != null && !falas.isEmpty()) {
                    String comando = falas.get(0).toLowerCase(Locale.ROOT);
                    processarComando(comando);
                }
                iniciarReconhecimentoVoz(); // continuar ouvindo
            }
            @Override public void onError(int error) {
                iniciarReconhecimentoVoz(); // reinicia
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        recognizer.startListening(intent);
    }

    /** Processa o comando de voz recebido */
    private void processarComando(String comando) {
        if (comando.contains("abrir roteiro")) {
            for (String roteiro : roteiroList) {
                String n = roteiro.replaceAll("\\D+", "");
                if (comando.contains(n)) {
                    abrirRoteiro(roteiro);
                    return;
                }
            }
        }

        if (comando.contains("voltar")) finish();
        if (comando.contains("desligar ia") || comando.contains("parar ia"))
            recognizer.destroy();
    }

    private void criarListaCompleta() {
        roteiroList = new ArrayList<>(Arrays.asList(
                "Roteiro 1","Roteiro 2","Roteiro 3","Roteiro 4","Roteiro 5",
                "Roteiro 6","Roteiro 7","Roteiro 8","Roteiro 9","Roteiro 10",
                "Roteiro 11","Roteiro 12","Roteiro 13","Roteiro 14","Roteiro 15",
                "Roteiro 16","Roteiro 17","Roteiro 18","Roteiro 19","Roteiro 20",
                "Roteiro 21","Roteiro 22","Roteiro 23","Roteiro 24","Roteiro 25",
                "Roteiro 26","Roteiro 27","Roteiro 28","Roteiro 29","Roteiro 30",
                "Roteiro 31","Roteiro 32","Roteiro 33","Roteiro 34","Roteiro 35",
                "Roteiro 36","Roteiro 37","Roteiro 38","Roteiro 39","Roteiro 40",
                "Roteiro 41","Roteiro 42","Roteiro 43","Roteiro 44","Roteiro 45",
                "Roteiro 46","Roteiro 47","Roteiro 48","Roteiro 49","Roteiro 50",
                "Roteiro 62","Roteiro 63","Roteiro 64","Roteiro 65","Roteiro 66",
                "Roteiro 67","Roteiro 68","Roteiro 69","Roteiro 73","Roteiro 74",
                "Roteiro 75","Roteiro 76","Roteiro 77","Roteiro 81","Roteiro 82",
                "Roteiro 85","Roteiro 89","Roteiro 90","Roteiro 91","Roteiro 92",
                "Roteiro 93","Roteiro 94","Roteiro 95","Roteiro 96","Roteiro 97",
                "Roteiro 98","Roteiro 99","Roteiro 101 - ambulâncias","Roteiro 114",
                "Roteiro 118","Roteiro 119","Roteiro 120","Roteiro 121","Roteiro 122",
                "Roteiro 123","Roteiro 124","Roteiro 125 Consultórios","Roteiro 127",
                "Roteiro 128","Roteiro 129","Roteiro 130","Roteiro 131","Roteiro 132",
                "Roteiro 133","Roteiro 134","Roteiro 135","Roteiro 136","Roteiro 137",
                "Roteiro 138","Roteiro 139","Roteiro 140","Roteiro 141","Roteiro 142",
                "Roteiro 143","Roteiro 144"
        ));
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String comando = intent.getStringExtra("texto");
            if (comando == null) return;
            comando = comando.toLowerCase(Locale.ROOT);
            processarComando(comando);
        }
    };

    private void abrirRoteiro(String nome) {
        Intent i = new Intent(this, WebViewPG.class);
        i.putExtra("roteiro_nome", nome);
        startActivity(i);
    }
}