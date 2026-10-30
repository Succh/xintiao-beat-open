package com.xintiao.beat;

import java.util.Calendar;
import java.util.Locale;

/** Pure helpers with no Android dependencies (unit-tested on the plain JVM). */
public final class Logic {
    private Logic() {}

    public static final String[] MOOD_IDS = {"happy", "miss", "tired", "angry", "aggrieved", "busy", "hungry", "night"};
    public static final String[] MOOD_LABELS = {"开心", "想你", "累了", "生气", "委屈", "忙碌", "饿了", "晚安"};

    private static final String[][] EMOJI = {
        {"happy", "😊"}, {"miss", "🥰"}, {"tired", "😪"}, {"angry", "😤"}, {"aggrieved", "🥺"},
        {"busy", "💼"}, {"hungry", "🍜"}, {"night", "🌙"}, {"eyes", "👀"}, {"heart", "💗"},
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
        for (String[] e : EMOJI) if (e[0].equals(key)) return e[1];
        return "💬";
    }

    public static String moodLabel(String id) {
        if (id == null) return "";
        for (int i = 0; i < MOOD_IDS.length; i++) if (MOOD_IDS[i].equals(id)) return MOOD_LABELS[i];
        return "";
    }

    public static boolean isMood(String id) {
        return moodLabel(id).length() > 0;
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
}
