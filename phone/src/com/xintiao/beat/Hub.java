package com.xintiao.beat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * App-wide state + glue: relay server  <->  this phone  <->  band/watch.
 * Everything runs on the main thread (Relay and WearBridge post back here).
 */
public final class Hub {
    public interface Listener {
        void onChange();
        void onToast(String msg);
    }

    private static Hub instance;

    public static synchronized Hub get(Context c) {
        if (instance == null) instance = new Hub(c.getApplicationContext());
        return instance;
    }

    final Context ctx;
    final SharedPreferences sp;
    final Handler main = new Handler(Looper.getMainLooper());
    final Relay relay;
    final WearBridge wear;
    final Notifier notifier;
    private final List<Listener> listeners = new ArrayList<>();
    private static final String OUTBOX_KEY = "outbox";
    private final List<JSONObject> outbox = new ArrayList<>();

    // live (not persisted) state
    boolean serverOnline;
    String code = "";
    long codeExpiresAt;
    private JSONObject pendingForBand;
    private long pendingAt;
    private boolean bandWantsCode;

    private Hub(Context c) {
        ctx = c;
        sp = c.getSharedPreferences("beat", Context.MODE_PRIVATE);
        notifier = new Notifier(c);
        relay = new Relay(this);
        wear = new WearBridge(this);
        loadOutbox();
    }

    // ------------------------------------------------------------------ lifecycle
    private boolean started;

    public void start() {
        if (!started) {
            started = true;
            wear.init();
        }
        connect();
    }

