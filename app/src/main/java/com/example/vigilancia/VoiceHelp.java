package com.example.vigilancia;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ScrollView;
import android.widget.TextView;

public class VoiceHelp {

    public static void showCommands(Context ctx) {
        String comandos =
                "📣 COMANDOS DE VOZ DISPONÍVEIS\n\n" +
                        "🗣️ CAMPOS:\n" +
                        "• Nome [texto]\n" +
                        "• CPF [números]\n" +
                        "• Telefone [números]\n" +
                        "• Email [endereço]\n" +
                        "• Endereço [texto]\n" +
                        "• Informações [texto]\n\n" +
                        "🔄 AÇÕES:\n" +
                        "• Erro [campo] [palavra] com/sem [letras]\n" +
                        "  Ex: Erro nome Parmegiani com J\n" +
                        "• Adicionar [texto] em [campo]\n" +
                        "  Ex: Adicionar Pereira em nome\n" +
                        "• Apagar [campo]\n" +
                        "• Limpar formulário\n\n" +
                        "⬇️ ROLAGEM:\n" +
                        "• Descer / Subir\n" +
                        "• Topo / Final da página\n\n" +
                        "ℹ️ Fale 'Ajuda' ou 'Comandos de voz' a qualquer momento.";

        TextView tv = new TextView(ctx);
        tv.setText(comandos);
        tv.setTextSize(16f);
        tv.setPadding(40,30,40,30);

        ScrollView sv = new ScrollView(ctx);
        sv.addView(tv);

        new AlertDialog.Builder(ctx)
                .setTitle("Comandos de voz")
                .setView(sv)
                .setPositiveButton("Fechar", null)
                .show();
    }
}