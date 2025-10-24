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

        // ---- Toolbar ----
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Voltar à Tabela");
        toolbar.setTitleTextColor(getResources().getColor(android.R.color.white));
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        // ---- WebView ----
        webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.getSettings().setSupportMultipleWindows(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // garante que o histórico de navegação fique dentro da WebView
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        webView.loadUrl(SITE_URL);

        // ---- Receiver de comandos de voz ----
        receiverComando = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String comando = intent.getStringExtra("texto");
                if (comando != null)
                    processarComando(comando.toLowerCase(Locale.ROOT));
            }
        };
        registrarReceiverCompat(receiverComando, new IntentFilter("IA_COMANDO"));

        // ---- Receiver para fechar app ----
        BroadcastReceiver fecharReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finishAffinity();
            }
        };
        registrarReceiverCompat(fecharReceiver, new IntentFilter("FECHAR_APP"));
    }

    // Registro compatível com todas as APIs
    private void registrarReceiverCompat(BroadcastReceiver receiver, IntentFilter filter) {
        try {
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else if (Build.VERSION.SDK_INT >= 26) {
                Method m = Context.class.getMethod(
                        "registerReceiver", BroadcastReceiver.class, IntentFilter.class, int.class);
                m.invoke(this, receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(receiver, filter);
            }
        } catch (Exception e) {
            registerReceiver(receiver, filter);
        }
    }

    /** Processa os comandos vindos por voz */
    private void processarComando(String comando) {
        try {
            // --- Ações do site ---
            if (comando.contains("abrir protocolo"))
                executarJavaScript("Abrir Protocolo");
            else if (comando.contains("consultar protocolo"))
                executarJavaScript("Consultar Protocolo");
            else if (comando.contains("ver detalhes"))
                executarJavaScript("Ver Detalhes");
            else if (comando.contains("solicitar serviço") || comando.contains("solicitar servico"))
                executarJavaScript("Solicitar Serviço");
            else if (comando.equals("solicitar") || comando.contains("solicitar "))
                executarJavaScript("Solicitar");
            else if (comando.contains("filtrar"))
                executarJavaScript("Filtrar");
            else if (comando.contains("consultar"))
                executarJavaScript("Consultar");
            else if (comando.contains("fechar"))
                executarJavaScript("Fechar");

                // --- Voltar à tabela ---
            else if (comando.contains("voltar à tabela") || comando.contains("voltar a tabela")) {
                Intent i = new Intent(this, Table.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(i);
                finish();
            }

            // --- Voltar apenas dentro da WebView ---
            else if (comando.equals("voltar")) {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    // Tenta achar botão "Voltar" no DOM se o site for SP‑App
                    executarJavaScript("voltar");
                    Toast.makeText(this, "🔹 Tentando voltar na página...", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao executar comando: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** Executa cliques em botões/links no DOM (case‑insensitive) */
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