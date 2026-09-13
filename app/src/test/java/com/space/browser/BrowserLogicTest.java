package com.space.browser;
import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;
public class BrowserLogicTest {
    @Test public void urlInputIsSafe() {
        assertEquals("https://example.org/path", BrowserLogic.resolve("example.org/path", "DuckDuckGo"));
        assertEquals("http://localhost:8080", BrowserLogic.resolve("localhost:8080", "DuckDuckGo"));
        assertEquals("http://192.168.1.1", BrowserLogic.resolve("192.168.1.1", "DuckDuckGo"));
        assertTrue(BrowserLogic.resolve("two words", "Google").startsWith("https://www.google.com/search?q=two+words"));
        assertTrue(BrowserLogic.resolve("javascript:alert(1)", "DuckDuckGo").startsWith("https://duckduckgo.com/"));
        assertFalse(BrowserLogic.webUrl("https://user:pass@example.com"));
        assertFalse(BrowserLogic.webUrl("file:///etc/passwd"));
        assertFalse(BrowserLogic.webUrl("intent://example.com"));
        assertFalse(BrowserLogic.webUrl("https://"));
        assertEquals("", BrowserLogic.resolve("   ", "Brave"));
    }
    @Test public void filtersRespectDomainBoundaries() {
        Set<String> rules = Set.of("doubleclick.net", "ads.example.com");
        assertTrue(BrowserLogic.matchesDomain("a.doubleclick.net", rules));
        assertTrue(BrowserLogic.matchesDomain("DOUBLECLICK.NET", rules));
        assertFalse(BrowserLogic.matchesDomain("notdoubleclick.net", rules));
        assertFalse(BrowserLogic.matchesDomain("doubleclick.net.evil.org", rules));
        assertFalse(BrowserLogic.matchesDomain("example.com", rules));
        assertEquals("example.com", BrowserLogic.host("https://example.com/a"));
    }
}
