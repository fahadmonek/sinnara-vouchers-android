package com.sinnara.vouchers;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

/**
 * Sinnara Vouchers – opens the voucher web app and lets it print straight to the branch's network printer.
 * The website address can be changed on the phone from the connection error screen.
 */
public class MainActivity extends Activity {

    /** Default website address. Can be changed on the phone; the saved address is used after that. */
    static final String DEFAULT_URL = "https://sinnara-vouchers.pages.dev/";
    static final String APP_VERSION = "1.0.2";
    private static final String PREFS = "sinnara";
    private static final String KEY_URL = "site_url";

    private WebView web;
    private String siteUrl;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#1d5f73"));
        siteUrl = prefs().getString(KEY_URL, DEFAULT_URL);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#edf1f1"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setUserAgentString(s.getUserAgentString() + " SinnaraVouchersApp/" + APP_VERSION);

        web.addJavascriptInterface(new PrinterBridge(this, web), "SinnaraAndroid");
        web.addJavascriptInterface(new AppBridge(), "SinnaraApp");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String host = u.getHost() == null ? "" : u.getHost();
                String appHost = Uri.parse(siteUrl).getHost();
                boolean ours = host.equals(appHost)
                        || host.endsWith(".firebaseapp.com") || host.endsWith(".googleapis.com")
                        || host.endsWith(".gstatic.com") || host.endsWith(".google.com");
                if (ours && ("https".equals(u.getScheme()) || "http".equals(u.getScheme()))) return false;
                // Anything else (other websites, tel:, mailto:, whatsapp:) opens outside the app
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame()) return;
                String desc = "";
                if (Build.VERSION.SDK_INT >= 23) desc = error.getErrorCode() + " " + error.getDescription();
                showError(desc, request.getUrl() == null ? siteUrl : request.getUrl().toString());
            }
        });

        if (state != null) web.restoreState(state);
        else web.loadUrl(siteUrl);
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }

    /** Error screen: shows the real error, a retry button, and a box to change the website address. */
    private void showError(String desc, String failingUrl) {
        String q = JSONObject.quote(siteUrl);
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='font-family:sans-serif;background:#edf1f1;color:#132026;margin:0;padding:24px'>"
                + "<div style='max-width:460px;margin:40px auto;background:#fff;border-radius:12px;padding:22px'>"
                + "<h2 style='margin-top:0'>Could not open the voucher website</h2>"
                + "<p dir='rtl'>تعذر فتح موقع السندات</p>"
                + "<p style='font-size:14px;color:#5a6a70'>Address: <b dir='ltr'>" + TextUtils.htmlEncode(failingUrl) + "</b><br>"
                + "Error: <b>" + TextUtils.htmlEncode(desc == null ? "" : desc) + "</b></p>"
                + "<p style='font-size:14px'>Check that the phone has internet. If this address is wrong, type the address you open in Chrome:</p>"
                + "<input id='u' dir='ltr' style='width:100%;box-sizing:border-box;font-size:16px;padding:12px;border:1px solid #cdd7d9;border-radius:8px'>"
                + "<div style='display:flex;gap:10px;margin-top:14px'>"
                + "<button style='flex:1;font-size:16px;padding:12px;border:0;border-radius:8px;background:#1d5f73;color:#fff' "
                + "onclick=\"SinnaraApp.setSiteUrl(document.getElementById('u').value)\">Save address</button>"
                + "<button style='flex:1;font-size:16px;padding:12px;border:1px solid #1d5f73;border-radius:8px;background:#fff;color:#1d5f73' "
                + "onclick=\"SinnaraApp.retry()\">Try again</button></div>"
                + "<p style='font-size:12px;color:#5a6a70;margin-bottom:0'>App version " + APP_VERSION + "</p></div>"
                + "<script>document.getElementById('u').value=" + q + ";</script></body></html>";
        web.loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null);
    }

    /** Small bridge used by the error screen. */
    private final class AppBridge {
        @JavascriptInterface
        public void retry() {
            runOnUiThread(new Runnable() { @Override public void run() { web.loadUrl(siteUrl); } });
        }

        @JavascriptInterface
        public void setSiteUrl(String url) {
            if (url == null) return;
            String u = url.trim();
            if (u.isEmpty()) return;
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
            if (!u.endsWith("/") && Uri.parse(u).getPath() != null && Uri.parse(u).getPath().isEmpty()) u = u + "/";
            final String finalUrl = u;
            prefs().edit().putString(KEY_URL, finalUrl).apply();
            runOnUiThread(new Runnable() { @Override public void run() { siteUrl = finalUrl; web.loadUrl(siteUrl); } });
        }

        @JavascriptInterface
        public String getSiteUrl() { return siteUrl; }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.setVisibility(View.GONE);
            web.destroy();
        }
        super.onDestroy();
    }
}
