package com.sinnara.vouchers;

import android.graphics.Bitmap;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;

/**
 * Builds ESC/POS print jobs and sends them to a network thermal printer (raw TCP, usually port 9100).
 * Works with Xprinter, Rongta, Gprinter, Epson TM and most other ESC/POS receipt printers.
 */
final class EscPos {
    private EscPos() {}

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int WRITE_TIMEOUT_MS = 15000;
    /** Rows per raster block. Small blocks keep cheap printers from overflowing their buffer. */
    private static final int BAND_ROWS = 96;

    private static void put(ByteArrayOutputStream o, int... bytes) {
        for (int b : bytes) o.write(b & 0xFF);
    }

    /** Converts a receipt image into a printable job: init, raster image, feed, optional cut. */
    static byte[] imageJob(Bitmap src, int dots, boolean cut) {
        int width = dots > 0 ? dots : 576;
        width = (width / 8) * 8;
        Bitmap bmp = src;
        if (src.getWidth() != width) {
            int h = Math.max(1, Math.round(src.getHeight() * (width / (float) src.getWidth())));
            bmp = Bitmap.createScaledBitmap(src, width, h, true);
        }
        int height = bmp.getHeight();
        int[] px = new int[width * height];
        bmp.getPixels(px, 0, width, 0, 0, width, height);
        return rasterJob(px, width, height, cut);
    }

    /** Pure conversion of ARGB pixels (row by row) to an ESC/POS raster job. width must be a multiple of 8. */
    static byte[] rasterJob(int[] px, int width, int height, boolean cut) {
        int widthBytes = width / 8;
        ByteArrayOutputStream o = new ByteArrayOutputStream(widthBytes * height + 1024);
        put(o, 0x1B, 0x40);                 // ESC @  initialise
        put(o, 0x1B, 0x61, 0x01);           // ESC a 1 centre
        for (int y0 = 0; y0 < height; y0 += BAND_ROWS) {
            int rows = Math.min(BAND_ROWS, height - y0);
            // GS v 0 m xL xH yL yH  – raster bit image
            put(o, 0x1D, 0x76, 0x30, 0x00, widthBytes & 0xFF, (widthBytes >> 8) & 0xFF, rows & 0xFF, (rows >> 8) & 0xFF);
            for (int y = y0; y < y0 + rows; y++) {
                int row = y * width;
                for (int xb = 0; xb < widthBytes; xb++) {
                    int v = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int p = px[row + xb * 8 + bit];
                        int a = (p >>> 24) & 0xFF, r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                        int lum = (r * 299 + g * 587 + b * 114) / 1000;
                        if (a > 127 && lum < 128) v |= (0x80 >> bit);
                    }
                    o.write(v);
                }
            }
        }
        put(o, 0x1B, 0x61, 0x00);           // left align again
        put(o, 0x1B, 0x64, 0x04);           // ESC d 4  feed 4 lines
        if (cut) put(o, 0x1D, 0x56, 0x42, 0x00); // GS V 66 0  feed and partial cut
        return o.toByteArray();
    }

    /** A short text-only test page. */
    static byte[] testJob(String title, String ip, int port, boolean cut) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        Charset ascii = Charset.forName("US-ASCII");
        put(o, 0x1B, 0x40, 0x1B, 0x61, 0x01);
        put(o, 0x1D, 0x21, 0x11);           // double size
        byte[] head = "TEST PRINT\n".getBytes(ascii);
        o.write(head, 0, head.length);
        put(o, 0x1D, 0x21, 0x00);
        String body = "\n" + asciiOnly(title) + "\n" + ip + ":" + port + "\n\nSinnara Vouchers app\nPrinter connected OK\n\n";
        byte[] b = body.getBytes(ascii);
        o.write(b, 0, b.length);
        put(o, 0x1B, 0x64, 0x04);
        if (cut) put(o, 0x1D, 0x56, 0x42, 0x00);
        return o.toByteArray();
    }

    private static String asciiOnly(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) sb.append(c >= 32 && c < 127 ? c : ' ');
        return sb.toString().trim();
    }

    /** Opens a TCP connection to the printer and writes the job the requested number of times. */
    static void send(String ip, int port, byte[] job, int copies) throws IOException {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
            s.setSoTimeout(WRITE_TIMEOUT_MS);
            OutputStream os = new BufferedOutputStream(s.getOutputStream(), 8192);
            for (int i = 0; i < copies; i++) os.write(job);
            os.flush();
            try { Thread.sleep(400); } catch (InterruptedException ignored) { }
        } finally {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    /** Only checks that the printer accepts a connection. */
    static void check(String ip, int port) throws IOException {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
        } finally {
            try { s.close(); } catch (IOException ignored) { }
        }
    }
}
