package com.xintiao.beat;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tiny programmatic-UI kit. Sizes are given in the mockup's CSS px (540 px wide screen) and scaled. */
final class Ui {
    static final int PINK = 0xFFFF8FB8, PINK_DEEP = 0xFFE85A93, LAV = 0xFFB79CFF, LAV_DEEP = 0xFF7E5AE8;
    static final int PINK_TINT = 0xFFFFE8F0, LAV_TINT = 0xFFF0EAFF;
    static final int BG = 0xFFFFF7FB, CARD = 0xFFFFFFFF, TEXT = 0xFF2F2438, MUTED = 0xFF8A7B98, LINE = 0xFFF5EAF1;
    static final int ONLINE = 0xFF7EE0B5, OFFLINE = 0xFFD9CCE2, OK = 0xFF3FAE84, TIP = 0xFF6B5A82;
    static final int GLASS = 0xCCFFFFFF;
    static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT, WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    final Context ctx;
    final float k;

    Ui(Context c) {
        ctx = c;
        float w = c.getResources().getDisplayMetrics().widthPixels;
        k = w / 540f;
    }

    int px(float css) { return Math.round(css * k); }

    TextView text(CharSequence s, float size, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    TextView ellipsize(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    GradientDrawable roundCss(int color, float radiusCss) { return round(color, px(radiusCss)); }

    GradientDrawable stroke(int fill, float radiusCss, float widthCss, int strokeColor) {
        GradientDrawable d = roundCss(fill, radiusCss);
        d.setStroke(Math.max(1, px(widthCss)), strokeColor);
        return d;
    }

    GradientDrawable gradient(int a, int b, float radiusCss) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        d.setCornerRadius(px(radiusCss));
        return d;
    }

    GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    LinearLayout col() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** White rounded card with a soft pink–lavender shadow. */
    LinearLayout card() {
        LinearLayout l = col();
        l.setBackground(roundCss(CARD, 26));
        l.setElevation(px(7));
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            l.setOutlineSpotShadowColor(0x66FF8FB8);
            l.setOutlineAmbientShadowColor(0x44B79CFF);
        }
        return l;
    }

    /** Soft tip / guide panel. */
    LinearLayout tipCard() {
        LinearLayout l = col();
        l.setBackground(gradient(0xFFFFE8F0, 0xFFF0EAFF, 22));
        l.setElevation(px(2));
        return l;
    }

    LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    LinearLayout.LayoutParams lp(int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(px(l), px(t), px(r), px(b));
        return p;
    }

    LinearLayout.LayoutParams weight(float wgt) {
        return new LinearLayout.LayoutParams(0, WRAP, wgt);
    }

    void pad(View v, float l, float t, float r, float b) { v.setPadding(px(l), px(t), px(r), px(b)); }

    /** Pill button. */
    TextView pill(String s, float size, int fg, int bg, float hCss) {
        TextView t = text(s, size, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(roundCss(bg, 999));
        pad(t, 14, 0, 14, 0);
        t.setMinHeight(px(hCss));
        return t;
    }

    View dot(int color, float sizeCss) {
        View v = new View(ctx);
        v.setBackground(oval(color));
        v.setLayoutParams(lp(px(sizeCss), px(sizeCss)));
        return v;
    }

    View divider() {
        View v = new View(ctx);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(lp(MATCH, Math.max(1, px(1))));
        return v;
    }

    TextView section(String s) {
        TextView t = text(s, 15, MUTED, true);
        t.setLayoutParams(lp(MATCH, WRAP, 4, 0, 0, 10));
        return t;
    }
}
