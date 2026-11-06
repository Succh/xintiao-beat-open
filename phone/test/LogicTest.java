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
        eq(14, Logic.MOOD_IDS.length, "14 moods");
        eq("困困", Logic.moodLabel("tired"), "legacy tired→sleepy");
        eq("想你", Logic.moodLabel("miss_you"), "miss_you");
        eq("想你", Logic.moodLabel("miss"), "legacy miss");
        eq("miss_you", Logic.canonicalizeMood("miss"), "canon miss");
        eq(false, Logic.isMood("nope"), "isMood");
        eq(true, Logic.isMood("shy"), "isMood shy");
        eq("害羞", Logic.moodLabel("shy"), "shy label");
        eq("心情", Logic.moodLabel("nope"), "unknown mood degrade");
        eq(true, Logic.hasMoodDisplay("nope"), "hasMoodDisplay unknown");
        eq(false, Logic.hasMoodDisplay(""), "hasMoodDisplay empty");
        eq("💬", Logic.emoji("unknown"), "emoji fallback");
        eq("😳", Logic.emoji("shy"), "shy emoji");
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
        eq("想你了 🥰\n心情：😴 困困 · 手机上回复", Logic.notifyBody("miss", "short", null, null, null, "tired"), "notify body");
        eq("宝贝 把心情改成了「想你」", Logic.timelineMain(false, "宝贝", "mood", null, "miss_you", ""), "tl mood");
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


        // 0.1.4 copy helpers
        eq("已送出", Logic.sendFeedback(true), "send online");
        eq("已存着，连上就送", Logic.sendFeedback(false), "send outbox");
        eq("宝贝 的表只能看通知，回消息请用手机", Logic.notifyModeHomeTip(true, false, "宝贝"), "notify tip partner");
        eq("我的表只能看通知，回消息请用手机", Logic.notifyModeHomeTip(false, true, "宝贝"), "notify tip me");
        eq("表上只能看通知，回消息请用手机", Logic.notifyModeHomeTip(true, true, "宝贝"), "notify tip both");
        eq("", Logic.notifyModeHomeTip(false, false, "宝贝"), "notify tip none");
        eq("打开小米运动健康，确认手环已连接后再点", Logic.wearRetryFailHint(), "wear retry");
        eq(true, Logic.isXiaomiFamily("Xiaomi", "Redmi"), "xiaomi family");
        eq(false, Logic.isXiaomiFamily("samsung", "samsung"), "not xiaomi");
        eq("自启动 · 省电无限制 · 多任务上锁", Logic.keepAliveRowSub(true), "ka row xm");
        eq("关闭电池优化，避免被系统杀掉", Logic.keepAliveRowSub(false), "ka row other");
        eq(true, Logic.keepAliveToast(true).contains("自启动"), "ka toast xm");
        eq("安装到你的表", Logic.installGuideTitle(), "guide title");
        eq(true, Logic.fullDualVibrateTip(true, false).contains("完整 App"), "dual vib tip");

        // 0.1.6 preview / today summary / notify mirror guide
        eq("预览：想你 🥰", Logic.moodPreview("miss_you"), "preview miss_you");
        eq("预览：困困 😴", Logic.moodPreview("tired"), "preview legacy");
        eq("", Logic.moodPreview(""), "preview empty");
        eq(400, Logic.PREVIEW_HOLD_MS, "hold ms");
        eq(true, Logic.sameDay(now - 60_000, now), "same day");
        eq(false, Logic.sameDay(now - 2 * 86_400_000L, now), "not same day");
        eq(false, Logic.sameDay(0, now), "ts 0");
        eq(2, Logic.todayMoodCount(new String[]{"mood", "miss", "mood", "mood"},
            new long[]{now - 1000, now - 2000, now - 3 * 86_400_000L, now - 5000}, now), "today mood count");
        eq(0, Logic.todayMoodCount(null, null, now), "count null");
        eq("今天你们互换了 3 次心情 ›", Logic.moodSummaryLine(3), "summary line");
        eq(true, Logic.moodSummaryLine(0).startsWith("今天还没互换心情"), "summary zero");
        eq("我", Logic.moodSummaryWho(true, "宝贝"), "who me");
        eq("宝贝", Logic.moodSummaryWho(false, "宝贝"), "who partner");
        eq("TA", Logic.moodSummaryWho(false, ""), "who TA");
        eq(true, Logic.isNotifyOnlyDevice("Redmi Watch 3"), "watch3 notify-only");
        eq(false, Logic.isNotifyOnlyDevice("Xiaomi Smart Band 9 Pro"), "band9pro full");
        eq(false, Logic.isNotifyOnlyDevice("Redmi Watch 5"), "watch5 full");
        eq(false, Logic.isNotifyOnlyDevice(""), "unknown not notify-only");
        eq(false, Logic.isNotifyOnlyDevice(null), "null not notify-only");
        eq(3, Logic.MIRROR_STEPS.length, "3 mirror steps");
        eq(true, Logic.MIRROR_STEPS[0].contains("小米运动健康") && Logic.MIRROR_STEPS[0].contains("通知与提醒"), "step1");
        eq(true, Logic.MIRROR_STEPS[1].contains("TA 的心跳"), "step2 channel");
        eq(true, Logic.MIRROR_STEPS[2].contains("试发一条"), "step3");
        eq(true, Logic.MIRROR_FIXES.length >= 1 && Logic.MIRROR_FIXES.length <= 2, "1-2 fixes");
        eq("com.xintiao.beat.debug.0.1.8.rpk", Logic.BAND_RPK, "rpk name");

        // 0.1.7 mood-aware FX + today miss line
        eq("🥰", Logic.moodFxCenter("miss_you"), "fx center miss_you");
        eq("😴", Logic.moodFxCenter("tired"), "fx center legacy");
        eq("💗", Logic.moodFxCenter(""), "fx center empty");
        eq("💗", Logic.moodFxCenter(null), "fx center null");
        eq(8, Logic.moodFxBits("sweet").length, "fx bits len");
        eq("🍬", Logic.moodFxBits("sweet")[0], "fx bits first");
        eq("💗", Logic.missFxCenter(), "miss fx center");
        eq(8, Logic.missFxBits().length, "miss fx bits");
        eq(2, Logic.todayKindCount(new String[]{"miss", "mood", "miss", "miss"},
            new long[]{now - 1000, now - 2000, now - 3 * 86_400_000L, now - 5000}, now, "miss"), "today miss count");
        eq("", Logic.todayMissLine(0), "miss line zero");
        eq("今天互发了 4 次「想你了」", Logic.todayMissLine(4), "miss line");
        eq(true, Logic.isPoke("poke3"), "isPoke");
        eq(false, Logic.isPoke("short"), "not poke");
        eq("", Logic.todayTogetherLine(0, 0), "together zero");
        eq("今天互发了 2 次「想你了」", Logic.todayTogetherLine(2, 0), "together miss only");
        eq("今天互戳了 3 次", Logic.todayTogetherLine(0, 3), "together poke only");
        eq("今天 · 想你了 2 次 · 戳了 1 次", Logic.todayTogetherLine(2, 1), "together both");
        eq(true, Logic.shouldPlayInboundMissFx("miss", "short", false), "inbound miss");
        eq(true, Logic.shouldPlayInboundMissFx("miss", "poke3", false), "inbound poke");
        eq(false, Logic.shouldPlayInboundMissFx("miss", "short", true), "outbound no fx helper");
        eq(false, Logic.shouldPlayInboundMissFx("mood", null, false), "mood not miss fx");

        System.out.println(passes + " passed, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }
}
