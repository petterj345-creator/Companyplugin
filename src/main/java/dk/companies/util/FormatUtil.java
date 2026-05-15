package dk.companies.util;

import java.text.DecimalFormat;

public final class FormatUtil {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private FormatUtil() {}

    public static String money(double v) { return "$" + MONEY.format(v); }

    public static String duration(long millis) {
        if (millis <= 0) return "expired";
        long s = millis / 1000L;
        long d = s / 86400L;  s %= 86400L;
        long h = s / 3600L;   s %= 3600L;
        long m = s / 60L;     s %= 60L;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    public static String remaining(long until) {
        return duration(until - System.currentTimeMillis());
    }
}
