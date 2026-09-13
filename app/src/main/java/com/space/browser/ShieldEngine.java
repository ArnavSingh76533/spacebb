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
            Set<String> loaded = new HashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("hosts.txt")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length > 1 && (parts[0].equals("0.0.0.0") || parts[0].equals("127.0.0.1"))) {
                        String h = parts[1].toLowerCase(java.util.Locale.ROOT);
                        if (h.contains(".") && !h.endsWith(".local") && !h.equals("localhost.localdomain")) loaded.add(h);
                    }
                }
            } catch (Exception ignored) {}
            domains = Collections.unmodifiableSet(loaded);
            ready.countDown();
        }, "Space-filters").start();
    }
    int size() { return domains.size(); }
    boolean blocked(String url) { try { ready.await(2, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } return BrowserLogic.matchesDomain(BrowserLogic.host(url), domains); }
}
