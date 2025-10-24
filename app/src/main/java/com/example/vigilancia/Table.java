package com.example.vigilancia;

import android.Manifest;
import android.app.ActivityManager;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Table extends AppCompatActivity {

    private static final int REQ_MIC = 10;
    private static final String PREFS = "AppPrefs";
    private static final String KEY_SNACKBAR = "snackbar_shown";

    private EditText searchField;
    private ListView listView;
    private FloatingActionButton fabVoice;
    private ArrayAdapter<String> adapter;
    private List<String> roteiroList;
    private BroadcastReceiver receiverIA;

    private boolean assistenteAtivo;

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

        assistenteAtivo = isServiceRunning(VoiceService.class);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                adapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable s){}
        });

        listView.setOnItemClickListener((p,v,pos,id)->abrirRoteiro(adapter.getItem(pos)));

        fabVoice.setOnClickListener(v -> alternarAssistente());

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean exibido = prefs.getBoolean(KEY_SNACKBAR, false);
        if (!exibido) {
            mostrarSnackbarInicial();
            prefs.edit().putBoolean(KEY_SNACKBAR, true).apply();
        }

        receiverIA = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String comando = intent.getStringExtra("texto");
                if (comando != null)
                    processarComando(comando.toLowerCase(Locale.ROOT));
            }
        };
        registrarReceiverCompat(receiverIA, new IntentFilter("IA_COMANDO"));

        // fechar app
        BroadcastReceiver fecharReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finishAffinity();
            }
        };
        registrarReceiverCompat(fecharReceiver, new IntentFilter("FECHAR_APP"));
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo s : am.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(s.service.getClassName())) return true;
        }
        return false;
    }

    private void alternarAssistente() {
        assistenteAtivo = isServiceRunning(VoiceService.class);
        if (assistenteAtivo) pararIA(); else pedirPermissaoMicrofone();
    }

    private void pedirPermissaoMicrofone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) iniciarIA();
        else ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,@NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if (requestCode==REQ_MIC && grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
            iniciarIA();
        } else {
            Toast.makeText(this,"Permissão de microfone negada.",Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:"+getPackageName()));
            startActivity(i);
        }
    }

    private void iniciarIA(){
        try{
            Intent serviceIntent = new Intent(this, VoiceService.class);
            startService(serviceIntent);
            Toast.makeText(this,"🎤 Assistente ativado",Toast.LENGTH_SHORT).show();
            assistenteAtivo = true;
        }catch(Exception e){
            Toast.makeText(this,"Erro ao iniciar assistente: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private void pararIA(){
        try{
            stopService(new Intent(this, VoiceService.class));
            Toast.makeText(this,"❌ Assistente desativado",Toast.LENGTH_SHORT).show();
            assistenteAtivo = false;
        }catch(Exception e){
            Toast.makeText(this,"Erro ao parar assistente: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
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

    private void processarComando(String comando){
        if(comando.contains("abrir roteiro")){
            for(String roteiro:roteiroList){
                String numero=roteiro.replaceAll("\\D+","");
                if(comando.contains(numero)){abrirRoteiro(roteiro);return;}
            }
        }
        if(comando.contains("segundo plano")){ moveTaskToBack(true); return; }
        if(comando.contains("encerrar assistente")
                || comando.contains("desligar assistente")
                || comando.contains("desativar assistente")
                || comando.contains("parar assistente")){ pararIA(); return; }

        if(comando.equals("encerrar") || comando.contains("encerrar aplicativo") || comando.contains("sair")){
            pararIA(); finishAffinity(); return;
        }
    }

    private void abrirRoteiro(String nome){
        Intent i = new Intent(this, WebViewPG.class);
        i.putExtra("roteiro_nome", nome);
        startActivity(i);
    }

    private void registrarReceiverCompat(BroadcastReceiver receiver, IntentFilter filter) {
        try {
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else if (Build.VERSION.SDK_INT >= 26) {
                Method m = Context.class.getMethod("registerReceiver", BroadcastReceiver.class, IntentFilter.class, int.class);
                m.invoke(this, receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else registerReceiver(receiver, filter);
        } catch (Exception e) { registerReceiver(receiver, filter); }
    }

    private void criarListaCompleta(){
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
                "Roteiro 143","Roteiro 144"));
    }

    @Override protected void onDestroy(){ super.onDestroy(); try{ unregisterReceiver(receiverIA);}catch(Exception ignored){} }
}