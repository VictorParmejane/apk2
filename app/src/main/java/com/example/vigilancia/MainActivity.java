package com.example.vigilancia;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView loadingText;
    private Handler handler = new Handler();
    private int dotCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadingText = findViewById(R.id.loadingText);

        // Animação dos pontos
        handler.postDelayed(loadingRunnable, 500);

        // Após 3 segundos, mudar para a tela com WebView
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(MainActivity.this, Table.class);
            startActivity(intent);
            finish();
        }, 3000);
    }

    private Runnable loadingRunnable = new Runnable() {
        @Override
        public void run() {
            dotCount = (dotCount + 1) % 4; // 0, 1, 2, 3
            String dots = new String(new char[dotCount]).replace("\0", ".");
            loadingText.setText("Loading" + dots);
            handler.postDelayed(this, 500);
        }
    };
}