package com.fajar.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class MainActivity extends Activity {

    // ====== PENGATURAN (ubah di sini kalau perlu) ======
    static final String DATA_URL = "https://fajarfao.github.io/tv/data.json";
    static final double LAT = -6.2383, LNG = 106.9756; // Bekasi
    static final double UTC_OFFSET = 7;                 // WIB
    // Aplikasi yang ditaruh paling depan di dock (urut sesuai daftar)
    static final String[] FAV = {
            "com.phlox.tvwebbrowser",
            "com.google.android.youtube.tv",
            "com.spotify.tv.android"
    };
    // ====================================================

    static final int[] BAND_H = {0, 4, 6, 10, 15, 17, 19, 24};
    static final int[] BAND_C = {0xFF1B1F4A, 0xFF5A3A63, 0xFFB0654A, 0xFF2F6F9F,
            0xFFA8641F, 0xFFB5452E, 0xFF1B1F4A, 0xFF1B1F4A};

    final Handler handler = new Handler();
    SharedPreferences prefs;
    float u; // 1 unit = tinggi layar / 540

    GradientDrawable bg;
    TextView clock, date, nextPrayer, mnOpp, mnSub, mnCount, mnList, status;
    TextView[] pTime = new TextView[5];
    TextView[] pName = new TextView[5];
    LinearLayout appRow;
    HorizontalScrollView appScroll;

    JSONArray fixtures;
    String updated = "";
    long lastLoad = 0;
    int lastMinute = -1;
    int appCount = -1;

    // ---------- util ----------
    int px(float n) { return (int) (n * u + 0.5f); }

    TextView tv(String text, float size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static String dur(long s) {
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + " hari " + h + " jam";
        if (h > 0) return h + " jam " + m + " menit";
        return m + " menit";
    }

    GradientDrawable card() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x24FFFFFF);
        g.setCornerRadius(px(18));
        return g;
    }

    // ---------- siklus hidup ----------
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        u = getResources().getDisplayMetrics().heightPixels / 540f;
        prefs = getSharedPreferences("tvhome", MODE_PRIVATE);
        getWindow().setBackgroundDrawable(new ColorDrawable(0xFF14172F));
        setContentView(buildUi());
        String cached = prefs.getString("data", null);
        if (cached != null) apply(cached, false);
        buildApps();
        lastMinute = -1;
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildApps();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        if (System.currentTimeMillis() - lastLoad > 60000) load();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (appRow != null && appRow.getChildCount() > 0) {
            appScroll.smoothScrollTo(0, 0);
            appRow.getChildAt(0).requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        // Ini layar beranda: tombol Back tidak melakukan apa-apa.
    }

    // ---------- tampilan ----------
    View buildUi() {
        FrameLayout root = new FrameLayout(this);
        bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1B1F4A, 0xFF0C0E24});
        root.setBackground(bg);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(48), px(36), px(48), px(20));
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // --- kolom kiri: jam + sholat ---
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        top.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        clock = tv("--:--", 104, Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        left.addView(clock);

        date = tv("", 18, 0xCCFFFFFF);
        date.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = px(6);
        left.addView(date, dlp);

        View sp = new View(this);
        left.addView(sp, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout pc = new LinearLayout(this);
        pc.setOrientation(LinearLayout.VERTICAL);
        pc.setBackground(card());
        pc.setPadding(px(20), px(16), px(20), px(16));
        TextView pl = tv("WAKTU SHOLAT · BEKASI", 11, 0xA6FFFFFF);
        pl.setLetterSpacing(0.15f);
        pc.addView(pl);

        LinearLayout prow = new LinearLayout(this);
        prow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams prlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        prlp.topMargin = px(12);
        pc.addView(prow, prlp);
        for (int i = 0; i < 5; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            pTime[i] = tv("--:--", 24, Color.WHITE);
            pTime[i].setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            pName[i] = tv(Prayer.NAMES[i], 12, Color.WHITE);
            col.addView(pTime[i]);
            col.addView(pName[i]);
            prow.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        nextPrayer = tv("", 14, 0xCCFFFFFF);
        LinearLayout.LayoutParams nplp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nplp.topMargin = px(12);
        pc.addView(nextPrayer, nplp);
        left.addView(pc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- kolom kanan: jadwal Barca ---
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setBackground(card());
        right.setPadding(px(24), px(20), px(24), px(20));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rlp.leftMargin = px(36);
        top.addView(right, rlp);

        TextView ml = tv("BARÇA · PERTANDINGAN BERIKUTNYA", 11, 0xA6FFFFFF);
        ml.setLetterSpacing(0.15f);
        right.addView(ml);

        mnOpp = tv("Menunggu data…", 38, Color.WHITE);
        mnOpp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.topMargin = px(18);
        right.addView(mnOpp, olp);

        mnSub = tv("", 16, 0xD9FFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = px(8);
        right.addView(mnSub, slp);

        mnCount = tv("", 16, 0xFFFFFFFF);
        mnCount.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = px(6);
        right.addView(mnCount, clp);

        TextView nl = tv("SETELAHNYA", 11, 0xA6FFFFFF);
        nl.setLetterSpacing(0.15f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = px(26);
        right.addView(nl, nlp);

        mnList = tv("", 15, 0xE6FFFFFF);
        mnList.setLineSpacing(px(6), 1f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = px(10);
        right.addView(mnList, llp);

        status = tv("", 11, 0x80FFFFFF);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stlp.topMargin = px(14);
        right.addView(status, stlp);

        // --- dock aplikasi ---
        appScroll = new HorizontalScrollView(this);
        appScroll.setHorizontalScrollBarEnabled(false);
        appRow = new LinearLayout(this);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setPadding(0, px(8), 0, px(4));
        appScroll.addView(appRow);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(104));
        alp.topMargin = px(14);
        page.addView(appScroll, alp);

        return root;
    }

    // ---------- dock aplikasi ----------
    int favIndex(String pkg) {
        for (int i = 0; i < FAV.length; i++) if (FAV[i].equals(pkg)) return i;
        return FAV.length;
    }

    void buildApps() {
        final PackageManager pm = getPackageManager();
        List<ResolveInfo> all = new ArrayList<ResolveInfo>();
        all.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        all.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));

        Map<String, ResolveInfo> uniq = new LinkedHashMap<String, ResolveInfo>();
        for (ResolveInfo ri : all) {
            String p = ri.activityInfo.packageName;
            if (p.equals(getPackageName())) continue;
            if (!uniq.containsKey(p)) uniq.put(p, ri);
        }
        if (uniq.size() == appCount) return;
        appCount = uniq.size();

        final Map<String, String> labels = new HashMap<String, String>();
        List<ResolveInfo> list = new ArrayList<ResolveInfo>(uniq.values());
        for (ResolveInfo ri : list) {
            labels.put(ri.activityInfo.packageName, ri.loadLabel(pm).toString());
        }
        Collections.sort(list, new Comparator<ResolveInfo>() {
            public int compare(ResolveInfo a, ResolveInfo b) {
                String pa = a.activityInfo.packageName, pb = b.activityInfo.packageName;
                int fa = favIndex(pa), fb = favIndex(pb);
                if (fa != fb) return fa - fb;
                return labels.get(pa).compareToIgnoreCase(labels.get(pb));
            }
        });

        appRow.removeAllViews();
        for (ResolveInfo ri : list) {
            appRow.addView(makeTile(pm, ri, labels.get(ri.activityInfo.packageName)));
        }
        if (appRow.getChildCount() > 0) appRow.getChildAt(0).requestFocus();
    }

    View makeTile(final PackageManager pm, ResolveInfo ri, String label) {
        final String pkg = ri.activityInfo.packageName;
        final LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setPadding(px(8), px(8), px(8), px(6));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(ri.loadIcon(pm));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon, new LinearLayout.LayoutParams(px(54), px(54)));

        TextView t = tv(label, 11, Color.WHITE);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                px(86), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = px(6);
        tile.addView(t, tlp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                px(96), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = px(10);
        tile.setLayoutParams(lp);

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(px(14));
                g.setColor(has ? 0x40FFFFFF : 0x00000000);
                v.setBackground(g);
                v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(120).start();
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (li == null) li = pm.getLaunchIntentForPackage(pkg);
                if (li != null) {
                    li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(li);
                }
            }
        });
        return tile;
    }

    // ---------- jam, sholat, pertandingan ----------
    final Runnable ticker = new Runnable() {
        public void run() {
            tick();
            handler.postDelayed(this, 1000);
        }
    };

    void tick() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        int hh = c.get(Calendar.HOUR_OF_DAY), mm = c.get(Calendar.MINUTE), ss = c.get(Calendar.SECOND);
        clock.setText(String.format(Locale.US, "%02d:%02d", hh, mm));
        double cur = hh + mm / 60.0 + ss / 3600.0;

        // hitung mundur sholat tiap detik
        updatePrayer(c, cur);

        if (mm != lastMinute) {
            lastMinute = mm;
            SimpleDateFormat df = new SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("id", "ID"));
            df.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));
            date.setText(df.format(new Date()));
            updateBackground(cur);
            updateMatch();
        } else {
            updateMatchCountdown();
        }
        if (System.currentTimeMillis() - lastLoad > 30 * 60 * 1000L) load();
    }

    void updateBackground(double cur) {
        int i = 0;
        while (i < BAND_H.length - 2 && cur >= BAND_H[i + 1]) i++;
        double span = BAND_H[i + 1] - BAND_H[i];
        float f = (float) ((cur - BAND_H[i]) / span);
        int c1 = BAND_C[i], c2 = BAND_C[i + 1];
        int r = (int) (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * f);
        int g = (int) (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * f);
        int bl = (int) (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * f);
        int top = Color.rgb(r, g, bl);
        int bottom = Color.rgb((int) (r * 0.45), (int) (g * 0.45), (int) (bl * 0.45));
        bg.setColors(new int[]{top, bottom});
    }

    void updatePrayer(Calendar c, double cur) {
        int y = c.get(Calendar.YEAR), mo = c.get(Calendar.MONTH) + 1, da = c.get(Calendar.DAY_OF_MONTH);
        double[] p = Prayer.times(y, mo, da, LAT, LNG, UTC_OFFSET);
        int nx = -1;
        for (int i = 0; i < 5; i++) {
            if (p[i] > cur) { nx = i; break; }
        }
        for (int i = 0; i < 5; i++) {
            pTime[i].setText(Prayer.hm(p[i]));
            boolean on = (i == nx);
            pTime[i].setAlpha(on ? 1f : 0.6f);
            pName[i].setAlpha(on ? 1f : 0.6f);
            pName[i].setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
        double tgt;
        String nm;
        if (nx < 0) {
            double[] t2 = Prayer.times(y, mo, da + 1, LAT, LNG, UTC_OFFSET);
            tgt = t2[0] + 24;
            nm = "Subuh";
        } else {
            tgt = p[nx];
            nm = Prayer.NAMES[nx];
        }
        long s = Math.max(0, Math.round((tgt - cur) * 3600));
        nextPrayer.setText(nm + " dalam " + dur(s));
    }

    long[] fxTimes = new long[0];

    long parseIso(String iso) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(iso).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    int nextIndex() {
        if (fixtures == null) return -1;
        long now = System.currentTimeMillis();
        for (int i = 0; i < fixtures.length(); i++) {
            try {
                long t = parseIso(fixtures.getJSONArray(i).getString(0));
                if (t > now - 6000000L) return i;
            } catch (Exception e) { /* lewati */ }
        }
        return -1;
    }

    String line(JSONArray a) throws Exception {
        long t = parseIso(a.getString(0));
        boolean home = "vs".equals(a.getString(1));
        String comp = a.optString(3, "");
        SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM · HH:mm", new Locale("id", "ID"));
        df.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));
        return (home ? "vs " : "@ ") + a.getString(2) + "\n" + df.format(new Date(t)) + " WIB"
                + (comp.length() > 0 ? " · " + comp : "");
    }

    void updateMatch() {
        int n = nextIndex();
        if (n < 0) {
            mnOpp.setText("Belum ada jadwal");
            mnSub.setText("");
            mnCount.setText("");
            mnList.setText("");
            status.setText(updated.length() > 0 ? "Data: " + updated : "");
            return;
        }
        try {
            JSONArray a = fixtures.getJSONArray(n);
            boolean home = "vs".equals(a.getString(1));
            mnOpp.setText((home ? "vs " : "@ ") + a.getString(2));
            SimpleDateFormat df = new SimpleDateFormat("EEEE, d MMMM · HH:mm", new Locale("id", "ID"));
            df.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));
            String comp = a.optString(3, "");
            mnSub.setText(df.format(new Date(parseIso(a.getString(0)))) + " WIB"
                    + (home ? " · Kandang" : " · Tandang") + (comp.length() > 0 ? " · " + comp : ""));
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (int i = n + 1; i < fixtures.length() && shown < 3; i++, shown++) {
                if (shown > 0) sb.append("\n");
                sb.append(line(fixtures.getJSONArray(i)).replace("\n", "  ·  "));
            }
            mnList.setText(sb.toString());
        } catch (Exception e) {
            mnOpp.setText("Data tidak terbaca");
        }
        status.setText(updated.length() > 0 ? "Data: " + updated : "");
        updateMatchCountdown();
    }

    void updateMatchCountdown() {
        int n = nextIndex();
        if (n < 0) return;
        try {
            long t = parseIso(fixtures.getJSONArray(n).getString(0));
            long s = (t - System.currentTimeMillis()) / 1000;
            mnCount.setText(s > 0 ? dur(s) + " lagi" : "Sedang berlangsung");
        } catch (Exception e) { /* lewati */ }
    }

    // ---------- ambil data ----------
    void load() {
        lastLoad = System.currentTimeMillis();
        new Thread(new Runnable() {
            public void run() {
                try {
                    URL url = new URL(DATA_URL + "?t=" + System.currentTimeMillis());
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String ln;
                    while ((ln = r.readLine()) != null) sb.append(ln);
                    r.close();
                    apply(sb.toString(), true);
                } catch (Exception e) {
                    // gagal: tetap pakai data tersimpan
                }
            }
        }).start();
    }

    void apply(final String s, final boolean save) {
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    JSONObject o = new JSONObject(s);
                    fixtures = o.getJSONArray("fixtures");
                    updated = o.optString("updated", "");
                    if (save) prefs.edit().putString("data", s).apply();
                    updateMatch();
                } catch (Exception e) {
                    // data rusak: abaikan
                }
            }
        });
    }
}
