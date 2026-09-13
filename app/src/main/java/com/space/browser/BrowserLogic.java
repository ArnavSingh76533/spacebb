package com.space.browser;

import java.net.IDN;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Pure, testable URL policy. Never accepts script, file, content or intent input. */
public final class BrowserLogic {
    private BrowserLogic() {}
    public static String host(String url) {
        try { String h = URI.create(url).getHost(); return h == null ? "" : h.toLowerCase(Locale.ROOT); }
        catch (Exception e) { return ""; }
    }
    public static String origin(String value) {
        try { URI u=URI.create(value); if(!webUrl(value))return "";
            String scheme=u.getScheme().toLowerCase(Locale.ROOT);
            int port=u.getPort()<0?(scheme.equals("https")?443:80):u.getPort();
            return scheme+"://"+u.getHost().toLowerCase(Locale.ROOT)+":"+port;
        } catch(Exception ignored) { return ""; }
    }
    public static boolean webUrl(String value) {
        try { URI u = URI.create(value); return ("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme())) && u.getHost() != null && u.getUserInfo() == null; }
        catch (Exception e) { return false; }
    }
    public static String resolve(String value, String engine) {
        String s = value.trim();
        if (s.isEmpty()) return "";
        if (webUrl(s)) return s;
        if (!s.matches(".*\\s.*") && !s.contains("@") && !s.contains("://")) {
            String h = s.split("/", 2)[0];
            String name = h.split(":", 2)[0];
            if (name.equals("localhost") || name.contains(".")) {
                try {
                    String ascii = IDN.toASCII(name);
                    String normalized = ascii + s.substring(name.length());
                    String candidate = (name.equals("localhost") || name.matches("[0-9.]+") ? "http://" : "https://") + normalized;
                    if (webUrl(candidate)) return candidate;
                } catch (Exception ignored) {}
            }
        }
        String base = "Google".equals(engine) ? "https://www.google.com/search?q=" : "Brave".equals(engine) ? "https://search.brave.com/search?q=" : "https://duckduckgo.com/?q=";
        try { return base + URLEncoder.encode(s, "UTF-8"); } catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
    public static boolean matchesDomain(String host, Set<String> domains) {
        String h = host.toLowerCase(Locale.ROOT);
        while (!h.isEmpty()) {
            if (domains.contains(h)) return true;
            int dot = h.indexOf('.');
            if (dot < 0) break;
            h = h.substring(dot + 1);
        }
        return false;
    }
}
