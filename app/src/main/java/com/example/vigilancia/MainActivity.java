package com.example.vigilancia;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView loadingText;
    private final Handler handler = new Handler();
    private int dotCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadingText = findViewById(R.id.loadingText);

        // ==== 🔹 Aguarda o layout carregar e ativa modo imersivo ====
        getWindow().getDecorView().post(this::enterImmersiveMode);

        // === 🔹 Animação "Loading…" ===
        handler.postDelayed(loadingRunnable, 500);

        // === 🔹 Após 3s, abrir a Table ===
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(MainActivity.this, Table.class);
            startActivity(intent);
            finish();
        }, 3000);
    }

    private void enterImmersiveMode() {
        View decorView = getWindow().getDecorView();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // API < 30
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    private final Runnable loadingRunnable = new Runnable() {
        @Override
        public void run() {
            dotCount = (dotCount + 1) % 4;
            String dots = new String(new char[dotCount]).replace("\0", ".");
            loadingText.setText("Loading" + dots);
            handler.postDelayed(this, 500);
        }
    };
}