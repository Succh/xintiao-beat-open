package com.xintiao.beat;

import java.util.Calendar;
import java.util.Locale;

/** Pure helpers with no Android dependencies (unit-tested on the plain JVM). */
public final class Logic {
    private Logic() {}

    /** Featured first three: miss_you / sweet / love (default highlight). */
    public static final String[] MOOD_IDS = {
        "miss_you", "sweet", "love", "warm", "happy", "soft", "shy", "clingy",
        "sleepy", "cozy", "excited", "grateful", "playful", "calm"
    };
    public static final String[] MOOD_LABELS = {
        "想你", "甜甜", "喜欢", "暖暖", "开心", "软软", "害羞", "黏你",
        "困困", "窝着", "兴奋", "感谢", "调皮", "平静"
    };

    /** Old 0.1.2/early-0.1.3 ids → canonical. */
    private static final String[][] MOOD_ALIASES = {
        {"miss", "miss_you"}, {"tired", "sleepy"}, {"angry", "soft"}, {"aggrieved", "soft"},
        {"busy", "calm"}, {"hungry", "cozy"}, {"night", "sleepy"}, {"jealous", "shy"},
        {"cheer", "excited"}, {"melt", "love"}
    };

    private static final String[][] EMOJI = {
        {"miss_you", "🥰"}, {"sweet", "🍬"}, {"love", "😍"}, {"warm", "🌸"}, {"happy", "😊"},
        {"soft", "🥺"}, {"shy", "😳"}, {"clingy", "🥹"}, {"sleepy", "😴"}, {"cozy", "☕"},
        {"excited", "🤩"}, {"grateful", "🙏"}, {"playful", "😜"}, {"calm", "😌"},
        /* legacy keys kept for phrases + old payloads */
        {"miss", "🥰"}, {"tired", "😪"}, {"angry", "😤"}, {"aggrieved", "🥺"},
        {"busy", "💼"}, {"hungry", "🍜"}, {"night", "🌙"}, {"jealous", "😒"}, {"cheer", "💪"}, {"melt", "🫠"},
        {"eyes", "👀"}, {"heart", "💗"},
        {"rice", "🍚"}, {"run", "🏃"}, {"chat", "💬"}, {"hearts", "💕"}, {"poke", "👉"},
        {"coffee", "☕"}, {"party", "🎉"}, {"kiss", "😘"}, {"hug", "🤗"}, {"wave", "👋"},
        {"home", "🏠"}, {"phone", "📞"}, {"book", "📚"}, {"ok", "👌"}, {"sun", "🌅"}, {"sparkle", "✨"},
        {"bear", "🐻"}, {"rabbit", "🐰"}, {"cat", "🐱"}, {"dog", "🐶"}, {"pig", "🐷"}, {"fox", "🦊"}
    };

    /** Emoji keys offered when adding a custom phrase (all have a PNG on the band). */
    public static final String[] PHRASE_EMOJI = {"heart", "eyes", "night", "rice", "run", "coffee", "kiss",
        "hug", "wave", "home", "phone", "party", "ok", "sun", "sparkle", "hearts"};
    /** Avatar choices for the profile. */
    public static final String[] AVATARS = {"bear", "rabbit", "cat", "dog", "pig", "fox"};

    public static final String[][] DEFAULT_PHRASES = {
        {"eyes", "在干嘛？"}, {"heart", "想你了"}, {"night", "晚安～"}, {"rice", "吃饭了吗"}, {"run", "马上到！"}
    };
    public static final int MAX_PHRASES = 8;
    public static final int MAX_PHRASE_LEN = 12;

    public static String emoji(String key) {
        if (key == null) return "💬";
        // Prefer canonical mood glyph when legacy id aliases (tired→sleepy etc.)
        String c = canonicalizeMood(key);
        for (String[] e : EMOJI) if (e[0].equals(c)) return e[1];
        if (!c.equals(key)) for (String[] e : EMOJI) if (e[0].equals(key)) return e[1];
        return "💬";
    }

