package com.example.vigilancia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
    private BroadcastReceiver receiverFechar;
    private String ultimoCampo = "";
    private String ultimoValor = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        Toolbar tb = findViewById(R.id.toolbar);
        tb.setTitle("Voltar à Tabela");
        tb.setTitleTextColor(getResources().getColor(android.R.color.white));
        tb.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        tb.setNavigationOnClickListener(v -> finish());

        webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){
                v.loadUrl(r.getUrl().toString());
                return true;
            }
        });

        String custom = getIntent().getStringExtra("url_custom");
        webView.loadUrl(custom!=null&&!custom.isEmpty()?custom:SITE_URL);

        receiverComando = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String cmd = i.getStringExtra("texto");
                if (cmd != null) processarComando(cmd.toLowerCase(Locale.ROOT).trim());
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

    // ======================================================
    // COMANDOS DE VOZ
    // ======================================================
    private void processarComando(String comando){
        try{
            if(comando.startsWith("apagar ")){apagarCampo(comando.replaceFirst("apagar\\s+","").trim());return;}
            if(comando.contains("encerrar")||comando.contains("fechar aplicativo")||comando.contains("sair")){encerrarAplicativo();return;}
            if(comando.startsWith("erro")){corrigirUltimoCampo(comando.replaceFirst("erro,?","").trim());return;}

            String raw=comando.trim();
            raw=corrigirSoletradoPreciso(raw);

            if(raw.contains("nome")){
                String val=capitalizarInteligente(extrairValorFlex(raw,"nome"));
                preencherCampo("Nome Completo",val);
                ultimoCampo="Nome Completo";ultimoValor=val;return;
            }
            if(raw.contains("cpf")){
                String val=formatarCPF(extrairValorFlex(raw,"cpf").replaceAll("\\D+",""));
                preencherCampo("CPF",val);ultimoCampo="CPF";ultimoValor=val;return;
            }
            if(raw.contains("telefone")){
                String val=formatarTelefone(extrairValorFlex(raw,"telefone").replaceAll("\\D+",""));
                preencherCampo("Telefone",val);ultimoCampo="Telefone";ultimoValor=val;return;
            }
            if(raw.contains("email")||raw.contains("e-mail")||raw.contains("e mail")){
                String val=formatarEmail(extrairValorFlex(raw,"email").replaceAll("(?i)e[-\\s]?mail",""));
                preencherCampo("Email",val);ultimoCampo="Email";ultimoValor=val;return;
            }
            if(raw.contains("endereco")||raw.contains("endereço")){
                String val=capitalizarInteligente(extrairValorFlex(raw,"endereco").replaceAll("end(ere|é|e)ço",""));
                preencherCampo("Endereço completo",val);ultimoCampo="Endereço completo";ultimoValor=val;return;
            }
            if(raw.contains("informações")||raw.contains("informacoes")){
                String val=capitalizarInteligente(extrairValorFlex(raw,"informações").replaceAll("(informações|informacoes)( do equipamento)?",""));
                preencherCampo("Informações do equipamento",val);ultimoCampo="Informações do equipamento";ultimoValor=val;return;
            }

            if(raw.contains("limpar formulario")||raw.contains("limpar formulário")){limparFormulario();return;}

            if(raw.contains("descer tudo")){scroll("bottom");return;}
            if(raw.contains("subir tudo")){scroll("top");return;}
            if(raw.contains("meio")||raw.contains("metade")){scroll("mid");return;}
            if(raw.contains("desc")){scroll("down",1);return;}
            if(raw.contains("sub")){scroll("up",1);}
        }catch(Exception e){Toast.makeText(this,"Erro: "+e.getMessage(),Toast.LENGTH_SHORT).show();}
    }

    private String extrairValorFlex(String frase,String campo){
        frase=frase.trim();
        if(frase.startsWith(campo)) return frase.replaceFirst(campo,"").trim();
        else if(frase.endsWith(campo)) return frase.replaceFirst(campo+"$","").trim();
        else return frase.replace(campo,"").trim();
    }

    // ======================================================
    // CORREÇÃO POR ERRO
    // ======================================================
    private void corrigirUltimoCampo(String comando){
        if(ultimoCampo.isEmpty()||ultimoValor.isEmpty()){
            Toast.makeText(this,"Nenhum campo para corrigir.",Toast.LENGTH_SHORT).show();return;
        }

        String textoAtual=ultimoValor;
        // ocorrência
        int ocorrencia=1;
        if(comando.contains("segundo")||comando.contains("2º"))ocorrencia=2;
        if(comando.contains("terceiro")||comando.contains("3º"))ocorrencia=3;
        comando=comando.replaceAll("(primeiro|segundo|terceiro|\\dº)","").trim();

        String[] partes=comando.split("\\s+",2);
        if(partes.length==0)return;
        String alvo=partes[0];
        String instrucao=partes.length>1?partes[1]:"";
        String corrigido=corrigirSoletradoPreciso(alvo+" "+instrucao);

        String alvoSimilar=procurarMaisParecida(alvo,textoAtual);
        if(alvoSimilar==null){Toast.makeText(this,"Palavra não encontrada.",Toast.LENGTH_SHORT).show();return;}

        int idx=-1,cont=0;
        for(int i=0;i<textoAtual.length();){
            idx=textoAtual.toLowerCase().indexOf(alvoSimilar.toLowerCase(),i);
            if(idx==-1)break;cont++;if(cont==ocorrencia)break;i=idx+alvoSimilar.length();
        }
        if(idx!=-1){
            String antes=textoAtual.substring(0,idx);
            String depois=textoAtual.substring(idx+alvoSimilar.length());
            textoAtual=antes+corrigido+depois;
            preencherCampo(ultimoCampo,capitalizarInteligente(textoAtual));
            ultimoValor=textoAtual;
            Toast.makeText(this,"✅ Corrigido "+ultimoCampo,Toast.LENGTH_SHORT).show();
        }else Toast.makeText(this,"Palavra não encontrada.",Toast.LENGTH_SHORT).show();
    }

    // busca semelhante por distância de Levenshtein (≤2)
    private String procurarMaisParecida(String alvo,String texto){
        alvo=alvo.toLowerCase(Locale.ROOT);
        String[] palavras=texto.split("\\s+");
        String melhor=null;int melhorDist=3;
        for(String p:palavras){
            int d=distanciaLevenshtein(alvo,p.toLowerCase(Locale.ROOT));
            if(d<melhorDist){melhor=p;melhorDist=d;}
        }
        return melhor;
    }

    private int distanciaLevenshtein(String a,String b){
        int m=a.length(),n=b.length();
        int[][]dp=new int[m+1][n+1];
        for(int i=0;i<=m;i++)dp[i][0]=i;
        for(int j=0;j<=n;j++)dp[0][j]=j;
        for(int i=1;i<=m;i++)
            for(int j=1;j<=n;j++){
                int cost=(a.charAt(i-1)==b.charAt(j-1))?0:1;
                dp[i][j]=Math.min(Math.min(dp[i-1][j]+1,dp[i][j-1]+1),dp[i-1][j-1]+cost);
            }
        return dp[m][n];
    }

    // ======================================================
    // REGRAS "COM / SEM ..."
    // ======================================================
    private String corrigirSoletradoPreciso(String t){
        t=t.toLowerCase(Locale.ROOT).trim();

        // COM
        if(t.matches(".*\\scom\\sth.*"))
            t=t.replaceAll("tiago","thiago").replaceAll("\\scom\\sth.*","");
        if(t.matches(".*\\scom\\sj.*"))
            t=t.replaceAll("ge","je").replaceAll("gi","ji").replaceAll("\\scom\\sj.*","");
        if(t.matches(".*\\scom\\sh\\sno\\sfinal.*"))
            t=t.replaceAll("(\\b\\w+)(\\b)(\\scom\\sh\\sno\\sfinal)","$1h");
        if(t.matches(".*\\scom\\sd.+"))
            t=t.replaceAll(" com d","d");

        // SEM
        if(t.matches(".*\\ssem\\sth.*"))
            t=t.replaceAll("thiago","tiago").replaceAll("\\ssem\\sth.*","");
        if(t.matches(".*\\ssem\\sj.*"))
            t=t.replaceAll("j","g").replaceAll("\\ssem\\sj.*","");
        if(t.matches(".*\\ssem\\sh\\sno\\sfinal.*"))
            t=t.replaceAll("h\\b","").replaceAll("\\ssem\\sh\\sno\\sfinal.*","");

        return t.trim();
    }

    // ======================================================
    // FORMATADORES / UTILITÁRIOS
    // ======================================================
    private String capitalizarInteligente(String t){
        t=t.trim().replaceAll("\\s+"," ");
        String[] min={"de","da","das","do","dos"};
        StringBuilder sb=new StringBuilder();
        for(String p:t.split(" ")){
            String pl=p.toLowerCase(Locale.ROOT);
            boolean minus=false;for(String m:min) if(pl.equals(m)) minus=true;
            if(minus) sb.append(pl).append(" ");
            else if(p.length()>1) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase()).append(" ");
            else sb.append(p.toUpperCase()).append(" ");
        }
        return sb.toString().trim();
    }

    private String formatarCPF(String c){c=c.replaceAll("\\D","");if(c.length()==11)return c.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})","$1.$2.$3-$4");return c;}
    private String formatarTelefone(String t){t=t.replaceAll("\\D","");if(t.length()==11)return t.replaceFirst("(\\d{2})(\\d{5})(\\d{4})","($1) $2-$3");if(t.length()==10)return t.replaceFirst("(\\d{2})(\\d{4})(\\d{4})","($1) $2-$3");return t;}
    private String formatarEmail(String e){e=e.toLowerCase(Locale.ROOT).replaceAll("\\s+","");e=e.replace("arroba","@").replace("ponto",".").replace("dot",".").replace("gmailcom","gmail.com").replace("hotmailcom","hotmail.com").replace("outlookcom","outlook.com").replace("@@","@").replace("..",".");return e;}
    private void scroll(String d){scroll(d,1);}
    private void scroll(String d,int v){String js="";if(d.equals("down"))js="window.scrollBy({top:window.innerHeight*"+v+",behavior:'smooth'});";else if(d.equals("up"))js="window.scrollBy({top:-window.innerHeight*"+v+",behavior:'smooth'});";else if(d.equals("mid"))js="window.scrollTo({top:document.body.scrollHeight/2,behavior:'smooth'});";else if(d.equals("bottom"))js="window.scrollTo({top:document.body.scrollHeight,behavior:'smooth'});";else if(d.equals("top"))js="window.scrollTo({top:0,behavior:'smooth'});";webView.evaluateJavascript("javascript:(function(){"+js+"})();",null);}

    // ======================================================
    // DOM
    // ======================================================
    private void preencherCampo(String label,String valor){
        String js="javascript:(function(){let labels=document.querySelectorAll('label');for(let i=0;i<labels.length;i++){if(labels[i].innerText.toLowerCase().includes('"+label.toLowerCase()+"')){let input=labels[i].nextElementSibling;if(input&&(input.tagName==='INPUT'||input.tagName==='TEXTAREA')){input.focus();input.value='"+valor.trim()+"';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));break;}}}})();";
        webView.evaluateJavascript(js,null);
    }

    private void apagarCampo(String campo){
        String label=campo.toLowerCase();
        switch(label){
            case"nome":label="Nome Completo";break;case"cpf":label="CPF";break;case"telefone":label="Telefone";break;
            case"email":case"e-mail":case"e mail":label="Email";break;
            case"endereco":case"endereço":label="Endereço completo";break;
            case"informacoes":case"informações":label="Informações do equipamento";break;
        }
        String js="javascript:(function(){let labels=document.querySelectorAll('label');for(let i=0;i<labels.length;i++){if(labels[i].innerText.toLowerCase().includes('"+label.toLowerCase()+"')){let input=labels[i].nextElementSibling;if(input){input.value='';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));break;}}}})();";
        webView.evaluateJavascript(js,null);
        Toast.makeText(this,"🧹 Campo "+label+" apagado",Toast.LENGTH_SHORT).show();
    }

    private void limparFormulario(){
        String js="javascript:(function(){let campos=document.querySelectorAll('input,textarea');for(let i=0;i<campos.length;i++){campos[i].value='';campos[i].dispatchEvent(new Event('input',{bubbles:true}));campos[i].dispatchEvent(new Event('change',{bubbles:true}));}})();";
        webView.evaluateJavascript(js,null);
        Toast.makeText(this,"🧹 Formulário limpo",Toast.LENGTH_SHORT).show();
    }

    private void encerrarAplicativo(){
        try{stopService(new Intent(this,VoiceService.class));}catch(Exception ignored){}
        Toast.makeText(this,"Encerrando aplicativo…",Toast.LENGTH_SHORT).show();
        finishAffinity();System.exit(0);
    }

    @Override protected void onDestroy(){
        super.onDestroy();
        try{unregisterReceiver(receiverComando);}catch(Exception ignored){}
        try{unregisterReceiver(receiverFechar);}catch(Exception ignored){}
    }
}