package com.example.vigilancia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.lang.reflect.Method;
import java.util.Locale;

public class WebViewPG extends AppCompatActivity {

    private static final String SITE_URL = "https://protocolo.rondonopolis.mt.gov.br/";
    private WebView webView;
    private BroadcastReceiver receiverComando;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        // Configura toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Voltar à Tabela");
        toolbar.setTitleTextColor(getResources().getColor(android.R.color.white));
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Configura WebView
        webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                view.loadUrl(uri.toString());
                return false;
            }
        });
        webView.loadUrl(SITE_URL);

        // 🔹 Receiver que reage aos comandos da IA de voz (enviados via broadcast)
        receiverComando = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String comando = intent.getStringExtra("texto");
                if (comando != null)
                    processarComando(comando.toLowerCase(Locale.ROOT));
            }
        };

        registrarReceiverCompat(receiverComando, new IntentFilter("IA_COMANDO"));
    }

    /** 🔹 Registro compatível com API 24 → 34 (sem warnings) */
    private void registrarReceiverCompat(BroadcastReceiver receiver, IntentFilter filter) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                // Android 13+
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else if (Build.VERSION.SDK_INT >= 26) {
                // Android 8–12 → usa reflexão
                Method m = Context.class.getMethod(
                        "registerReceiver",
                        BroadcastReceiver.class,
                        IntentFilter.class,
                        int.class);
                m.invoke(this, receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                // Android 7 ou inferior
                registerReceiver(receiver, filter);
            }
        } catch (Exception e) {
            registerReceiver(receiver, filter);
        }
    }

    /** 🔹 Executa as ações conforme o comando reconhecido */
    private void processarComando(String comando) {
        if (comando.contains("abrir protocolo")) {
            executarJavaScript("Abrir Protocolo");
            Toast.makeText(this, "🔹 Abrindo protocolo...", Toast.LENGTH_SHORT).show();
        }
        else if (comando.contains("consultar protocolo")) {
            executarJavaScript("Consultar Protocolo");
            Toast.makeText(this, "🔹 Consultando protocolo...", Toast.LENGTH_SHORT).show();
        }
        else if (comando.contains("voltar") || comando.contains("voltar tabela")) {
            Toast.makeText(this, "↩️ Voltando à tabela...", Toast.LENGTH_SHORT).show();
            finish();
        }
        else if (comando.contains("sair") || comando.contains("encerrar")) {
            Toast.makeText(this, "👋 Encerrando aplicativo...", Toast.LENGTH_SHORT).show();
            finishAffinity();
        }
    }

    /** 🔹 Injeção de JavaScript que simula clique em botões/links com o texto informado */
    private void executarJavaScript(String textoBotao) {
        String script =
                "javascript:(function(){"
                        + "var els=document.querySelectorAll('button,a,input[type=button],input[type=submit]');"
                        + "for(var i=0;i<els.length;i++){"
                        + " if(els[i].innerText && els[i].innerText.toLowerCase().includes('"
                        + textoBotao.toLowerCase()
                        + "')){els[i].click();return;}"
                        + "}"
                        + "})();";
        webView.evaluateJavascript(script, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(receiverComando); } catch (Exception ignored) {}
    }
}