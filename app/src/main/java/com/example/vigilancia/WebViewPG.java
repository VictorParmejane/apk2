package com.example.vigilancia;

import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class WebViewPG extends AppCompatActivity {

    private static final String SITE_URL = "https://protocolo.rondonopolis.mt.gov.br/";
    private WebView webView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        // 🔹 Configurar Toolbar como ActionBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Voltar a Tabela");
        toolbar.setTitleTextColor(getResources().getColor(android.R.color.white));
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);

        // Clicar na seta → voltar
        toolbar.setNavigationOnClickListener(v -> finish());

        // 🔹 Configurar WebView
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

        // 🔹 Carregar o site
        webView.loadUrl(SITE_URL);
    }
}