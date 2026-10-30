package com.xintiao.beat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements Hub.Listener {
    private static final int TAB_HOME = 0, TAB_LOG = 1, TAB_SET = 2;
    private Hub hub;
    private Ui ui;
    private FrameLayout root;
    private int tab = TAB_HOME;
    private String typedCode = "";
    private TextView countdownView;
    private TextView codeView;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (countdownView != null) updateCountdown();
            hub.main.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Color.WHITE);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        hub = Hub.get(getApplicationContext());
        ui = new Ui(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        if (Hub.atLeast(33) && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        hub.start();
        BeatService.start(getApplicationContext());
    }

    @Override protected void onResume() {
        super.onResume();
        hub.addListener(this);
        hub.connect();
        render();
        hub.main.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        hub.removeListener(this);
        hub.main.removeCallbacks(tick);
    }

    @Override public void onChange() { render(); }

    @Override public void onToast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    @Override public void onBackPressed() {
        if (hub.paired() && tab != TAB_HOME) { tab = TAB_HOME; render(); return; }
        super.onBackPressed();
    }

    // =====================================================================================
    private void render() {
        int scrollY = 0;
        View old = root.getChildCount() > 0 ? root.findViewWithTag("scroll") : null;
        if (old != null) scrollY = old.getScrollY();
        root.removeAllViews();
        countdownView = null;
        codeView = null;
        if (!hub.paired()) {
            root.addView(scroll(pairingScreen(), scrollY, 0));
            return;
        }
        LinearLayout content = tab == TAB_HOME ? homeScreen() : tab == TAB_LOG ? logScreen() : settingsScreen();
        root.addView(scroll(content, scrollY, 84));
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.px(84), Gravity.BOTTOM);
        root.addView(nav(), np);
    }

    private ScrollView scroll(View content, int y, float bottomCss) {
        ScrollView s = new ScrollView(this);
        s.setTag("scroll");
        s.setFillViewport(true);
        s.setClipToPadding(false);
        s.setPadding(0, 0, 0, ui.px(bottomCss + 24));
        s.addView(content);
        s.post(() -> s.scrollTo(0, y));
        return s;
    }

    private View nav() {
        LinearLayout n = ui.row();
        n.setBackgroundColor(Color.WHITE);
        n.setGravity(Gravity.TOP);
        ui.pad(n, 0, 12, 0, 0);
        String[][] items = {{"💗", "首页"}, {"📅", "记录"}, {"⚙️", "设置"}};
        for (int i = 0; i < 3; i++) {
            final int t = i;
            LinearLayout it = ui.col();
            it.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView e = ui.text(items[i][0], 22, Ui.TEXT, false);
            e.setGravity(Gravity.CENTER);
            if (tab != i) e.setAlpha(0.45f);
            TextView l = ui.text(items[i][1], 12, tab == i ? Ui.PINK_DEEP : Ui.MUTED, tab == i);
            l.setGravity(Gravity.CENTER);
            it.addView(e);
            it.addView(l);
            it.setOnClickListener(v -> { tab = t; render(); });
            n.addView(it, new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
        }
        FrameLayout f = new FrameLayout(this);
        f.addView(n);
        View top = ui.divider();
        f.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, ui.px(1))));
        return f;
    }

    private TextView title(String s) {
        TextView t = ui.text(s, 26, Ui.TEXT, true);
        t.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 24, 16, 24, 0));
        return t;
    }

    // ===================================================================== pairing screen
    private LinearLayout pairingScreen() {
        LinearLayout c = ui.col();

        // hero
        LinearLayout hero = ui.col();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{0xFFFFD6E6, 0xFFE6DAFF});
        float r = ui.px(48);
        g.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        hero.setBackground(g);
        ui.pad(hero, 24, 60, 24, 36);
        LinearLayout pair = ui.row();
        pair.setGravity(Gravity.CENTER);
        pair.addView(avatarBubble(Logic.emoji(hub.myAvatar()), 112, 60));
        TextView h = ui.text("💗", 30, Ui.TEXT, false);
        h.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable hb = ui.oval(Ui.PINK);
        hb.setStroke(ui.px(5), Color.WHITE);
        h.setBackground(hb);
        LinearLayout.LayoutParams hp = ui.lp(ui.px(60), ui.px(60));
        hp.setMargins(-ui.px(14), 0, -ui.px(14), 0);
        h.setElevation(ui.px(8));
        pair.addView(h, hp);
        pair.addView(avatarBubble("🐰", 112, 60));
        hero.addView(pair);
        TextView t = ui.text("和 TA 连上心跳", 30, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 36, 0, 0));
        hero.addView(t);
        TextView p = ui.text("配对后，你们的心情、想念和小短语\n会实时出现在对方的手环 / 手表上", 15, Ui.MUTED, false);
        p.setGravity(Gravity.CENTER);
        p.setLineSpacing(0, 1.3f);
        p.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        hero.addView(p);
        c.addView(hero);

        // profile + server
        LinearLayout prof = ui.card();
        ui.pad(prof, 24, 18, 24, 18);
        LinearLayout pr = ui.row();
        TextView pl = ui.text("我的昵称", 14, Ui.MUTED, false);
        pr.addView(pl, ui.weight(1));
        TextView edit = ui.text(hub.myName() + "  " + Logic.emoji(hub.myAvatar()) + "  ›", 15, Ui.LAV_DEEP, true);
        pr.addView(edit);
        pr.setOnClickListener(v -> editProfile());
        prof.addView(pr);
        prof.addView(spacer(12));
        prof.addView(ui.divider());
        prof.addView(spacer(12));
        LinearLayout sr = ui.row();
        TextView sl = ui.text("服务器", 14, Ui.MUTED, false);
        sr.addView(sl, ui.weight(1));
        sr.addView(serverStatus());
        sr.setOnClickListener(v -> editServer());
        prof.addView(sr);
        prof.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 24, -28, 24, 0));
        c.addView(prof);

        // my code
        LinearLayout mine = ui.card();
        ui.pad(mine, 24, 22, 24, 22);
        LinearLayout lr = ui.row();
        lr.addView(ui.text("我的配对码 · 发给 TA", 14, Ui.MUTED, false), ui.weight(1));
        countdownView = ui.text("", 14, Ui.LAV_DEEP, false);
        lr.addView(countdownView);
        mine.addView(lr);
        LinearLayout cr = ui.row();
        cr.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        codeView = ui.text("", 44, Ui.TEXT, true);
        codeView.setLetterSpacing(0.12f);
        cr.addView(codeView, ui.weight(1));
        LinearLayout acts = ui.row();
        TextView copy = ui.pill("复制", 14, Ui.PINK_DEEP, Ui.PINK_TINT, 36);
        TextView share = ui.pill("分享", 14, Ui.PINK_DEEP, Ui.PINK_TINT, 36);
        copy.setOnClickListener(v -> copyCode());
        share.setOnClickListener(v -> shareCode());
        acts.addView(copy);
        LinearLayout.LayoutParams sp = ui.lp(Ui.WRAP, Ui.WRAP);
        sp.leftMargin = ui.px(8);
        acts.addView(share, sp);
        cr.addView(acts);
        mine.addView(cr);
        mine.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 24, 16, 24, 0));
        c.addView(mine);
        updateCountdown();

        TextView or = ui.text("—————  或  —————", 14, 0xFFD8C8D6, false);
        or.setGravity(Gravity.CENTER);
        or.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 18, 0, 18));
        c.addView(or);

        // input
        LinearLayout inp = ui.card();
        ui.pad(inp, 24, 22, 24, 22);
        inp.addView(ui.text("输入 TA 的配对码", 14, Ui.MUTED, false));
        FrameLayout boxesWrap = new FrameLayout(this);
        LinearLayout boxes = ui.row();
        final TextView[] cells = new TextView[6];
        for (int i = 0; i < 6; i++) {
            cells[i] = ui.text("", 28, Ui.TEXT, true);
            cells[i].setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, ui.px(64), 1);
            if (i > 0) bp.leftMargin = ui.px(10);
            boxes.addView(cells[i], bp);
        }
        EditText hidden = new EditText(this);
        hidden.setInputType(InputType.TYPE_CLASS_NUMBER);
        hidden.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        hidden.setTextColor(Color.TRANSPARENT);
        hidden.setBackground(null);
        hidden.setCursorVisible(false);
        hidden.setText(typedCode);
        hidden.setSelection(typedCode.length());
        Runnable paint = () -> {
            for (int i = 0; i < 6; i++) {
                boolean filled = i < typedCode.length();
                boolean cur = i == typedCode.length();
                cells[i].setText(filled ? String.valueOf(typedCode.charAt(i)) : "");
                cells[i].setBackground(ui.stroke(filled || cur ? Color.WHITE : Ui.BG, 16, 2,
                    cur ? Ui.PINK : filled ? Ui.LAV : Ui.LINE));
            }
        };
        paint.run();
        hidden.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c2) { }
            public void onTextChanged(CharSequence s, int a, int b, int c2) { }
            public void afterTextChanged(Editable s) {
                typedCode = Logic.cleanCode(s.toString());
                paint.run();
                if (typedCode.length() == 6) hideKeyboard(hidden);
            }
        });
        boxesWrap.addView(boxes, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.px(64)));
        boxesWrap.addView(hidden, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.px(64)));
        boxesWrap.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 14, 0, 0));
        inp.addView(boxesWrap);
        inp.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 24, 0, 24, 0));
        c.addView(inp);

        TextView go = ui.pill("开始配对", 18, Color.WHITE, Ui.PINK, 60);
        go.setElevation(ui.px(6));
        go.setLayoutParams(ui.lp(Ui.MATCH, ui.px(60), 24, 22, 24, 0));
        go.setOnClickListener(v -> {
            if (typedCode.length() != 6) { onToast("配对码是 6 位数字"); return; }
            hub.joinCode(typedCode);
        });
        c.addView(go);

        TextView tips = ui.text("小提示　配对码只在 5 分钟内有效。手环上的「心跳」也会显示同一个配对码，两边任选其一输入即可。", 13, Ui.TIP, false);
        tips.setLineSpacing(0, 1.4f);
        tips.setBackground(ui.roundCss(Ui.LAV_TINT, 20));
        ui.pad(tips, 20, 18, 20, 18);
        tips.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 24, 24, 24, 0));
        c.addView(tips);

        TextView foot = ui.text("数据只在你们两人之间同步", 12, Ui.MUTED, false);
        foot.setGravity(Gravity.CENTER);
        foot.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 24, 0, 8));
        c.addView(foot);
        return c;
    }

    private TextView avatarBubble(String emoji, float sizeCss, float fontCss) {
        TextView a = ui.text(emoji, fontCss, Ui.TEXT, false);
        a.setGravity(Gravity.CENTER);
        a.setBackground(ui.oval(Color.WHITE));
        a.setElevation(ui.px(6));
        a.setLayoutParams(ui.lp(ui.px(sizeCss), ui.px(sizeCss)));
        return a;
    }

    private View spacer(float css) {
        View v = new View(this);
        v.setLayoutParams(ui.lp(Ui.MATCH, ui.px(css)));
        return v;
    }

    private TextView serverStatus() {
        String url = hub.serverUrl();
        String s;
        int col;
        if (url.isEmpty()) { s = "未设置 ›"; col = Ui.PINK_DEEP; }
        else if (hub.serverOnline) { s = "已连接 ›"; col = Ui.OK; }
        else { s = "连接中… ›"; col = Ui.MUTED; }
        return ui.text(s, 15, col, true);
    }

    private void updateCountdown() {
        if (countdownView == null || codeView == null) return;
        long now = System.currentTimeMillis();
        boolean valid = !hub.code.isEmpty() && hub.codeExpiresAt > now;
        if (valid) {
            codeView.setText(Logic.formatCode(hub.code));
            codeView.setTextColor(Ui.TEXT);
            codeView.setOnClickListener(null);
            countdownView.setText(Logic.countdown(hub.codeExpiresAt, now) + " 后失效");
        } else {
            codeView.setText(hub.serverOnline ? "点我生成" : "——");
            codeView.setTextColor(hub.serverOnline ? Ui.PINK_DEEP : Ui.MUTED);
            codeView.setOnClickListener(v -> hub.requestCode());
            countdownView.setText(hub.code.isEmpty() ? "" : "已失效");
            if (hub.serverOnline && hub.code.isEmpty() && !askedCode) { askedCode = true; hub.requestCode(); }
        }
    }
    private boolean askedCode;

    private void copyCode() {
        if (hub.code.isEmpty()) { hub.requestCode(); return; }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("配对码", hub.code));
        onToast("已复制 " + hub.code);
    }

    private void shareCode() {
        if (hub.code.isEmpty()) { hub.requestCode(); return; }
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "我的心跳配对码：" + hub.code + "（5 分钟内有效）");
        startActivity(Intent.createChooser(i, "把配对码发给 TA"));
    }

    private void hideKeyboard(View v) {
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        im.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    // ===================================================================== home
    private LinearLayout homeScreen() {
        LinearLayout c = ui.col();
        JSONObject p = hub.partner();
        String pName = p.optString("name", "TA");
        long now = System.currentTimeMillis();

        LinearLayout hd = ui.row();
        hd.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 24, 16, 24, 0));
        hd.addView(ui.text("💗 心跳 Beat", 26, Ui.TEXT, true), ui.weight(1));
        long days = Logic.daysTogether(hub.pairedAt(), now);
        TextView d = ui.pill("在一起 " + days + " 天", 13, Ui.PINK_DEEP, Ui.PINK_TINT, 30);
        hd.addView(d);
        c.addView(hd);

        if (!hub.serverOnline) {
            TextView off = ui.text(hub.serverUrl().isEmpty() ? "还没设置服务器，去「设置」填写" : "正在连接服务器…（消息会先存着）", 13, Ui.TIP, false);
            off.setBackground(ui.roundCss(Ui.LAV_TINT, 14));
            ui.pad(off, 14, 8, 14, 8);
            off.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 24, 12, 24, 0));
            c.addView(off);
        }

        LinearLayout duo = ui.row();
        duo.setGravity(Gravity.TOP);
        duo.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 24, 18, 24, 0));
        String pDev = p.optString("device");
        String pDevLine = "⌚ " + (pDev.isEmpty() ? "未知设备" : pDev) + " · " + Logic.modeLabel(p.optString("mode", "notify"));
        duo.addView(statusCard(true, pName, p.optBoolean("online"), p.optString("mood"), p.optString("text"),
            p.optLong("updatedAt"), pDevLine, now, p.optBoolean("wearing")), new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
        String myDev = hub.wear.hasNode() ? "⌚ " + hub.wear.deviceName() + " · 已连接" : "⌚ 未连接手环";
        LinearLayout me = statusCard(false, "我", hub.serverOnline, hub.myMood(), hub.myMoodText(), hub.myMoodAt(), myDev, now, hub.wear.wearing());
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, Ui.MATCH, 1);
        mp.leftMargin = ui.px(12);
        duo.addView(me, mp);
        c.addView(duo);

        TextView miss = ui.pill("💗  想你了", 22, Color.WHITE, Ui.PINK, 72);
        miss.setElevation(ui.px(8));
        miss.setLayoutParams(ui.lp(Ui.MATCH, ui.px(72), 24, 16, 24, 0));
        miss.setOnClickListener(v -> { hub.sendMiss("short", "phone"); pulse(v); });
        miss.setOnLongClickListener(v -> { hub.sendMiss("long", "phone"); pulse(v); onToast("好想好想你 💞"); return true; });
        c.addView(miss);
        TextView hint = ui.text("轻点：振一下　长按：持续振动", 12, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        hint.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        c.addView(hint);

        LinearLayout quick = ui.row();
        quick.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 24, 12, 24, 0));
        String[] qs = {"😊 改心情", "💬 发短语", "👉 戳三下"};
        for (int i = 0; i < 3; i++) {
            final int q = i;
            TextView chip = ui.text(qs[i], 14, Ui.TEXT, false);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(ui.stroke(Color.WHITE, 999, 1.5f, Ui.LINE));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ui.px(40), 1);
            if (i > 0) cp.leftMargin = ui.px(8);
            chip.setOnClickListener(v -> {
                if (q == 0) moodDialog();
                else if (q == 1) phraseDialog();
                else { hub.sendPoke("phone"); pulse(v); }
            });
            quick.addView(chip, cp);
        }
        c.addView(quick);

        LinearLayout tlh = ui.row();
        tlh.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 24, 24, 24, 0));
        TextView sec = ui.text("最近互动", 15, Ui.MUTED, true);
        tlh.addView(sec, ui.weight(1));
        TextView all = ui.text("全部 ›", 13, Ui.LAV_DEEP, false);
        all.setOnClickListener(v -> { tab = TAB_LOG; render(); });
        tlh.addView(all);
        c.addView(tlh);
        LinearLayout items = timeline(6, false);
        items.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 24, 10, 24, 0));
        c.addView(items);
        return c;
    }

    private void pulse(View v) {
        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).withEndAction(() ->
            v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();
    }

    private LinearLayout statusCard(boolean ta, String who, boolean online, String mood, String text, long at,
                                    String dev, long now, boolean wearing) {
        LinearLayout c = ui.card();
        c.setBackground(ta ? ui.gradient(0xFFFFE3EE, 0xFFEEE6FF, 24) : ui.roundCss(Color.WHITE, 24));
        ui.pad(c, 18, 18, 18, 18);
        LinearLayout w = ui.row();
        w.addView(ui.dot(online ? Ui.ONLINE : Ui.OFFLINE, 8));
        TextView wt = ui.ellipsize(ui.text(who + " · " + (online ? "在线" : "离线"), 13, Ui.MUTED, false));
        LinearLayout.LayoutParams wp = ui.lp(Ui.WRAP, Ui.WRAP);
        wp.leftMargin = ui.px(6);
        w.addView(wt, wp);
        c.addView(w);
        boolean has = Logic.isMood(mood);
        TextView em = ui.text(has ? Logic.emoji(mood) : "🫧", 48, Ui.TEXT, false);
        em.setLayoutParams(ui.lp(Ui.WRAP, Ui.WRAP, 0, 10, 0, 6));
        c.addView(em);
        c.addView(ui.text(has ? Logic.moodLabel(mood) : "还没设置心情", 19, Ui.TEXT, true));
        if (text != null && !text.isEmpty()) {
            TextView tx = ui.text(text, 14, Ui.TEXT, false);
            tx.setAlpha(0.8f);
            tx.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
            c.addView(tx);
        }
        String time = at > 0 ? Logic.timeAgo(at, now) : "";
        if (wearing) time = time.isEmpty() ? "戴着手环" : time + " · 戴着手环";
        TextView t = ui.text(time, 12, Ui.MUTED, false);
        t.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        c.addView(t);
        TextView dv = ui.ellipsize(ui.text(dev, 11, Ui.MUTED, false));
        dv.setBackground(ui.roundCss(ta ? 0xB3FFFFFF : Ui.BG, 999));
        ui.pad(dv, 10, 4, 10, 4);
        dv.setLayoutParams(ui.lp(Ui.WRAP, Ui.WRAP, 0, 10, 0, 0));
        c.addView(dv);
        if (!ta) c.setOnClickListener(v -> moodDialog());
        return c;
    }

    private LinearLayout timeline(int max, boolean dayHeaders) {
        LinearLayout box = ui.col();
        JSONArray h = hub.history();
        String pName = hub.partner().optString("name", "TA");
        if (h.length() == 0) {
            LinearLayout card = ui.card();
            ui.pad(card, 18, 22, 18, 22);
            TextView e = ui.text("还没有互动，先给 TA 发一个「想你了」吧 💗", 14, Ui.MUTED, false);
            e.setGravity(Gravity.CENTER);
            card.addView(e);
            box.addView(card);
            return box;
        }
        SimpleDateFormat dayFmt = new SimpleDateFormat("M 月 d 日 EEEE", Locale.CHINA);
        String lastDay = null;
        LinearLayout card = null;
        int shown = 0;
        for (int i = h.length() - 1; i >= 0 && (max <= 0 || shown < max); i--, shown++) {
            JSONObject e = h.optJSONObject(i);
            if (e == null) continue;
            String day = dayFmt.format(new Date(e.optLong("ts")));
            if (card == null || (dayHeaders && !day.equals(lastDay))) {
                if (dayHeaders) {
                    TextView dh = ui.text(day, 13, Ui.MUTED, true);
                    dh.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 4, card == null ? 0 : 18, 0, 8));
                    box.addView(dh);
                }
                card = ui.card();
                ui.pad(card, 18, 6, 18, 6);
                box.addView(card);
                lastDay = day;
            } else {
                card.addView(ui.divider());
            }
            card.addView(timelineItem(e, pName));
        }
        return box;
    }

    private View timelineItem(JSONObject e, String pName) {
        boolean mine = e.optBoolean("mine");
        String kind = e.optString("kind");
        LinearLayout it = ui.row();
        ui.pad(it, 0, 13, 0, 13);
        TextView ic = ui.text(Logic.eventIcon(kind, e.optString("level"), e.optString("mood"), e.optString("e")), 22, Ui.TEXT, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(ui.oval(mine ? Ui.LAV_TINT : Ui.PINK_TINT));
        it.addView(ic, ui.lp(ui.px(42), ui.px(42)));
        LinearLayout b = ui.col();
        b.addView(ui.text(Logic.timelineMain(mine, pName, kind, e.optString("level"), e.optString("mood"), e.optString("text")), 15, Ui.TEXT, false));
        b.addView(ui.text(Logic.timelineSub(mine, kind, e.optString("text"), e.optString("via"), e.optBoolean("delivered")), 12, Ui.MUTED, false));
        LinearLayout.LayoutParams bp = ui.weight(1);
        bp.leftMargin = ui.px(12);
        it.addView(b, bp);
        it.addView(ui.text(Logic.clock(e.optLong("ts")), 12, Ui.MUTED, false));
        return it;
    }

    // ===================================================================== dialogs
    private AlertDialog sheet(View content) {
        AlertDialog d = new AlertDialog.Builder(this).setView(content).create();
        d.show();
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(ui.roundCss(Ui.BG, 28));
        return d;
    }

    private void moodDialog() {
        LinearLayout c = ui.col();
        ui.pad(c, 22, 22, 22, 18);
        c.addView(ui.text("现在的心情", 20, Ui.TEXT, true));
        GridLayout g = new GridLayout(this);
        g.setColumnCount(4);
        final String[] chosen = {Logic.isMood(hub.myMood()) ? hub.myMood() : ""};
        final TextView[] tiles = new TextView[Logic.MOOD_IDS.length];
        Runnable paint = () -> {
            for (int i = 0; i < tiles.length; i++) {
                boolean on = Logic.MOOD_IDS[i].equals(chosen[0]);
                tiles[i].setBackground(on ? ui.stroke(Color.WHITE, 18, 2.5f, Ui.LAV) : ui.roundCss(Color.WHITE, 18));
            }
        };
        int cell = (getResources().getDisplayMetrics().widthPixels - ui.px(230)) / 4;
        for (int i = 0; i < Logic.MOOD_IDS.length; i++) {
            final String id = Logic.MOOD_IDS[i];
            TextView t = ui.text(Logic.emoji(id) + "\n" + Logic.MOOD_LABELS[i], 14, Ui.TEXT, false);
            t.setGravity(Gravity.CENTER);
            t.setLineSpacing(0, 1.2f);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cell;
            lp.height = cell;
            lp.setMargins(ui.px(4), ui.px(4), ui.px(4), ui.px(4));
            t.setOnClickListener(v -> { chosen[0] = id; paint.run(); });
            tiles[i] = t;
            g.addView(t, lp);
        }
        paint.run();
        g.setLayoutParams(ui.lp(Ui.WRAP, Ui.WRAP, 0, 14, 0, 0));
        c.addView(g);
        EditText note = input("附言（可不填）", hub.myMoodText(), 20);
        note.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 14, 0, 0));
        c.addView(note);
        TextView ok = ui.pill("告诉 TA", 17, Color.WHITE, Ui.PINK, 52);
        ok.setLayoutParams(ui.lp(Ui.MATCH, ui.px(52), 0, 16, 0, 0));
        c.addView(ok);
        AlertDialog d = sheet(c);
        ok.setOnClickListener(v -> {
            if (chosen[0].isEmpty()) { onToast("先选一个心情"); return; }
            hub.sendMood(chosen[0], note.getText().toString().trim(), "phone");
            d.dismiss();
        });
    }

    private void phraseDialog() {
        LinearLayout c = ui.col();
        ui.pad(c, 22, 22, 22, 18);
        c.addView(ui.text("发一句", 20, Ui.TEXT, true));
        LinearLayout list = ui.card();
        ui.pad(list, 18, 4, 18, 4);
        list.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 14, 0, 0));
        JSONArray ph = hub.phrases();
        final AlertDialog[] dd = new AlertDialog[1];
        for (int i = 0; i < ph.length(); i++) {
            JSONObject p = ph.optJSONObject(i);
            if (i > 0) list.addView(ui.divider());
            TextView t = ui.text(Logic.emoji(p.optString("e")) + "  " + p.optString("text"), 16, Ui.TEXT, false);
            ui.pad(t, 0, 14, 0, 14);
            t.setOnClickListener(v -> { hub.sendPhrase(p.optString("e"), p.optString("text"), "phone"); dd[0].dismiss(); });
            list.addView(t);
        }
        c.addView(list);
        TextView more = ui.text("＋ 自定义一句", 15, Ui.PINK_DEEP, true);
        ui.pad(more, 4, 14, 4, 4);
        more.setOnClickListener(v -> { dd[0].dismiss(); addPhraseDialog(true); });
        c.addView(more);
        dd[0] = sheet(c);
    }

    private void addPhraseDialog(boolean sendNow) {
        LinearLayout c = ui.col();
        ui.pad(c, 22, 22, 22, 18);
        c.addView(ui.text(sendNow ? "写一句" : "添加短语", 20, Ui.TEXT, true));
        final String[] em = {"heart"};
        LinearLayout er = ui.row();
        er.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        final TextView[] ets = new TextView[Logic.PHRASE_EMOJI.length];
        Runnable paint = () -> {
            for (int i = 0; i < ets.length; i++)
                ets[i].setBackground(Logic.PHRASE_EMOJI[i].equals(em[0]) ? ui.oval(Ui.PINK_TINT) : null);
        };
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        for (int i = 0; i < Logic.PHRASE_EMOJI.length; i++) {
            final String k = Logic.PHRASE_EMOJI[i];
            TextView t = ui.text(Logic.emoji(k), 24, Ui.TEXT, false);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> { em[0] = k; paint.run(); });
            ets[i] = t;
            er.addView(t, ui.lp(ui.px(48), ui.px(48)));
        }
        paint.run();
        hs.addView(er);
        c.addView(hs);
        EditText tx = input("最多 " + Logic.MAX_PHRASE_LEN + " 个字", "", Logic.MAX_PHRASE_LEN);
        tx.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        c.addView(tx);
        TextView ok = ui.pill(sendNow ? "发送" : "保存", 17, Color.WHITE, Ui.PINK, 52);
        ok.setLayoutParams(ui.lp(Ui.MATCH, ui.px(52), 0, 16, 0, 0));
        c.addView(ok);
        AlertDialog d = sheet(c);
        ok.setOnClickListener(v -> {
            String t = Logic.cleanPhrase(tx.getText().toString());
            if (t == null) { onToast("写点什么吧"); return; }
            if (sendNow) hub.sendPhrase(em[0], t, "phone");
            else if (!hub.addPhrase(em[0], t)) { onToast("最多 " + Logic.MAX_PHRASES + " 条"); return; }
            d.dismiss();
        });
    }

    private EditText input(String hint, String value, int max) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(0xFFBBAEC6);
        e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, ui.px(16));
        if (max > 0) e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        e.setBackground(ui.stroke(Color.WHITE, 16, 1.5f, Ui.LINE));
        ui.pad(e, 16, 12, 16, 12);
        return e;
    }

    private void editProfile() {
        LinearLayout c = ui.col();
        ui.pad(c, 22, 22, 22, 18);
        c.addView(ui.text("我的昵称和头像", 20, Ui.TEXT, true));
        EditText name = input("昵称（TA 看到的名字）", hub.myName(), 10);
        name.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 14, 0, 0));
        c.addView(name);
        final String[] av = {hub.myAvatar()};
        LinearLayout r = ui.row();
        r.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        final TextView[] ts = new TextView[Logic.AVATARS.length];
        Runnable paint = () -> {
            for (int i = 0; i < ts.length; i++)
                ts[i].setBackground(Logic.AVATARS[i].equals(av[0]) ? ui.oval(Ui.PINK_TINT) : null);
        };
        for (int i = 0; i < Logic.AVATARS.length; i++) {
            final String k = Logic.AVATARS[i];
            TextView t = ui.text(Logic.emoji(k), 28, Ui.TEXT, false);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> { av[0] = k; paint.run(); });
            ts[i] = t;
            r.addView(t, new LinearLayout.LayoutParams(0, ui.px(56), 1));
        }
        paint.run();
        c.addView(r);
        TextView ok = ui.pill("保存", 17, Color.WHITE, Ui.PINK, 52);
        ok.setLayoutParams(ui.lp(Ui.MATCH, ui.px(52), 0, 16, 0, 0));
        c.addView(ok);
        AlertDialog d = sheet(c);
        ok.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            hub.setProfile(n.isEmpty() ? "我" : n, av[0]);
            d.dismiss();
        });
    }

    private void editServer() {
        LinearLayout c = ui.col();
        ui.pad(c, 22, 22, 22, 18);
        c.addView(ui.text("服务器地址", 20, Ui.TEXT, true));
        TextView tip = ui.text("你们两个人要填同一个地址，例如 wss://beat.example.com/ws", 13, Ui.MUTED, false);
        tip.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        c.addView(tip);
        EditText url = input("wss://…/ws", hub.serverUrl(), 200);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 14, 0, 0));
        c.addView(url);
        TextView ok = ui.pill("保存并连接", 17, Color.WHITE, Ui.PINK, 52);
        ok.setLayoutParams(ui.lp(Ui.MATCH, ui.px(52), 0, 16, 0, 0));
        c.addView(ok);
        AlertDialog d = sheet(c);
        ok.setOnClickListener(v -> {
            String u = url.getText().toString().trim();
            if (!u.isEmpty() && !u.startsWith("ws://") && !u.startsWith("wss://")) { onToast("要以 wss:// 开头"); return; }
            hub.code = "";
            askedCode = false;
            hub.setServerUrl(u);
            d.dismiss();
        });
    }

    // ===================================================================== records
    private LinearLayout logScreen() {
        LinearLayout c = ui.col();
        c.addView(title("记录"));
        LinearLayout items = timeline(0, true);
        items.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 24, 18, 24, 0));
        c.addView(items);
        return c;
    }

    // ===================================================================== settings
    private LinearLayout settingsScreen() {
        LinearLayout c = ui.col();
        c.addView(title("设置"));
        LinearLayout w = ui.col();
        w.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 24, 18, 24, 0));
        c.addView(w);
        JSONObject p = hub.partner();
        String pName = p.optString("name", "TA");

        // ---- partner device
        w.addView(ui.section(pName + " 的设备"));
        String pDev = p.optString("device");
        boolean pCan = Logic.canRunQuickApp(pDev);
        w.addView(deviceRow(pDev.isEmpty() ? "还不知道 TA 的设备" : pDev,
            pDev.isEmpty() ? "TA 打开 App 后会自动识别" : pCan ? "已识别 · 可以装「心跳」手表 App" : "已识别 · 不支持第三方应用",
            null, null));
        String pMode = p.optString("mode", "notify");
        w.addView(modeOption("notify", pMode, "适用 Redmi Watch 3 / 4。消息以手机通知的形式同步到手表，会振动、显示文字和表情；表上不能直接回复。",
            m -> hub.setPartnerMode(m)));
        w.addView(modeOption("full", pMode, "适用 Redmi Watch 5 / 6、小米手环 9 及以上。表上装「心跳」，实时收发心情、振动和短语。",
            m -> hub.setPartnerMode(m)));

        // ---- my device
        w.addView(spacer(14));
        w.addView(ui.section("我的设备"));
        String myDev = hub.wear.hasNode() ? hub.wear.deviceName() : "没找到手环";
        String sub;
        if (!hub.wear.hasNode()) sub = hub.wear.lastError.isEmpty() ? "请先在小米运动健康里连接手环" : hub.wear.lastError;
        else if (!hub.wear.permitted()) sub = "需要授权才能和手环通信";
        else sub = (hub.wear.appInstalled() ? "已装「心跳」" : "手环上还没装「心跳」") + (hub.wear.wearing() ? " · 佩戴中" : "");
        w.addView(deviceRow(myDev, sub, hub.wear.hasNode() && !hub.wear.permitted() ? "去授权" : "重新检测", v -> {
            if (hub.wear.hasNode() && !hub.wear.permitted()) hub.wear.requestPermission();
            else { hub.redetect(); onToast("正在检测…"); }
        }));
        String myMode = hub.mode();
        w.addView(modeOption("notify", myMode, "我手腕上用手机通知提醒（手表不能装 App 时用）。", m -> hub.setMode(m, true)));
        w.addView(modeOption("full", myMode, "我的手环上装了「心跳」，直接在表上弹出、振动和回复。", m -> hub.setMode(m, true)));

        // ---- phrases
        w.addView(spacer(14));
        LinearLayout sh = ui.row();
        sh.addView(ui.text("自定义短语", 15, Ui.MUTED, true));
        sh.addView(ui.text("  · 同步到手环，最多 " + Logic.MAX_PHRASES + " 条", 12, Ui.MUTED, false));
        sh.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 4, 0, 0, 10));
        w.addView(sh);
        LinearLayout phr = ui.card();
        ui.pad(phr, 18, 8, 18, 8);
        JSONArray ph = hub.phrases();
        for (int i = 0; i < ph.length(); i++) {
            final int idx = i;
            JSONObject o = ph.optJSONObject(i);
            LinearLayout r = ui.row();
            ui.pad(r, 0, 11, 0, 11);
            TextView e = ui.text(Logic.emoji(o.optString("e")), 20, Ui.TEXT, false);
            r.addView(e, ui.lp(ui.px(34), Ui.WRAP));
            r.addView(ui.text(o.optString("text"), 15, Ui.TEXT, false), ui.weight(1));
            TextView del = ui.text("✕", 15, 0xFFCDBFD8, false);
            ui.pad(del, 12, 0, 4, 0);
            del.setOnClickListener(v -> {
                if (hub.phrases().length() <= 1) { onToast("至少留一条"); return; }
                hub.removePhrase(idx);
            });
            r.addView(del);
            phr.addView(r);
            phr.addView(ui.divider());
        }
        LinearLayout add = ui.row();
        ui.pad(add, 0, 13, 0, 6);
        add.addView(ui.text("＋ 添加短语", 15, Ui.PINK_DEEP, true));
        add.addView(ui.text("  已用 " + ph.length() + " / " + Logic.MAX_PHRASES, 12, Ui.MUTED, false));
        add.setOnClickListener(v -> {
            if (hub.phrases().length() >= Logic.MAX_PHRASES) { onToast("最多 " + Logic.MAX_PHRASES + " 条"); return; }
            addPhraseDialog(false);
        });
        phr.addView(add);
        w.addView(phr);

        // ---- toggles
        w.addView(spacer(14));
        LinearLayout box = ui.card();
        box.addView(settingRow("睡眠时免打扰", "23:30 – 07:30 手机静默；手环仍显示但不振", toggle(hub.quietEnabled()), v -> hub.setQuiet(!hub.quietEnabled())));
        box.addView(ui.divider());
        box.addView(settingRow("保持后台运行", "关闭电池优化、允许自启动", link(batteryOk() ? "已设置 ›" : "去设置 ›"), v -> keepAlive()));
        box.addView(ui.divider());
        box.addView(settingRow("我的昵称和头像", hub.myName() + " " + Logic.emoji(hub.myAvatar()), link("修改 ›"), v -> editProfile()));
        box.addView(ui.divider());
        String url = hub.serverUrl();
        box.addView(settingRow("服务器", url.isEmpty() ? "未设置" : url, serverStatus(), v -> editServer()));
        w.addView(box);

        w.addView(spacer(14));
        LinearLayout danger = ui.card();
        danger.addView(settingRow("解除配对", "双方的记录都会清空", link("解除 ›"), v ->
            new AlertDialog.Builder(this).setTitle("确定解除配对？").setMessage("解除后需要重新输入配对码。")
                .setPositiveButton("解除", (dlg, which) -> hub.unpair()).setNegativeButton("再想想", null).show()));
        w.addView(danger);

        TextView ver = ui.text("心跳 Beat " + versionName() + " · 数据只在你们两人之间同步", 12, Ui.MUTED, false);
        ver.setGravity(Gravity.CENTER);
        ver.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 20, 0, 0));
        w.addView(ver);
        return c;
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    private View deviceRow(String name, String sub, String action, View.OnClickListener l) {
        LinearLayout d = ui.card();
        d.setOrientation(LinearLayout.HORIZONTAL);
        d.setGravity(Gravity.CENTER_VERTICAL);
        ui.pad(d, 18, 14, 18, 14);
        TextView ic = ui.text("⌚", 24, Ui.TEXT, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(ui.roundCss(Ui.LAV_TINT, 14));
        d.addView(ic, ui.lp(ui.px(44), ui.px(44)));
        LinearLayout b = ui.col();
        b.addView(ui.ellipsize(ui.text(name, 16, Ui.TEXT, true)));
        b.addView(ui.text(sub, 12, Ui.MUTED, false));
        LinearLayout.LayoutParams bp = ui.weight(1);
        bp.leftMargin = ui.px(12);
        d.addView(b, bp);
        if (action != null) {
            TextView a = ui.text(action, 13, Ui.LAV_DEEP, false);
            a.setOnClickListener(l);
            ui.pad(a, 10, 10, 0, 10);
            d.addView(a);
        }
        d.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        return d;
    }

    interface ModePick { void pick(String m); }

    private View modeOption(String mode, String current, String desc, ModePick pick) {
        boolean on = mode.equals(current);
        LinearLayout o = ui.card();
        o.setOrientation(LinearLayout.HORIZONTAL);
        o.setBackground(on ? ui.stroke(0xFFFFF9FB, 24, 2, Ui.PINK) : ui.roundCss(Color.WHITE, 24));
        ui.pad(o, 18, 16, 18, 16);
        View radio = new View(this);
        android.graphics.drawable.GradientDrawable rd = ui.oval(Color.WHITE);
        rd.setStroke(ui.px(on ? 7 : 2), on ? Ui.PINK : 0xFFD9CCE2);
        radio.setBackground(rd);
        LinearLayout.LayoutParams rp = ui.lp(ui.px(22), ui.px(22));
        rp.topMargin = ui.px(2);
        o.addView(radio, rp);
        LinearLayout b = ui.col();
        LinearLayout h = ui.row();
        h.addView(ui.text(Logic.modeLabel(mode), 16, Ui.TEXT, true));
        boolean full = "full".equals(mode);
        TextView chip = ui.text(on ? "当前" : full ? "实时" : "省心", 11, full && !on ? Ui.LAV_DEEP : Ui.PINK_DEEP, false);
        chip.setBackground(ui.roundCss(full && !on ? Ui.LAV_TINT : Ui.PINK_TINT, 999));
        ui.pad(chip, 8, 2, 8, 2);
        LinearLayout.LayoutParams cp = ui.lp(Ui.WRAP, Ui.WRAP);
        cp.leftMargin = ui.px(6);
        h.addView(chip, cp);
        b.addView(h);
        TextView dt = ui.text(desc, 13, Ui.MUTED, false);
        dt.setLineSpacing(0, 1.3f);
        dt.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
        b.addView(dt);
        LinearLayout.LayoutParams bp = ui.weight(1);
        bp.leftMargin = ui.px(12);
        o.addView(b, bp);
        o.setOnClickListener(v -> { if (!on) pick.pick(mode); });
        o.setLayoutParams(ui.lp(Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        return o;
    }

    private View settingRow(String t, String sub, View right, View.OnClickListener l) {
        LinearLayout r = ui.row();
        ui.pad(r, 18, 14, 18, 14);
        LinearLayout b = ui.col();
        b.addView(ui.text(t, 15, Ui.TEXT, false));
        b.addView(ui.ellipsize(ui.text(sub, 12, Ui.MUTED, false)));
        r.addView(b, ui.weight(1));
        r.addView(right);
        r.setOnClickListener(l);
        return r;
    }

    private TextView link(String s) { return ui.text(s, 13, Ui.LAV_DEEP, false); }

    private View toggle(boolean on) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(ui.roundCss(on ? Ui.PINK : 0xFFE4DCE8, 999));
        View k = new View(this);
        k.setBackground(ui.oval(Color.WHITE));
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(ui.px(22), ui.px(22), Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START));
        kp.setMargins(ui.px(3), 0, ui.px(3), 0);
        f.addView(k, kp);
        f.setLayoutParams(ui.lp(ui.px(46), ui.px(28)));
        return f;
    }

    private boolean batteryOk() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void keepAlive() {
        if (!batteryOk()) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                return;
            } catch (Exception ignored) { }
        }
        // MIUI / HyperOS: autostart + battery saver live on the app-info page
        onToast("在「自启动」打开开关，「省电策略」选「无限制」");
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }
}
