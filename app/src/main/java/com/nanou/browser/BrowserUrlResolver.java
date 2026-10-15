package com.nanou.browser;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalizes address-bar input and allows only HTTP(S) navigation. */
public final class BrowserUrlResolver {
    public static final String HOME_URL = "nanou://home";
    public static final String SEARCH_BASE = "https://duckduckgo.com/?q=";
    private static final Pattern HOST_LIKE = Pattern.compile(
            "^(?i)(localhost|(?:[a-z0-9-]+\\.)+[a-z]{2,63}|(?:[0-9]{1,3}\\.){3}[0-9]{1,3})(?::[0-9]{1,5})?(?:[/?#].*)?$"
    );

    private BrowserUrlResolver() {}

    public static String resolve(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) return HOME_URL;
        String input = rawInput.trim();
        if (HOME_URL.equalsIgnoreCase(input) || "home".equalsIgnoreCase(input)) return HOME_URL;
        if (containsWhitespace(input)) return search(input);
        if (input.toLowerCase(Locale.ROOT).matches("^localhost(?::[0-9]{1,5})?(?:[/?#].*)?$")) {
            String candidate = "http://" + input;
            if (isWebUrl(candidate)) return candidate;
        }
        // Test bare host:port before treating the colon as a URI scheme.
        if (HOST_LIKE.matcher(input).matches()) {
            String candidate = "https://" + input;
            if (isWebUrl(candidate)) return candidate;
        }
        if (hasScheme(input)) return isWebUrl(input) ? input : search(input);
        return search(input);
    }

    public static boolean isWebUrl(String value) {
        if (value == null || value.isEmpty() || containsWhitespace(value)) return false;
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            int port = uri.getPort();
            return scheme != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && !uri.getHost().isEmpty()
                    && port >= -1 && port <= 65535;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static boolean isHomeUrl(String value) {
        return value != null && (HOME_URL.equalsIgnoreCase(value)
                || value.startsWith("https://nanou.browser/")
                || value.startsWith("http://nanou.browser/"));
    }

    private static String search(String query) {
        return SEARCH_BASE + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    private static boolean hasScheme(String input) {
        return input.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*$");
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) if (Character.isWhitespace(value.charAt(i))) return true;
        return false;
    }
}
