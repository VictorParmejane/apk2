package com.example.vigilancia;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WebViewPG extends AppCompatActivity {

    private static final String SITE_URL = "https://protocolo.rondonopolis.mt.gov.br/";
    private static final String PREF_DIC = "DicFonemico";
    private WebView webView;
    private BroadcastReceiver receiverComando, receiverFechar;
    private SharedPreferences prefs;
    private final Map<String,String> dicionarioFonemico = new HashMap<>();

    private String ultimoCampo = "";
    private String ultimoValor = "";
    private AlertDialog dialogAjuda;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        Toolbar tb = findViewById(R.id.toolbar);
        tb.setTitle("Voltar à Tabela");
        tb.setTitleTextColor(getResources().getColor(android.R.color.white));
        tb.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        tb.setNavigationOnClickListener(v -> finish());

        carregarDicionario();

        webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                v.loadUrl(r.getUrl().toString());
                return true;
            }
        });

        String custom = getIntent().getStringExtra("url_custom");
        webView.loadUrl(custom != null && !custom.isEmpty() ? custom : SITE_URL);

        receiverComando = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String cmd = i.getStringExtra("texto");
                if (cmd != null)
                    processarComando(cmd.toLowerCase(Locale.ROOT).trim());
            }
        };
        registrarReceiverCompat(receiverComando,new IntentFilter("IA_COMANDO"));

        receiverFechar = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { encerrarAplicativo(); }
        };
        registrarReceiverCompat(receiverFechar,new IntentFilter("FECHAR_APP"));
    }

    private void registrarReceiverCompat(BroadcastReceiver r, IntentFilter f){
        try{
            if(Build.VERSION.SDK_INT>=33)
                registerReceiver(r,f,Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(r,f);
        }catch(Exception e){registerReceiver(r,f);}
    }

    private void carregarDicionario(){
        prefs = getSharedPreferences(PREF_DIC, MODE_PRIVATE);
        Map<String,?> salvos = prefs.getAll();
        for(String k:salvos.keySet()){
            String v=(String)salvos.get(k);
            if(v!=null)dicionarioFonemico.put(k,v);
        }
    }

    private void salvarNovoSinonimo(String errado,String certo){
        dicionarioFonemico.put(errado,certo);
        if(prefs==null)prefs=getSharedPreferences(PREF_DIC,MODE_PRIVATE);
        prefs.edit().putString(errado,certo).apply();
    }

    // ======================================================
    private void processarComando(String comando){
        try{
            if (comando.matches(".*(ajuda|comando|comandos de voz).*")) { mostrarAjuda(); return; }
            if (comando.contains("fechar") && dialogAjuda != null && dialogAjuda.isShowing()) {
                dialogAjuda.dismiss(); return;
            }

            if (comando.startsWith("apagar ")) { apagarCampo(comando.replaceFirst("apagar\\s+","").trim()); return; }
            if (comando.contains("limpar formulario")||comando.contains("limpar formulário")){ limparFormulario(); return; }
            if (comando.contains("voltar a tabela")){ finish(); return; }
            if (comando.contains("encerrar")||comando.contains("fechar aplicativo")||comando.contains("sair")){ encerrarAplicativo(); return; }
            if (comando.startsWith("erro")){ tratarErro(comando.replaceFirst("erro,?","").trim()); return; }
            if (comando.startsWith("adicionar")){ tratarAdicionar(comando.replaceFirst("adicionar\\s*","").trim()); return; }

            // ===== ROLAGEM PADRÃO =====
            if (comando.contains("meio") || comando.contains("centro")) { scroll("mid"); return; }
            if (comando.contains("final") || comando.contains("inferior")) { scroll("bottom"); return; }
            if (comando.contains("topo") || comando.contains("início") || comando.contains("inicio") || comando.contains("superior")) { scroll("top"); return; }

            if (comando.contains("descer tudo")) { scroll("bottom"); return; }
            if (comando.contains("subir tudo")) { scroll("top"); return; }

            // ===== SCROLL “X VEZES” =====
            if (comando.matches(".*(subir|suba|descer|desça).*\\d.*") ||
                    comando.matches(".*(duas|três|tres|quatro|cinco|seis|sete|oito|nove|dez).*vez")) {
                int vezes = extrairNumeroVezes(comando);
                boolean subir = comando.contains("sub");
                for (int i = 0; i < vezes; i++) {
                    final int step = i;
                    webView.postDelayed(() -> {
                        if (subir) scroll("up",1);
                        else scroll("down",1);
                    }, step * 400L); // atraso entre execuções
                }
                return;
            }

            // comandos simples
            if (comando.contains("descer") || comando.contains("desça")) { scroll("down",1); return; }
            if (comando.contains("subir") || comando.contains("suba")) { scroll("up",1); return; }

            // ===== CAMPOS =====
            String comandoLimpo = comando.toLowerCase(Locale.ROOT)
                    .replaceAll("(?i)(nome completo|cpf completo|telefone completo|email completo|endereco completo|endereço completo|informações do equipamento|informacoes do equipamento|informações|informacoes|nome|cpf|telefone|email|e-mail|e mail|endereco|endereço)", "")
                    .trim();
            comandoLimpo = aplicarDicionario(corrigirSoletrado(corrigirEntradaDitado(comandoLimpo)));

            if (comando.contains("nome")) preencherCampo("Nome Completo", capitalizarInteligente(comandoLimpo));
            else if (comando.contains("cpf")) preencherCampo("CPF", formatarCPF(comandoLimpo.replaceAll("\\D+","")));
            else if (comando.contains("telefone")) preencherCampo("Telefone", formatarTelefone(comandoLimpo.replaceAll("\\D+","")));
            else if (comando.contains("email") || comando.contains("e-mail") || comando.contains("e mail"))
                preencherCampo("Email", formatarEmail(comandoLimpo));
            else if (comando.contains("endereco") || comando.contains("endereço"))
                preencherCampo("Endereço completo", capitalizarInteligente(comandoLimpo));
            else if (comando.contains("informações do equipamento") || comando.contains("informacoes do equipamento")) {
                // limpa o texto "informações do equipamento" do início
                String val = capitalizarInteligente(
                        comando.replaceFirst("(?i)informações?\\s+do\\s+equipamento","").trim()
                );
                preencherCampo("Informações do equipamento", val);
            } else if (comando.contains("informações") || comando.contains("informacoes")) {
                preencherCampo("Informações do equipamento", capitalizarInteligente(comandoLimpo));
            }

        } catch (Exception e) {
            Toast.makeText(this,"Erro: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    private int extrairNumeroVezes(String t){
        if(t.contains("10")||t.contains("dez"))return 10;
        if(t.contains("9")||t.contains("nove"))return 9;
        if(t.contains("8")||t.contains("oito"))return 8;
        if(t.contains("7")||t.contains("sete"))return 7;
        if(t.contains("6")||t.contains("seis"))return 6;
        if(t.contains("5")||t.contains("cinco"))return 5;
        if(t.contains("4")||t.contains("quatro"))return 4;
        if(t.contains("3")||t.contains("três")||t.contains("tres"))return 3;
        if(t.contains("2")||t.contains("duas")||t.contains("dois"))return 2;
        return 1;
    }

    private void tratarErro(String msg) {
        try {
            if (!msg.contains(" com ")) return;
            String[] p = msg.split("\\s+com\\s+");
            salvarNovoSinonimo(p[0].trim().toLowerCase(Locale.ROOT), p[1].trim().toLowerCase(Locale.ROOT));
            Toast.makeText(this,"Treino salvo",Toast.LENGTH_SHORT).show();
        } catch(Exception ignored){}
    }

    private void tratarAdicionar(String c){}

    private String aplicarDicionario(String e){
        if(e==null)return"";
        String[] w=e.split("\\s+");
        StringBuilder sb=new StringBuilder();
        for(String p:w){
            String sub=dicionarioFonemico.getOrDefault(p.toLowerCase(Locale.ROOT),p);
            sb.append(sub).append(" ");
        }
        return sb.toString().trim();
    }

    private String corrigirEntradaDitado(String t){
        String[] l=t.split(" ");
        if(l.length>3){int c=0;for(String s:l)if(s.length()==1)c++;if(c>=l.length*0.6)t=String.join("",l);}
        return t.trim().replaceAll("\\s+"," ");
    }

    private String corrigirSoletrado(String x){return x;}

    private String capitalizarInteligente(String txt){
        txt=txt.trim();
        if(txt.isEmpty())return txt;
        String[]min={"de","do","dos","da","das"};
        StringBuilder sb=new StringBuilder();
        for(String p:txt.split(" ")){
            boolean m=false;for(String n:min)if(p.equalsIgnoreCase(n))m=true;
            if(m)sb.append(p.toLowerCase()).append(" ");
            else sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }

    private String formatarCPF(String c){
        if(c==null)return""; c=c.replaceAll("\\D","");
        if(c.length()==11)return c.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})","$1.$2.$3-$4");
        return c;
    }

    private String formatarTelefone(String t){
        if(t==null)return""; t=t.replaceAll("\\D+","");
        if(t.length()==11)return t.replaceFirst("(\\d{2})(\\d{5})(\\d{4})","($1) $2-$3");
        if(t.length()==10)return t.replaceFirst("(\\d{2})(\\d{4})(\\d{4})","($1) $2-$3");
        return t;
    }

    private String formatarEmail(String e){
        if(e==null)return""; e=e.replaceAll("\\s+","");
        e=e.replace("arroba","@").replace("ponto",".");
        return e;
    }

    private void preencherCampo(String label,String valor){
        try{
            String js="javascript:(function(){let l=document.querySelectorAll('label');for(let i=0;i<l.length;i++){if(l[i].innerText.toLowerCase().includes('"+label.toLowerCase()+"')){let input=l[i].nextElementSibling;if(input&&(input.tagName==='INPUT'||input.tagName==='TEXTAREA')){input.focus();input.value='"+valor.trim()+"';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));break;}}}})();";
            webView.evaluateJavascript(js,null);
        }catch(Exception e){}
    }

    private void apagarCampo(String campo){
        String label=campo.toLowerCase();
        switch(label){
            case"nome":label="Nome Completo";break;
            case"cpf":label="CPF";break;
            case"telefone":label="Telefone";break;
            case"email":case"e-mail":case"e mail":label="Email";break;
            case"endereco":case"endereço":label="Endereço completo";break;
            case"informacoes":case"informações":label="Informações do equipamento";break;
        }
        String js="javascript:(function(){let l=document.querySelectorAll('label');for(let i=0;i<l.length;i++){if(l[i].innerText.toLowerCase().includes('"+label.toLowerCase()+"')){let x=l[i].nextElementSibling;if(x){x.value='';x.dispatchEvent(new Event('input',{bubbles:true}));x.dispatchEvent(new Event('change',{bubbles:true}));break;}}}})();";
        webView.evaluateJavascript(js,null);
    }

    private void limparFormulario(){
        String js="javascript:(function(){let c=document.querySelectorAll('input,textarea');for(let i=0;i<c.length;i++){c[i].value='';c[i].dispatchEvent(new Event('input',{bubbles:true}));c[i].dispatchEvent(new Event('change',{bubbles:true}));}})();";
        webView.evaluateJavascript(js,null);
    }

    private void scroll(String dir){scroll(dir,1);}
    private void scroll(String dir,int vezes){
        String js="";
        if(dir.equals("down")) js="window.scrollBy({top:window.innerHeight*"+vezes+",behavior:'smooth'});";
        else if(dir.equals("up")) js="window.scrollBy({top:-window.innerHeight*"+vezes+",behavior:'smooth'});";
        else if(dir.equals("mid")) js="window.scrollTo({top:document.body.scrollHeight/2,behavior:'smooth'});";
        else if(dir.equals("bottom")) js="window.scrollTo({top:document.body.scrollHeight,behavior:'smooth'});";
        else if(dir.equals("top")) js="window.scrollTo({top:0,behavior:'smooth'});";
        webView.evaluateJavascript("javascript:(function(){"+js+"})();",null);
    }

    private void encerrarAplicativo(){
        try{stopService(new Intent(this,VoiceService.class));}catch(Exception ignored){}
        Toast.makeText(this,"Encerrando aplicativo…",Toast.LENGTH_SHORT).show();
        finishAffinity();System.exit(0);
    }

    private void mostrarAjuda(){
        String help="📣 COMANDOS DE VOZ DISPONÍVEIS\n\n"+
                "🗣️ CAMPOS:\nNome, CPF, Telefone, Email, Endereço, Informações\n\n"+
                "🔄 AÇÕES:\nAdicionar, Apagar, Corrigir (Erro), Limpar formulário\n\n"+
                "⬆️⬇️ ROLAGEM:\nSubir, Descer, Subir 3 vezes, Descer 3 vezes, Subir tudo, Descer tudo, Topo, Meio, Final\n\n"+
                "🔙 Voltar à tabela\n❌ Encerrar / Sair";
        TextView tv=new TextView(this);
        tv.setText(help);
        tv.setPadding(40,30,40,30);
        tv.setTextSize(15f);
        ScrollView sv=new ScrollView(this);
        sv.addView(tv);
        dialogAjuda=new AlertDialog.Builder(this).setTitle("Comandos de voz").setView(sv)
                .setPositiveButton("Fechar",(d,w)->dialogAjuda.dismiss()).create();
        dialogAjuda.show();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        try{unregisterReceiver(receiverComando);}catch(Exception ignored){}
        try{unregisterReceiver(receiverFechar);}catch(Exception ignored){}
    }
}