    public void connect() {
        String url = serverUrl();
        if (url.isEmpty()) return;
        relay.start(url);
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    void changed() {
        for (Listener l : new ArrayList<>(listeners)) l.onChange();
    }

    void toast(String m) {
        for (Listener l : new ArrayList<>(listeners)) l.onToast(m);
    }

    // ------------------------------------------------------------------ prefs
    public String serverUrl() { return sp.getString("server_url", ctx.getString(R.string.default_server)); }

    public void setServerUrl(String url) {
        sp.edit().putString("server_url", url.trim()).apply();
        relay.stop();
        serverOnline = false;
        connect();
        changed();
    }

    public String myName() { return sp.getString("name", "我"); }
    public String myAvatar() { return sp.getString("avatar", "bear"); }
    public String myMood() { return sp.getString("my_mood", ""); }
    public String myMoodText() { return sp.getString("my_text", ""); }
    public long myMoodAt() { return sp.getLong("my_mood_at", 0); }
    public String mode() { return sp.getString("mode", "notify"); }
    public boolean modeAuto() { return sp.getBoolean("mode_auto", true); }
    public boolean paired() { return sp.getBoolean("paired", false); }
    public long pairedAt() { return sp.getLong("paired_at", 0); }
    public boolean quietEnabled() { return sp.getBoolean("quiet", true); }
    public String uid() { return sp.getString("uid", ""); }

    public void setProfile(String name, String avatar) {
        sp.edit().putString("name", name).putString("avatar", avatar).apply();
        sendStatus();
        pushStateToBand();
        changed();
    }

    public void setMode(String mode, boolean byUser) {
        if ("full".equals(mode) && wear.hasNode() && !Logic.canRunQuickApp(wear.deviceName())) {
            toast("当前表不支持快应用，已改为通知模式");
            mode = "notify";
        }
        SharedPreferences.Editor ed = sp.edit().putString("mode", mode);
        if (byUser) ed.putBoolean("mode_auto", false);
        ed.apply();
        sendStatus();
        changed();
    }

    /** "重新检测": go back to automatic mode detection and re-scan the wearable. */
    public void redetect() {
        sp.edit().putBoolean("mode_auto", true).apply();
        wear.refresh();
    }

    public void setQuiet(boolean on) {
        sp.edit().putBoolean("quiet", on).apply();
        changed();
    }

    public JSONObject partner() {
        try { return new JSONObject(sp.getString("partner", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    private void savePartner(JSONObject p) {
        sp.edit().putString("partner", p == null ? "{}" : p.toString()).apply();
    }

    public JSONArray history() {
        try { return new JSONArray(sp.getString("history", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private void saveHistory(JSONArray h) {
        // keep last 100
        if (h.length() > 100) {
            JSONArray t = new JSONArray();
            for (int i = h.length() - 100; i < h.length(); i++) t.put(h.opt(i));
            h = t;
        }
        sp.edit().putString("history", h.toString()).apply();
    }

    private void addHistory(JSONObject ev) {
        JSONArray h = history();
        String id = ev.optString("id");
        for (int i = 0; i < h.length(); i++) if (id.equals(h.optJSONObject(i).optString("id"))) return;
        h.put(ev);
        saveHistory(h);
    }

    public JSONArray phrases() {
        try {
            JSONArray a = new JSONArray(sp.getString("phrases", ""));
            if (a.length() > 0) return a;
        } catch (Exception ignored) { }
        JSONArray a = new JSONArray();
        for (String[] p : Logic.DEFAULT_PHRASES) a.put(phrase(p[0], p[1]));
        return a;
    }

    static JSONObject phrase(String e, String text) {
        JSONObject o = new JSONObject();
        try { o.put("e", e); o.put("text", text); } catch (Exception ignored) { }
        return o;
    }

    public boolean addPhrase(String e, String text) {
        String t = Logic.cleanPhrase(text);
        JSONArray a = phrases();
        if (t == null || a.length() >= Logic.MAX_PHRASES) return false;
        a.put(phrase(e, t));
        sp.edit().putString("phrases", a.toString()).apply();
        pushStateToBand();
        changed();
        return true;
    }

    public void removePhrase(int idx) {
        JSONArray a = phrases();
        if (a.length() <= 1) return;
        JSONArray b = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (i != idx) b.put(a.opt(i));
        sp.edit().putString("phrases", b.toString()).apply();
        pushStateToBand();
        changed();
    }

    // ------------------------------------------------------------------ actions from UI
    public void requestCode() {
        if (!relay.send(op("pair_code"))) toast("还没连上服务器");
    }

    public void joinCode(String code) {
        JSONObject o = op("pair_join");
        put(o, "code", Logic.cleanCode(code));
        if (!relay.send(o)) toast("还没连上服务器");
    }

    public void unpair() {
        relay.send(op("unpair"));
    }

    public void setPartnerMode(String mode) {
        JSONObject o = op("set_partner_mode");
        put(o, "mode", mode);
        if (!relay.send(o)) toast("还没连上服务器");
    }

    public void sendMiss(String level, String via) { sendEvent(ev("miss", "level", level), via); }
    public void sendPoke(String via) { sendEvent(ev("miss", "level", "poke3"), via); }
    public void sendAck(String via) { sendEvent(ev("ack", null, null), via); }

    public void sendMood(String mood, String text, String via) {
        mood = Logic.canonicalizeMood(mood);
        JSONObject e = ev("mood", "mood", mood);
        put(e, "text", text == null ? "" : text);
        sp.edit().putString("my_mood", mood).putString("my_text", text == null ? "" : text)
            .putLong("my_mood_at", System.currentTimeMillis()).apply();
        sendEvent(e, via);
        pushStateToBand();
        changed();
    }

    public void sendPhrase(String emojiKey, String text, String via) {
        JSONObject e = ev("phrase", "text", text);
        put(e, "e", emojiKey);
        sendEvent(e, via);
    }

    private JSONObject ev(String kind, String k, String v) {
        JSONObject e = new JSONObject();
        put(e, "kind", kind);
        if (k != null) put(e, k, v);
        return e;
    }

    private void sendEvent(JSONObject event, String via) {
        put(event, "via", via);
        if (event.optString("cid").isEmpty()) put(event, "cid", newCid());
        JSONObject o = op("event");
        put(o, "event", event);
        if (!paired()) {
            toast("还没配对哦");
            if ("band".equals(via)) bandSent(false);
            return;
        }
        if (!relay.send(o)) {
            outboxAdd(o);
            if ("phone".equals(via)) toast(Logic.sendFeedback(false));
            if ("band".equals(via)) bandSent(false);
        } else {
            if ("phone".equals(via)) toast(Logic.sendFeedback(true));
        }
    }

    // ------------------------------------------------------------------ relay callbacks
    void onRelayOpen() {
        JSONObject h = op("hello");
        if (!uid().isEmpty()) {
            put(h, "uid", uid());
            put(h, "token", sp.getString("token", ""));
        }
        put(h, "name", myName());
        put(h, "avatar", myAvatar());
        put(h, "device", wear.deviceName());
        put(h, "mode", mode());
        relay.send(h);
    }

    void onRelayClosed() {
        if (serverOnline) {
            serverOnline = false;
            pushStateToBand();
            changed();
        }
    }

    void onRelayMessage(JSONObject m) {
        String op = m.optString("op");
        switch (op) {
            case "welcome": {
                serverOnline = true;
                sp.edit().putString("uid", m.optString("uid")).putString("token", m.optString("token"))
                    .putBoolean("paired", m.optBoolean("paired")).putLong("paired_at", m.optLong("pairedAt")).apply();
                if (m.optBoolean("paired")) savePartner(m.optJSONObject("partner"));
                else savePartner(null);
                JSONArray hist = m.optJSONArray("history");
                if (hist != null && hist.length() > 0) {
                    JSONArray h = new JSONArray();
                    for (int i = 0; i < hist.length(); i++) h.put(markMine(hist.optJSONObject(i)));
                    saveHistory(h);
                }
                flushOutbox();
                sendStatus();
                pushStateToBand();
                changed();
                break;
            }
            case "error":
                if ("bad_token".equals(m.optString("reason"))) {
                    // server lost our identity (e.g. data wiped) -> start fresh, user must pair again
                    sp.edit().remove("uid").remove("token").putBoolean("paired", false).apply();
                    relay.reconnectNow();
                } else if ("not_paired".equals(m.optString("reason"))) {
                    sp.edit().putBoolean("paired", false).apply();
                    changed();
                }
                break;
            case "pair_code":
                code = m.optString("code");
                codeExpiresAt = m.optLong("expiresAt");
                JSONObject c = band("code");
                put(c, "code", code);
                put(c, "expiresAt", codeExpiresAt);
                wear.sendToBand(c);
                changed();
                break;
            case "pair_error": {
                String r = m.optString("reason");
                toast("too_many_tries".equals(r) ? "试太多次了，等一分钟再来" :
                    "own_code".equals(r) ? "这是你自己的配对码，要输入 TA 的" : "配对码不对或已过期");
                break;
            }
            case "paired":
                sp.edit().putBoolean("paired", true).putLong("paired_at", m.optLong("pairedAt"))
                    .putString("history", "[]").apply();
                savePartner(m.optJSONObject("partner"));
                code = "";
                toast("配对成功 💗");
                pushStateToBand();
                changed();
                break;
            case "unpaired":
                sp.edit().putBoolean("paired", false).putString("history", "[]").putLong("paired_at", 0).apply();
                savePartner(null);
                pushStateToBand();
                changed();
                break;
            case "event": {
                JSONObject e = markMine(m.optJSONObject("event"));
                addHistory(e);
                if ("mood".equals(e.optString("kind"))) {
                    JSONObject p = partner();
                    put(p, "mood", Logic.canonicalizeMood(e.optString("mood")));
                    put(p, "text", e.optString("text"));
                    put(p, "updatedAt", e.optLong("ts"));
                    savePartner(p);
                }
                deliverIncoming(e);
                pushStateToBand();
                changed();
                break;
            }
            case "sent": {
                JSONObject e = markMine(m.optJSONObject("event"));
                put(e, "delivered", m.optBoolean("delivered"));
                addHistory(e);
                outboxRemoveByCid(e.optString("cid"));
                if ("band".equals(e.optString("via"))) bandSent(m.optBoolean("delivered"));
                if ("mood".equals(e.optString("kind"))) {
                    sp.edit().putString("my_mood", Logic.canonicalizeMood(e.optString("mood"))).putString("my_text", e.optString("text"))
                        .putLong("my_mood_at", e.optLong("ts")).apply();
                    pushStateToBand();
                }
                changed();
                break;
            }
            case "delivered": {
                JSONArray ids = m.optJSONArray("ids");
                JSONArray h = history();
                for (int i = 0; ids != null && i < h.length(); i++) {
                    JSONObject e = h.optJSONObject(i);
                    for (int j = 0; j < ids.length(); j++) if (ids.optString(j).equals(e.optString("id"))) put(e, "delivered", true);
                }
                saveHistory(h);
                changed();
                break;
            }
            case "partner_status":
                savePartner(m.optJSONObject("partner"));
                pushStateToBand();
                changed();
                break;
            case "mode_changed": {
                String mode = m.optString("mode");
                sp.edit().putString("mode", mode).putBoolean("mode_auto", false).apply();
                toast(m.optString("by", "TA") + " 把你的提醒方式改成了「" + Logic.modeLabel(mode) + "」");
                sendStatus();
                changed();
                break;
            }
            case "history": {
                JSONArray hist = m.optJSONArray("events");
                JSONArray h = new JSONArray();
                for (int i = 0; hist != null && i < hist.length(); i++) h.put(markMine(hist.optJSONObject(i)));
                saveHistory(h);
                changed();
                break;
            }
            default:
                break;
        }
    }

    private JSONObject markMine(JSONObject e) {
        if (e == null) return new JSONObject();
        put(e, "mine", uid().equals(e.optString("from")));
        return e;
    }

    // ------------------------------------------------------------------ delivery to this user's wrist
    private void deliverIncoming(JSONObject e) {
        String kind = e.optString("kind");
        String from = e.optString("fromName", partner().optString("name", "TA"));
        String title = Logic.notifyTitle(from);
        String body = Logic.notifyBody(kind, e.optString("level"), e.optString("mood"), e.optString("text"),
            e.optString("e"), partner().optString("mood"));
        // Quiet hours / ack: phone stays silent; band still shows UI but with silent:true (no buzz).
        boolean quiet = quietEnabled() && inQuietNow();
        boolean soft = quiet || "ack".equals(kind);

        if ("full".equals(mode()) && wear.hasNode()) {
            JSONObject b = band("incoming");
            put(b, "kind", kind);
            put(b, "level", e.optString("level"));
            put(b, "mood", e.optString("mood"));
            put(b, "text", e.optString("text"));
            put(b, "e", e.optString("e"));
            put(b, "from", from);
            put(b, "ts", e.optLong("ts"));
            put(b, "silent", soft);
            pendingForBand = b;
            pendingAt = System.currentTimeMillis();
            notifier.showIncoming(title, body, true); // silent copy on the phone
            wear.deliver(b, title, body);
        } else {
            // notification mode: Mi Fitness mirrors; soft = low-priority silent phone notify
            notifier.showIncoming(title, body, soft);
        }
    }

    /** Called by WearBridge when launching the band app / messaging failed. */
    void onBandDeliveryFailed(String title, String body) {
        pendingForBand = null;
        notifier.showIncoming(title, body, false);
    }

    /** Called by WearBridge after launching the band app: send the pending event if hello didn't take it. */
    void flushPendingToBand() {
        if (pendingForBand != null) {
            JSONObject b = pendingForBand;
            pendingForBand = null;
            wear.sendToBand(b);
        }
    }

    private boolean inQuietNow() {
        Calendar c = Calendar.getInstance();
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        return Logic.inQuietHours(now, 23 * 60 + 30, 7 * 60 + 30);
    }

    // ------------------------------------------------------------------ band side
    void onBandMessage(JSONObject m) {
        String t = m.optString("t");
        switch (t) {
            case "hello":
                pushStateToBand();
                if (pendingForBand != null && System.currentTimeMillis() - pendingAt < 120000) flushPendingToBand();
                break;
            case "miss":
                sendMiss(m.optString("level", "short"), "band");
                break;
            case "poke":
                sendPoke("band");
                break;
            case "mood":
                if (Logic.isMood(m.optString("mood"))) sendMood(m.optString("mood"), "", "band");
                break;
            case "phrase":
                sendPhrase(m.optString("e", "chat"), m.optString("text"), "band");
                break;
            case "ack":
                sendAck("band");
                break;
            case "pair_code":
                if (code.length() == 6 && codeExpiresAt > System.currentTimeMillis() + 30000) {
                    JSONObject c = band("code");
                    put(c, "code", code);
                    put(c, "expiresAt", codeExpiresAt);
                    wear.sendToBand(c);
                } else {
                    requestCode();
                }
                break;
            default:
                break;
        }
    }

    private void bandSent(boolean ok) {
        JSONObject s = band("sent");
        put(s, "ok", ok);
        wear.sendToBand(s);
    }

    void pushStateToBand() {
        if (!wear.hasNode()) return;
        JSONObject s = band("state");
        put(s, "server", serverOnline);
        put(s, "paired", paired());
        JSONObject me = new JSONObject();
        put(me, "name", myName());
        put(me, "mood", myMood());
        put(s, "me", me);
        JSONObject p = partner();
        JSONObject bp = new JSONObject();
        put(bp, "name", p.optString("name", "TA"));
        put(bp, "mood", p.optString("mood"));
        put(bp, "text", p.optString("text"));
        put(bp, "online", p.optBoolean("online"));
        put(bp, "wearing", p.optBoolean("wearing"));
        put(bp, "updatedAt", p.optLong("updatedAt"));
        put(s, "partner", bp);
        put(s, "phrases", phrases());
        wear.sendToBand(s);
    }

    /** Wearable connection / detection changed. */
    void onWearChanged() {
        if (wear.hasNode()) {
            String name = wear.deviceName();
            // Watch 3 etc.: never stay on full, even if user locked mode_auto=false
            if (!Logic.canRunQuickApp(name) && "full".equals(mode())) {
                sp.edit().putString("mode", "notify").putBoolean("mode_auto", false).apply();
                toast("当前表不支持快应用，已改为通知模式");
            } else if (modeAuto()) {
                String m = Logic.detectMode(name, wear.appInstalled());
                sp.edit().putString("mode", m).apply();
            }
        }
        sendStatus();
        pushStateToBand();
        changed();
    }

    void sendStatus() {
        JSONObject s = op("status");
        put(s, "wearing", wear.wearing());
        put(s, "device", wear.deviceName());
        put(s, "mode", mode());
        put(s, "name", myName());
        put(s, "avatar", myAvatar());
        relay.send(s);
    }

    // ------------------------------------------------------------------ outbox (persisted)
    private String newCid() {
        return Long.toHexString(System.currentTimeMillis()) + "-" + Integer.toHexString((int) (Math.random() * 0xfffff));
    }

    private void loadOutbox() {
        outbox.clear();
        try {
            JSONArray a = new JSONArray(sp.getString(OUTBOX_KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) outbox.add(o);
            }
        } catch (Exception ignored) { }
    }

    private void saveOutbox() {
        JSONArray a = new JSONArray();
        for (JSONObject o : outbox) a.put(o);
        sp.edit().putString(OUTBOX_KEY, a.toString()).apply();
    }

    private void outboxAdd(JSONObject o) {
        String cid = o.optJSONObject("event") != null ? o.optJSONObject("event").optString("cid") : "";
        if (!cid.isEmpty()) {
            for (int i = 0; i < outbox.size(); i++) {
                JSONObject e = outbox.get(i).optJSONObject("event");
                if (e != null && cid.equals(e.optString("cid"))) { outbox.set(i, o); saveOutbox(); return; }
            }
        }
        outbox.add(o);
        saveOutbox();
    }

    private void outboxRemoveByCid(String cid) {
        if (cid == null || cid.isEmpty()) return;
        boolean changed = false;
        for (int i = outbox.size() - 1; i >= 0; i--) {
            JSONObject e = outbox.get(i).optJSONObject("event");
            if (e != null && cid.equals(e.optString("cid"))) {
                outbox.remove(i);
                changed = true;
            }
        }
        if (changed) saveOutbox();
    }

    /** Resend persisted outbox after welcome; drop items the socket accepts. */
    private void flushOutbox() {
        if (outbox.isEmpty()) return;
        List<JSONObject> q = new ArrayList<>(outbox);
        for (JSONObject o : q) {
            String cid = o.optJSONObject("event") != null ? o.optJSONObject("event").optString("cid") : "";
            if (relay.send(o)) outboxRemoveByCid(cid);
        }
    }

    // ------------------------------------------------------------------ util
    static JSONObject op(String op) {
        JSONObject o = new JSONObject();
        put(o, "op", op);
        return o;
    }

    static JSONObject band(String t) {
        JSONObject o = new JSONObject();
        put(o, "t", t);
        return o;
    }

    static void put(JSONObject o, String k, Object v) {
        try { o.put(k, v); } catch (Exception ignored) { }
    }

    static boolean atLeast(int sdk) { return Build.VERSION.SDK_INT >= sdk; }
}
