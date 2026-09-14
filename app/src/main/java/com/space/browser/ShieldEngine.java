package com.space.browser;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class ShieldEngine {
    private final java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
    private volatile Set<String> domains = Collections.emptySet();
    ShieldEngine(Context context) {
        // No remote calls during browsing; immutable lookup set shared between tabs.
        new Thread(() -> {
            Set<String> loaded = new HashSet<>(110000);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("hosts.txt")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int prefix = line.startsWith("0.0.0.0") ? 7 : line.startsWith("127.0.0.1") ? 9 : -1;
                    if (prefix < 0 || line.length() <= prefix || !Character.isWhitespace(line.charAt(prefix))) continue;
                    int from=prefix;
                    while(from<line.length() && Character.isWhitespace(line.charAt(from)))from++;
                    int to=from;
                    while(to<line.length() && !Character.isWhitespace(line.charAt(to)) && line.charAt(to)!='#')to++;
                    String h=line.substring(from,to).toLowerCase(java.util.Locale.ROOT);
                    if (h.contains(".") && !h.endsWith(".local") && !h.equals("localhost.localdomain")) loaded.add(h);
                }
            } catch (Exception ignored) {}
            domains = Collections.unmodifiableSet(loaded);
            ready.countDown();
        }, "Space-filters").start();
    }
    int size() { return domains.size(); }
    boolean blocked(String url) { try { ready.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } return BrowserLogic.matchesDomain(BrowserLogic.host(url), domains); }
}
