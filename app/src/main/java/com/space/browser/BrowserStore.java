package com.space.browser;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class BrowserStore {
    final SharedPreferences prefs;
    static final class Entry {
        final String title, url;
        Entry(String title, String url) { this.title = title; this.url = url; }
    }
    BrowserStore(Context context) { prefs = context.getSharedPreferences("space", Context.MODE_PRIVATE); }
    List<Entry> entries(String key) {
        List<Entry> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(key, "[]"));
            for (int i=0;i<array.length();i++) { JSONObject o = array.getJSONObject(i); if (BrowserLogic.webUrl(o.getString("url"))) list.add(new Entry(o.optString("title", "Page"), o.getString("url"))); }
        } catch (Exception ignored) {}
        return list;
    }
    void add(String key, String title, String url, int cap) {
        if (!BrowserLogic.webUrl(url)) return;
        List<Entry> list = entries(key);
        list.removeIf(e -> e.url.equals(url));
        list.add(0, new Entry(title == null || title.trim().isEmpty() ? BrowserLogic.host(url) : title, url));
        while (list.size() > cap) list.remove(list.size()-1);
        write(key, list);
    }
    void remove(String key, String url) { List<Entry> list = entries(key); list.removeIf(e -> e.url.equals(url)); write(key, list); }
    void write(String key, List<Entry> list) {
        JSONArray array = new JSONArray();
        for (Entry e : list) { try { array.put(new JSONObject().put("title", e.title).put("url", e.url)); } catch (Exception ignored) {} }
        prefs.edit().putString(key, array.toString()).apply();
    }
}
