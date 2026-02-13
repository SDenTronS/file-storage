package dev.dentron.filestorage.common.util;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class PathUtils {
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    public static String sanitizePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String p = raw.strip();

        if (p.startsWith("/") || p.endsWith("/") || p.contains("\\") || p.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid prefix");
        }

        String[] parts = p.split("/", -1);
        var out = new ArrayList<String>(parts.length);

        for (String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("Invalid prefix");
            }

            if (part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("Invalid prefix");
            }

            if (!SEGMENT.matcher(part).matches()) {
                throw new IllegalArgumentException("Invalid prefix");
            }

            out.add(part);
        }

        String result = String.join("/", out);

        if (result.length() > 256) {
            throw new IllegalArgumentException("Invalid prefix");
        }

        return result;
    }

}