    /** Map legacy mood id to canonical; unknown returns original. */
    public static String canonicalizeMood(String id) {
        if (id == null || id.isEmpty()) return id == null ? "" : id;
        for (String[] a : MOOD_ALIASES) if (a[0].equals(id)) return a[1];
        return id;
    }

    public static String moodLabel(String id) {
        if (id == null || id.isEmpty()) return "";
        String c = canonicalizeMood(id);
        for (int i = 0; i < MOOD_IDS.length; i++) if (MOOD_IDS[i].equals(c)) return MOOD_LABELS[i];
        return "心情"; // unknown id: degrade for old/new client mismatch
    }

    public static boolean isMood(String id) {
        if (id == null || id.isEmpty()) return false;
        String c = canonicalizeMood(id);
        for (String m : MOOD_IDS) if (m.equals(c)) return true;
        return false;
    }

    /** Display helper: known mood uses label; unknown non-empty id still shows something. */
    public static boolean hasMoodDisplay(String id) {
        return id != null && !id.isEmpty();
    }

    public static String timeAgo(long ts, long now) {
        if (ts <= 0) return "";
        long s = Math.max(0, (now - ts) / 1000);
        if (s < 60) return "刚刚";
        long m = s / 60;
        if (m < 60) return m + " 分钟前";
        long h = m / 60;
        if (h < 24) return h + " 小时前";
        return (h / 24) + " 天前";
    }

