package com.sinnara.vouchers;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Sinnara Vouchers – opens the voucher web app and lets it print straight to the branch's network printer.
 */
public class MainActivity extends Activity {

    /** The live voucher website. Change this one line if the site moves to another address. */
    static final String APP_URL = "https://sinnara-vouchers.pages.dev/";
    static final String APP_VERSION = "1.0";

    private WebView web;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#1d5f73"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#edf1f1"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setUserAgentString(s.getUserAgentString() + " SinnaraVouchersApp/" + APP_VERSION);

        web.addJavascriptInterface(new PrinterBridge(this, web), "SinnaraAndroid");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String host = u.getHost() == null ? "" : u.getHost();
                String appHost = Uri.parse(APP_URL).getHost();
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
                if (request.isForMainFrame()) showOffline();
            }
        });

        if (state != null) web.restoreState(state);
        else web.loadUrl(APP_URL);
    }

    private void showOffline() {
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='font-family:sans-serif;background:#edf1f1;color:#132026;display:flex;align-items:center;justify-content:center;height:100vh;margin:0'>"
                + "<div style='text-align:center;padding:24px'><h2>No internet connection</h2>"
                + "<p>Connect to Wi-Fi or mobile data, then try again.</p>"
                + "<p dir='rtl'>لا يوجد اتصال بالإنترنت</p>"
                + "<button style='font-size:17px;padding:12px 28px;border:0;border-radius:8px;background:#1d5f73;color:#fff' "
                + "onclick=\"location.href='" + APP_URL + "'\">Try again</button></div></body></html>";
        web.loadDataWithBaseURL(APP_URL, html, "text/html", "utf-8", APP_URL);
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
