package com.sinnara.vouchers;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The bridge the web page calls as window.SinnaraAndroid.
 * Every long task runs on a background thread and reports back through window.__sinnaraCb(id, ok, message).
 */
final class PrinterBridge {
    private final Activity activity;
    private final WebView web;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    PrinterBridge(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
    }

    @JavascriptInterface
    public boolean isAvailable() { return true; }

    @JavascriptInterface
    public String appVersion() { return MainActivity.APP_VERSION; }

    /** Prints a receipt image (base64 PNG) on the network printer. dots: 576 for 80 mm paper, 384 for 58 mm. */
    @JavascriptInterface
    public void printImage(final String cb, final String ip, final int port, final String pngBase64,
                           final int dots, final int copies, final boolean cut) {
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    byte[] png = Base64.decode(stripPrefix(pngBase64), Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(png, 0, png.length);
                    if (bmp == null) throw new IOException("image could not be read");
                    byte[] job = EscPos.imageJob(bmp, dots, cut);
                    EscPos.send(ip, port, job, Math.max(1, Math.min(copies, 3)));
                    reply(cb, true, "");
                } catch (Throwable e) {
                    reply(cb, false, reason(e));
                }
            }
        });
    }

    @JavascriptInterface
    public void testPrint(final String cb, final String ip, final int port, final String title, final int dots) {
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    EscPos.send(ip, port, EscPos.testJob(title, ip, port, true), 1);
                    reply(cb, true, "");
                } catch (Throwable e) {
                    reply(cb, false, reason(e));
                }
            }
        });
    }

    @JavascriptInterface
    public void checkPrinter(final String cb, final String ip, final int port) {
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    EscPos.check(ip, port);
                    reply(cb, true, "");
                } catch (Throwable e) {
                    reply(cb, false, reason(e));
                }
            }
        });
    }

    /** Saves a file (for example a voucher PDF) into the phone's Downloads folder. */
    @JavascriptInterface
    public void saveFile(final String cb, final String base64, final String name, final String mime) {
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    byte[] data = Base64.decode(stripPrefix(base64), Base64.DEFAULT);
                    String safeName = (name == null || name.trim().isEmpty()) ? "voucher.pdf" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
                    if (Build.VERSION.SDK_INT >= 29) {
                        ContentResolver cr = activity.getContentResolver();
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
                        cv.put(MediaStore.MediaColumns.MIME_TYPE, mime == null ? "application/octet-stream" : mime);
                        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                        if (uri == null) throw new IOException("could not create file");
                        OutputStream os = cr.openOutputStream(uri);
                        if (os == null) throw new IOException("could not open file");
                        try { os.write(data); } finally { os.close(); }
                    } else {
                        File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                        if (dir == null) dir = activity.getFilesDir();
                        if (!dir.exists() && !dir.mkdirs()) throw new IOException("could not create folder");
                        FileOutputStream fos = new FileOutputStream(new File(dir, safeName));
                        try { fos.write(data); } finally { fos.close(); }
                    }
                    reply(cb, true, safeName);
                } catch (Throwable e) {
                    reply(cb, false, reason(e));
                }
            }
        });
    }

    private static String stripPrefix(String b64) {
        if (b64 == null) return "";
        int comma = b64.indexOf(',');
        return (b64.startsWith("data:") && comma > 0) ? b64.substring(comma + 1) : b64;
    }

    private static String reason(Throwable e) {
        if (e instanceof SocketTimeoutException) return "timeout";
        if (e instanceof ConnectException) return "connection refused";
        if (e instanceof NoRouteToHostException) return "no route to printer";
        if (e instanceof UnknownHostException) return "unknown address";
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }

    private void reply(final String cb, final boolean ok, final String msg) {
        final String js = "window.__sinnaraCb&&window.__sinnaraCb(" + JSONObject.quote(cb) + "," + ok + "," + JSONObject.quote(msg == null ? "" : msg) + ")";
        activity.runOnUiThread(new Runnable() {
            @Override public void run() { web.evaluateJavascript(js, null); }
        });
    }
}
