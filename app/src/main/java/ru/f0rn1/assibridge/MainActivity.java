package ru.f0rn1.assibridge;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MainActivity extends Activity {

    private static final String PREFS = "assi";
    private static final String KEY_SERVER = "server";
    private static final String KEY_MAC = "wol_mac";
    private static final String KEY_BCAST = "wol_bcast";
    private static final int REQ_FILE = 1001;
    private static final int REQ_MIC = 1002;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private LinearLayout root;
    private View setupView;
    private View offlineView;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#080809"));
        setContentView(root);

        askMic();
        buildWebView();

        String server = prefs().getString(KEY_SERVER, "");
        if (server == null || server.isEmpty()) showSetup();
        else load(server);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /* ---------- экран настройки адреса ---------- */

    private void showSetup() {
        if (setupView != null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(56, 0, 56, 0);
        box.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Assi");
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        box.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Адрес твоего сервера");
        hint.setTextColor(Color.parseColor("#8b93a1"));
        hint.setTextSize(14);
        hint.setPadding(0, 8, 0, 24);
        box.addView(hint);

        final EditText input = new EditText(this);
        input.setHint("https://assi.assi.pro");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#5a6270"));
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        box.addView(input);

        Button go = new Button(this);
        go.setText("Подключиться");
        go.setAllCaps(false);
        go.setOnClickListener(v -> {
            String url = input.getText().toString().trim();
            if (url.isEmpty()) { toast("Введи адрес"); return; }
            if (!url.startsWith("http")) url = "https://" + url;
            if (!url.endsWith("/")) url = url + "/";
            prefs().edit().putString(KEY_SERVER, url).apply();
            hideSetup();
            load(url);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 24;
        box.addView(go, lp);

        setupView = box;
        root.addView(setupView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void hideSetup() {
        if (setupView != null) {
            root.removeView(setupView);
            setupView = null;
        }
    }

    /* ---------- экран «ноут не отвечает» ---------- */

    private boolean hasWol() {
        String mac = prefs().getString(KEY_MAC, "");
        return mac != null && !mac.isEmpty();
    }

    private void showOffline() {
        if (offlineView != null) {
            offlineView.setVisibility(View.VISIBLE);
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor("#080809"));
        box.setPadding(56, 0, 56, 0);

        TextView title = new TextView(this);
        title.setText("Assi");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Сервер не отвечает");
        hint.setTextColor(Color.parseColor("#8b93a1"));
        hint.setTextSize(14);
        hint.setPadding(0, 10, 0, 28);
        hint.setGravity(Gravity.CENTER);
        box.addView(hint);

        Button wakeBtn = new Button(this);
        wakeBtn.setText("Разбудить ноут");
        wakeBtn.setAllCaps(false);
        wakeBtn.setOnClickListener(v -> {
            if (!hasWol()) { toast("MAC неизвестен — подключись к серверу один раз"); return; }
            boolean ok = wakeWith(prefs().getString(KEY_MAC, ""), prefs().getString(KEY_BCAST, ""));
            toast(ok ? "Сигнал пробуждения отправлен" : "Не смог отправить");
        });
        box.addView(wakeBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button retry = new Button(this);
        retry.setText("Повторить");
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> {
            hideOffline();
            String s = prefs().getString(KEY_SERVER, "");
            if (s != null && !s.isEmpty()) load(s);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 12;
        box.addView(retry, lp);

        offlineView = box;
        root.addView(offlineView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void hideOffline() {
        if (offlineView != null) offlineView.setVisibility(View.GONE);
    }

    /* ---------- webview ---------- */

    @SuppressLint("SetJavaScriptEnabled")
    private void buildWebView() {
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));
        WebView.setWebContentsDebuggingEnabled(true);

        web.addJavascriptInterface(new Bridge(), "AssiNative");

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(Intent.createChooser(intent, "Выбрать файл"), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    toast("Не нашёл, чем выбрать файл");
                    return false;
                }
                return true;
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    String host = u.getHost() == null ? "" : u.getHost();
                    String cur = web.getUrl() == null ? "" : Uri.parse(web.getUrl()).getHost();
                    if (cur != null && host.equalsIgnoreCase(cur)) return false;
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                    return true;
                }
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!"about:blank".equals(url)) hideOffline();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, android.webkit.WebResourceError err) {
                if (req != null && req.isForMainFrame()) {
                    showOffline();
                }
            }
        });

        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void load(String url) {
        if (web != null) web.loadUrl(url);
    }

    private void askMic() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE) {
            Uri[] out = null;
            if (res == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    out = new Uri[n];
                    for (int i = 0; i < n; i++) out[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    out = new Uri[]{data.getData()};
                }
            }
            if (fileCallback != null) {
                fileCallback.onReceiveValue(out);
                fileCallback = null;
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** mac вида "30-E3-A4-D4-DD-2A", broadcasts — адреса через запятую */
    private boolean wakeWith(String mac, String broadcasts) {
        try {
            String[] parts = mac.replace(":", "-").split("-");
            if (parts.length < 6) return false;
            byte[] macBytes = new byte[6];
            for (int i = 0; i < 6; i++) macBytes[i] = (byte) Integer.parseInt(parts[i].trim(), 16);

            byte[] packet = new byte[6 + 16 * 6];
            for (int i = 0; i < 6; i++) packet[i] = (byte) 0xFF;
            for (int r = 0; r < 16; r++) {
                System.arraycopy(macBytes, 0, packet, 6 + r * 6, 6);
            }

            String[] targets = (broadcasts == null || broadcasts.isEmpty())
                    ? new String[]{"255.255.255.255"}
                    : broadcasts.split(",");

            DatagramSocket sock = new DatagramSocket();
            sock.setBroadcast(true);
            for (String t : targets) {
                String addr = t.trim();
                if (addr.isEmpty()) continue;
                try {
                    DatagramPacket p = new DatagramPacket(packet, packet.length,
                            InetAddress.getByName(addr), 9);
                    sock.send(p);
                } catch (Exception ignored) { }
            }
            sock.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /* ---------- мост для страницы ---------- */

    private class Bridge {

        @JavascriptInterface
        public boolean hasWol() {
            return true;
        }

        /** страница сообщает MAC и broadcast-адреса, чтобы «Разбудить» работало и когда сервер спит */
        @JavascriptInterface
        public void setWol(String mac, String broadcasts) {
            prefs().edit()
                    .putString(KEY_MAC, mac == null ? "" : mac.trim())
                    .putString(KEY_BCAST, broadcasts == null ? "" : broadcasts.trim())
                    .apply();
        }

        @JavascriptInterface
        public boolean wake(String mac, String broadcasts) {
            String m = (mac == null || mac.isEmpty()) ? prefs().getString(KEY_MAC, "") : mac;
            String b = (broadcasts == null || broadcasts.isEmpty()) ? prefs().getString(KEY_BCAST, "") : broadcasts;
            return wakeWith(m, b);
        }

        /** сменить сервер: чистит сохранённый адрес и показывает экран настройки */
        @JavascriptInterface
        public void resetServer() {
            runOnUiThread(() -> {
                prefs().edit().remove(KEY_SERVER).apply();
                if (web != null) web.loadUrl("about:blank");
                showSetup();
            });
        }

        @JavascriptInterface
        public String server() {
            return prefs().getString(KEY_SERVER, "");
        }
    }
}
