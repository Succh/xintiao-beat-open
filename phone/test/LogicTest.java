import com.xintiao.beat.Logic;

/** Plain-JVM tests for the Android app's pure logic (run: ./test.sh). */
public class LogicTest {
    static int fails = 0, passes = 0;
    static void eq(Object want, Object got, String what) {
        if (want == null ? got == null : want.equals(got)) passes++;
        else { fails++; System.out.println("FAIL " + what + ": want <" + want + "> got <" + got + ">"); }
    }

    public static void main(String[] a) {
        // moods
        eq(8, Logic.MOOD_IDS.length, "8 moods");
        eq("累了", Logic.moodLabel("tired"), "label");
        eq(false, Logic.isMood("nope"), "isMood");
        eq("💬", Logic.emoji("unknown"), "emoji fallback");
        // time
        long now = 1_800_000_000_000L;
        eq("刚刚", Logic.timeAgo(now - 30_000, now), "just now");
        eq("3 分钟前", Logic.timeAgo(now - 180_000, now), "minutes");
        eq("2 小时前", Logic.timeAgo(now - 7_200_000, now), "hours");
        eq("04:52", Logic.countdown(now + 292_000, now), "countdown");
        eq("00:00", Logic.countdown(now - 5, now), "countdown expired");
        eq(1L, Logic.daysTogether(now, now), "day 1");
        eq(520L, Logic.daysTogether(now - 519L * 86_400_000L, now), "520 days");
        // quiet hours across midnight
        eq(true, Logic.inQuietHours(23 * 60 + 45, 1410, 450), "quiet 23:45");
        eq(true, Logic.inQuietHours(3 * 60, 1410, 450), "quiet 03:00");
        eq(false, Logic.inQuietHours(12 * 60, 1410, 450), "not quiet noon");
        // codes / phrases
        eq("382 915", Logic.formatCode("382915"), "formatCode");
        eq("604123", Logic.cleanCode("604 123"), "cleanCode");
        eq(null, Logic.cleanPhrase("   "), "empty phrase");
        eq(Logic.MAX_PHRASE_LEN, Logic.cleanPhrase("一二三四五六七八九十一二三四五").length(), "phrase trimmed");
        // texts
        eq("想你了 🥰", Logic.eventText("miss", "short", null, null, null), "miss text");
        eq("戳了戳你 👉👉👉", Logic.eventText("miss", "poke3", null, null, null), "poke text");
        eq("💗 宝贝", Logic.notifyTitle("宝贝"), "notify title");
        eq("想你了 🥰\n心情：😪 累了 · 手机上回复", Logic.notifyBody("miss", "short", null, null, null, "tired"), "notify body");
        eq("宝贝 把心情改成了「想你」", Logic.timelineMain(false, "宝贝", "mood", null, "miss", ""), "tl mood");
        eq("你 发送了「想你了」", Logic.timelineMain(true, "宝贝", "miss", "short", null, null), "tl miss");
        eq("宝贝 戳了你 3 下", Logic.timelineMain(false, "宝贝", "miss", "poke3", null, null), "tl poke");
        eq("附言：在上课", Logic.timelineSub(false, "mood", "在上课", "phone", false), "tl sub mood");
        eq("来自手环 · 已送达", Logic.timelineSub(true, "miss", null, "band", true), "tl sub delivered");
        // devices / mode
        eq(false, Logic.canRunQuickApp("Redmi Watch 3"), "watch3 no apps");
        eq(true, Logic.canRunQuickApp("Xiaomi Smart Band 9 Pro"), "band 9 pro apps");
        eq("notify", Logic.detectMode("Redmi Watch 3", false), "detect notify");
        eq("full", Logic.detectMode("Xiaomi Smart Band 9 Pro", true), "detect full");
        eq("通知模式", Logic.modeLabel("notify"), "mode label");

        System.out.println(passes + " passed, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }
}
