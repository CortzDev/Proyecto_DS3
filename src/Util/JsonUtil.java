package Util;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


public final class JsonUtil {

    private JsonUtil() {}

    
    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    
    public static String dec(BigDecimal bd) {
        if (bd == null) return "0.00";
        return bd.stripTrailingZeros().toPlainString();
    }

    
    public static String nowUtcZ() {
        SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    
    public static String normalizeToUtcZ(String ts) {
        if (ts == null || ts.isEmpty()) return nowUtcZ();
        try {
            String fixed = ts.replaceFirst("([+-]\\d\\d):(\\d\\d)$", "$1$2");
            OffsetDateTime odt = OffsetDateTime.parse(fixed);
            Instant inst = odt.toInstant();
            return DateTimeFormatter.ISO_INSTANT.format(inst);
        } catch (Throwable ignore) {
            return ts;
        }
    }
}
