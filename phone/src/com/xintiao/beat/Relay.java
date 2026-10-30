package com.xintiao.beat;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** WebSocket link to the relay server with automatic reconnect (exponential backoff, max 60 s). */
final class Relay extends WebSocketListener {
    private final Hub hub;
    private final OkHttpClient client = new OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build();
    private WebSocket ws;
    private boolean open;
    private boolean wanted;
    private String url = "";
    private int attempt;
    private int generation;
    private final Runnable reconnect = new Runnable() {
        @Override public void run() { connectNow(); }
    };

    Relay(Hub hub) { this.hub = hub; }

    void start(String url) {
        if (wanted && url.equals(this.url) && ws != null) return;
        this.url = url;
        wanted = true;
        attempt = 0;
        connectNow();
    }

    void stop() {
        wanted = false;
        hub.main.removeCallbacks(reconnect);
        if (ws != null) ws.close(1000, "bye");
        ws = null;
        open = false;
    }

    void reconnectNow() {
        if (ws != null) ws.cancel();
        ws = null;
        open = false;
        attempt = 0;
        connectNow();
    }

    boolean isOpen() { return open; }

    boolean send(JSONObject o) {
        return open && ws != null && ws.send(o.toString());
    }

    private void connectNow() {
        hub.main.removeCallbacks(reconnect);
        if (!wanted || url.isEmpty()) return;
        if (ws != null) ws.cancel();
        open = false;
        final int gen = ++generation;
        Request req;
        try {
            req = new Request.Builder().url(url).build();
        } catch (IllegalArgumentException e) {
            hub.toast("服务器地址格式不对");
            return;
        }
        ws = client.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket w, Response r) {
                hub.main.post(() -> { if (gen == generation) { open = true; attempt = 0; hub.onRelayOpen(); } });
            }
            @Override public void onMessage(WebSocket w, String text) {
                hub.main.post(() -> {
                    if (gen != generation) return;
                    try { hub.onRelayMessage(new JSONObject(text)); } catch (Exception ignored) { }
                });
            }
            @Override public void onClosing(WebSocket w, int code, String reason) { w.close(1000, null); }
            @Override public void onClosed(WebSocket w, int code, String reason) {
                hub.main.post(() -> lost(gen));
            }
            @Override public void onFailure(WebSocket w, Throwable t, Response r) {
                hub.main.post(() -> lost(gen));
            }
        });
    }

    private void lost(int gen) {
        if (gen != generation) return;
        open = false;
        ws = null;
        hub.onRelayClosed();
        if (!wanted) return;
        long delay = Math.min(60000L, 1000L << Math.min(attempt, 6));
        attempt++;
        hub.main.postDelayed(reconnect, delay);
    }
}
