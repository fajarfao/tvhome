package com.fajar.tvhome;

import java.util.Calendar;
import java.util.TimeZone;

/** Perhitungan waktu sholat (Kemenag RI: Subuh 20, Isya 18, Ashar mazhab Syafii). Tanpa internet. */
public class Prayer {
    public static final String[] NAMES = {"Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya"};

    static double fix(double h) {
        h = h % 24;
        if (h < 0) h += 24;
        return h;
    }

    /** Jam (WIB, desimal) untuk Subuh, Dzuhur, Ashar, Maghrib, Isya pada tanggal tersebut. */
    public static double[] times(int y, int mo, int da, double lat, double lng, double off) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(y, mo - 1, da, 12, 0, 0);
        double jd = c.getTimeInMillis() / 86400000.0 + 2440587.5;
        double R = Math.PI / 180;
        double D = jd - 2451545;
        double g = (357.529 + 0.98560028 * D) * R;
        double q = ((280.459 + 0.98564736 * D) % 360 + 360) % 360;
        double L = (q + 1.915 * Math.sin(g) + 0.02 * Math.sin(2 * g)) * R;
        double e = (23.439 - 4e-7 * D) * R;
        double ra = Math.atan2(Math.cos(e) * Math.sin(L), Math.cos(L)) / R / 15;
        double d = Math.asin(Math.sin(e) * Math.sin(L));
        double eq = q / 15 - fix(ra);
        if (eq > 12) eq -= 24;
        if (eq < -12) eq += 24;
        double dh = 12 + off - lng / 15 - eq;
        double la = lat * R;
        double ih = 2.0 / 60;
        double A = Math.atan(1 / (1 + Math.tan(Math.abs(la - d))));
        double asr = Math.acos((Math.sin(A) - Math.sin(d) * Math.sin(la)) / (Math.cos(d) * Math.cos(la))) / R / 15;
        return new double[]{
                dh - t(20, d, la) + ih,
                dh + ih,
                dh + asr + ih,
                dh + t(0.833, d, la) + ih,
                dh + t(18, d, la) + ih
        };
    }

    static double t(double a, double d, double la) {
        double R = Math.PI / 180;
        return Math.acos((-Math.sin(a * R) - Math.sin(d) * Math.sin(la)) / (Math.cos(d) * Math.cos(la))) / R / 15;
    }

    public static String hm(double h) {
        int m = (int) Math.round(fix(h) * 60) % 1440;
        return String.format("%02d:%02d", m / 60, m % 60);
    }
}