    public static String clock(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    public static String countdown(long expiresAt, long now) {
        long s = Math.max(0, (expiresAt - now) / 1000);
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    public static long daysTogether(long pairedAt, long now) {
        if (pairedAt <= 0) return 0;
        return Math.max(0, (now - pairedAt) / 86400000L) + 1;
    }

    /** Quiet hours may wrap midnight, e.g. 23:30–07:30. Minutes since midnight. */
    public static boolean inQuietHours(int nowMin, int startMin, int endMin) {
        if (startMin == endMin) return false;
        if (startMin < endMin) return nowMin >= startMin && nowMin < endMin;
        return nowMin >= startMin || nowMin < endMin;
    }

    public static String formatCode(String code) {
        if (code == null || code.length() != 6) return code == null ? "" : code;
        return code.substring(0, 3) + " " + code.substring(3);
    }

    public static String cleanCode(String s) {
        if (s == null) return "";
        return s.replaceAll("\\D", "");
    }

    public static String cleanPhrase(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) return null;
        int n = t.codePointCount(0, t.length());
        if (n > MAX_PHRASE_LEN) t = t.substring(0, t.offsetByCodePoints(0, MAX_PHRASE_LEN));
        return t;
    }

    /** What happened, as the main line of a timeline row / notification body. */
    public static String eventText(String kind, String level, String mood, String text, String e) {
        if ("miss".equals(kind)) {
            if ("poke3".equals(level)) return "戳了戳你 👉👉👉";
            if ("long".equals(level)) return "好想好想你 🥰";
            return "想你了 🥰";
        }
        if ("mood".equals(kind)) {
            String s = "心情 " + emoji(mood) + " " + moodLabel(mood);
            return text != null && !text.isEmpty() ? s + "：" + text : s;
        }
        if ("phrase".equals(kind)) return emoji(e) + " " + (text == null ? "" : text);
        if ("ack".equals(kind)) return "收到啦 👌";
        return "";
    }

    /** Timeline icon for an event. */
    public static String eventIcon(String kind, String level, String mood, String e) {
        if ("miss".equals(kind)) return "poke3".equals(level) ? "👉" : "💗";
        if ("mood".equals(kind)) return emoji(mood);
        if ("phrase".equals(kind)) return emoji(e);
        if ("ack".equals(kind)) return "👌";
        return "💬";
    }

    /** Timeline second line, e.g. "TA · 来自手环" / "我 · 手机". */
    public static String eventSub(boolean mine, String partnerName, String via) {
        String who = mine ? "我" : (partnerName == null || partnerName.isEmpty() ? "TA" : partnerName);
        return who + " · " + ("band".equals(via) ? "来自手环" : "来自手机");
    }

    public static String notifyTitle(String fromName) {
        return "💗 " + (fromName == null || fromName.isEmpty() ? "TA" : fromName);
    }

    /** Body shown on the phone and mirrored to Redmi Watch 3 by Mi Fitness. */
    public static String notifyBody(String kind, String level, String mood, String text, String e, String partnerMood) {
        String main = eventText(kind, level, mood, text, e);
        String m = "mood".equals(kind) ? null : partnerMood;
        if (m != null && isMood(m)) return main + "\n心情：" + emoji(m) + " " + moodLabel(m) + " · 手机上回复";
        return main;
    }

    /**
     * Pick a delivery mode from the connected wearable name.
     * Redmi Watch 3 (and other non-Vela watches) cannot run quick apps -> notification mode.
     */
    public static String detectMode(String nodeName, boolean wearAppInstalled) {
        if (wearAppInstalled) return "full";
        return "notify";
    }

    public static boolean canRunQuickApp(String nodeName) {
        if (nodeName == null) return false;
        String n = nodeName.toLowerCase(Locale.ROOT);
        if (n.contains("watch 3") || n.contains("watch3")) return false;
        return n.contains("band 8 pro") || n.contains("band 9") || n.contains("band 10") || n.contains("手环8 pro")
            || n.contains("手环9") || n.contains("手环10") || n.contains("watch 5") || n.contains("watch 6")
            || n.contains("watch s") || n.contains("watch h1");
    }

    public static String modeLabel(String mode) {
        return "full".equals(mode) ? "完整手表 App" : "通知模式";
    }

    /** Timeline main line, e.g. "宝贝 把心情改成了「想你」" / "你 发送了「想你了」". */
    public static String timelineMain(boolean mine, String partnerName, String kind, String level, String mood, String text) {
        String who = mine ? "你" : (partnerName == null || partnerName.isEmpty() ? "TA" : partnerName);
        if ("miss".equals(kind)) {
            if ("poke3".equals(level)) return mine ? "你 戳了 TA 3 下" : who + " 戳了你 3 下";
            String w = "long".equals(level) ? "好想好想你" : "想你了";
            return mine ? "你 发送了「" + w + "」" : who + " " + w;
        }
        if ("mood".equals(kind)) return who + " 把心情改成了「" + moodLabel(mood) + "」";
        if ("phrase".equals(kind)) return who + " 说「" + (text == null ? "" : text) + "」";
        if ("ack".equals(kind)) return who + " 回了「收到」";
        return who;
    }

    /** Timeline small line. */
    public static String timelineSub(boolean mine, String kind, String text, String via, boolean delivered) {
        if ("mood".equals(kind) && text != null && !text.isEmpty()) return "附言：" + text;
        String src = "band".equals(via) ? "来自手环" : "来自手机";
        if (!mine) return src;
        return src + (delivered ? " · 已送达" : " · TA 上线后送达");
    }
    /** Phone toast after sendEvent: online vs outbox. */
    public static String sendFeedback(boolean sentOnline) {
        return sentOnline ? "已送出" : "已存着，连上就送";
    }

    /** Home tip when self and/or partner use notify mode. Empty if neither. */
    public static String notifyModeHomeTip(boolean partnerNotify, boolean meNotify, String partnerName) {
        if (!partnerNotify && !meNotify) return "";
        if (partnerNotify && meNotify) return "表上只能看通知，回消息请用手机";
        if (partnerNotify) {
            String who = (partnerName == null || partnerName.isEmpty()) ? "TA" : partnerName;
            return who + " 的表只能看通知，回消息请用手机";
        }
        return "我的表只能看通知，回消息请用手机";
    }

    /** Shown when band re-detect fails / not connected. */
    public static String wearRetryFailHint() {
        return "打开小米运动健康，确认手环已连接后再点";
    }

    /** Xiaomi / Redmi / POCO / HyperOS family (MIUI-style autostart + battery pages). */
    public static boolean isXiaomiFamily(String manufacturer, String brand) {
        String m = manufacturer == null ? "" : manufacturer.toLowerCase(Locale.ROOT);
        String b = brand == null ? "" : brand.toLowerCase(Locale.ROOT);
        for (String k : new String[]{"xiaomi", "redmi", "poco", "blackshark"}) {
            if (m.contains(k) || b.contains(k)) return true;
        }
        return false;
    }

    /** Settings/keepAlive one-liner under the row. */
    public static String keepAliveRowSub(boolean xiaomiFamily) {
        return xiaomiFamily
            ? "自启动 · 省电无限制 · 多任务上锁"
            : "关闭电池优化，避免被系统杀掉";
    }

    /** Toast after opening app-info / battery page. */
    public static String keepAliveToast(boolean xiaomiFamily) {
        return xiaomiFamily
            ? "① 自启动打开  ② 省电策略选「无限制」  ③ 多任务界面下拉上锁"
            : "请允许忽略电池优化，保持后台连接";
    }

    /** Home tip when both sides use full (phone notify + band popup may both fire). */
    public static String fullDualVibrateTip(boolean meFull, boolean partnerFull) {
        if (!meFull && !partnerFull) return "";
        if (meFull && partnerFull) return "双方都是完整 App：手机通知和手表可能都会振一下";
        if (meFull) return "我是完整 App：手机通知和手表可能都会振一下";
        return "TA 是完整 App：手机通知和手表可能都会振一下";
    }

    /** Guide sheet title — not locked to Band 9 Pro. */
    public static String installGuideTitle() {
        return "安装到你的表";
    }

    // ------------------------------------------------------------------ 0.1.6
    /** Band package file name shown in the AstroBox step (kept in sync with band/src/manifest.json). */
    public static final String BAND_RPK = "com.xintiao.beat.debug.0.1.10.rpk";

    /** Long-press threshold for the local-only mood preview (ms). */
    public static final int PREVIEW_HOLD_MS = 400;

    /** Local-only preview line shown while holding a mood tile; never sent. */
    public static String moodPreview(String moodId) {
        if (moodId == null || moodId.isEmpty()) return "";
        return "预览：" + moodLabel(moodId) + " " + emoji(moodId);
    }

    /** Small line under the preview bubble. */
    public static String moodPreviewHint() {
        return "TA 会看到这个心情 · 松手不发送";
    }

    /** True if ts falls on the same local calendar day as now. */
    public static boolean sameDay(long ts, long now) {
        if (ts <= 0) return false;
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(ts);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(now);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** Counts today's mood events from parallel arrays (kind, ts) of local history. */
    public static int todayMoodCount(String[] kinds, long[] ts, long now) {
        if (kinds == null || ts == null) return 0;
        int n = 0;
        for (int i = 0; i < Math.min(kinds.length, ts.length); i++)
            if ("mood".equals(kinds[i]) && sameDay(ts[i], now)) n++;
        return n;
    }

    /** Grey one-liner at the bottom of home. */
    public static String moodSummaryLine(int n) {
        if (n <= 0) return "今天还没互换心情 · 点「😊 改心情」告诉 TA";
        return "今天你们互换了 " + n + " 次心情 ›";
    }

    /** Who column in the today-mood list. */
    public static String moodSummaryWho(boolean mine, String partnerName) {
        if (mine) return "我";
        return partnerName == null || partnerName.isEmpty() ? "TA" : partnerName;
    }

    /** Wearable is known and cannot run quick apps (e.g. Redmi Watch 3) → notification-mirror guide. */
    public static boolean isNotifyOnlyDevice(String nodeName) {
        return nodeName != null && !nodeName.trim().isEmpty() && !canRunQuickApp(nodeName);
    }

    /** Notification-mirror checklist for notify-only watches (3 steps). */
    public static final String[] MIRROR_STEPS = {
        "打开「小米运动健康」→ 设备 → 通知与提醒 → 找到「心跳」并打开开关",
        "手机设置里允许「心跳」通知，并确认「TA 的心跳」这一类通知是打开的",
        "点下面「试发一条」，手机会弹一条测试通知，看表上是否亮起"
    };

    /** Failure state: only 1–2 short fixes. */
    public static final String[] MIRROR_FIXES = {
        "表和手机要保持蓝牙连接：在小米运动健康首页看到「已连接」",
        "关掉手机勿扰 / 专注模式后再试一次"
    };

    public static String mirrorGuideTitle() { return "通知镜像清单"; }

    public static String mirrorTestTitle() { return "💗 心跳 · 测试"; }

    public static String mirrorTestBody() { return "表上亮了就说明通知镜像成功啦～"; }

    // ------------------------------------------------------------------ 0.1.7 / 0.1.8
    /** Center glyph for mood FX; falls back to heart. */
    public static String moodFxCenter(String moodId) {
        if (moodId != null && isMood(moodId)) return emoji(moodId);
        return "💗";
    }

    /** Eight spark glyphs around the mood FX center (mood emoji mixed with hearts). */
    public static String[] moodFxBits(String moodId) {
        String e = moodFxCenter(moodId);
        return new String[]{e, "💕", "✨", e, "💖", "💗", "✨", e};
    }

    /** Soft FX when tapping 想你了 / 戳三下 (no mood id). */
    public static String missFxCenter() { return "💗"; }

    public static String[] missFxBits() {
        return new String[]{"💕", "💗", "✨", "💖", "💕", "💗", "✨", "💖"};
    }

    /** Counts today's events of one kind from parallel arrays. */
    public static int todayKindCount(String[] kinds, long[] ts, long now, String kind) {
        if (kinds == null || ts == null || kind == null) return 0;
        int n = 0;
        for (int i = 0; i < Math.min(kinds.length, ts.length); i++)
            if (kind.equals(kinds[i]) && sameDay(ts[i], now)) n++;
        return n;
    }

    /** Optional home line under the mood summary; empty when zero. */
    public static String todayMissLine(int n) {
        return todayTogetherLine(n, 0);
    }

    /** True when a miss event is the poke-3 gesture (not short/long 想你了). */
    public static boolean isPoke(String level) {
        return "poke3".equals(level);
    }

    /** Home footer: miss and/or poke counts for today; empty when both zero. */
    public static String todayTogetherLine(int missN, int pokeN) {
        if (missN <= 0 && pokeN <= 0) return "";
        if (missN > 0 && pokeN <= 0) return "今天互发了 " + missN + " 次「想你了」";
        if (missN <= 0) return "今天互戳了 " + pokeN + " 次";
        return "今天 · 想你了 " + missN + " 次 · 戳了 " + pokeN + " 次";
    }

    /** Soft FX bits for inbound partner miss/poke (same palette as outbound). */
    public static boolean shouldPlayInboundMissFx(String kind, String level, boolean mine) {
        return !mine && "miss".equals(kind);
    }

    // ------------------------------------------------------------------ 0.1.9
    /** Soft FX when sending/receiving a phrase; center follows phrase emoji. */
    public static String phraseFxCenter(String emojiKey) {
        if (emojiKey == null || emojiKey.isEmpty()) return "💬";
        return emoji(emojiKey);
    }

    public static String[] phraseFxBits(String emojiKey) {
        String e = phraseFxCenter(emojiKey);
        return new String[]{e, "✨", "💕", e, "💖", "💬", "✨", e};
    }

    /** Soft FX for inbound partner phrase while the phone app is open. */
    public static boolean shouldPlayInboundPhraseFx(String kind, boolean mine) {
        return !mine && "phrase".equals(kind);
    }

    // ------------------------------------------------------------------ 0.1.10
    /** Soft FX when sending/receiving「收到」. */
    public static String ackFxCenter() { return "👌"; }

    public static String[] ackFxBits() {
        return new String[]{"👌", "💕", "✨", "💗", "👌", "💖", "✨", "💕"};
    }

    /** Soft FX for inbound partner ack while the phone app is open. */
    public static boolean shouldPlayInboundAckFx(String kind, boolean mine) {
        return !mine && "ack".equals(kind);
    }

}

