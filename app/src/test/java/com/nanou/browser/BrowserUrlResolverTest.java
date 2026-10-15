package com.nanou.browser;

import static org.junit.Assert.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class BrowserUrlResolverTest {
    @Test public void blankOpensHome() {
        assertEquals(BrowserUrlResolver.HOME_URL, BrowserUrlResolver.resolve("  "));
        assertEquals(BrowserUrlResolver.HOME_URL, BrowserUrlResolver.resolve(null));
    }
    @Test public void keepsHttpAndHttps() {
        assertEquals("https://example.com/a?q=hello",
                BrowserUrlResolver.resolve("https://example.com/a?q=hello"));
        assertEquals("http://example.com", BrowserUrlResolver.resolve("http://example.com"));
    }
    @Test public void addsHttpsToDomain() {
        assertEquals("https://example.com/path", BrowserUrlResolver.resolve("example.com/path"));
        assertEquals("https://www.example.com", BrowserUrlResolver.resolve("www.example.com"));
    }
    @Test public void searchesWords() {
        assertEquals(BrowserUrlResolver.SEARCH_BASE
                        + URLEncoder.encode("sakafo gasy", StandardCharsets.UTF_8),
                BrowserUrlResolver.resolve("sakafo gasy"));
    }
    @Test public void refusesUnsupportedSchemesForNavigation() {
        assertTrue(BrowserUrlResolver.resolve("javascript:alert(1)").startsWith(BrowserUrlResolver.SEARCH_BASE));
        assertFalse(BrowserUrlResolver.isWebUrl("javascript:alert(1)"));
        assertFalse(BrowserUrlResolver.isWebUrl("file:///etc/passwd"));
        assertFalse(BrowserUrlResolver.isWebUrl("content://private/item"));
        assertFalse(BrowserUrlResolver.isWebUrl("data:text/html,hello"));
        assertFalse(BrowserUrlResolver.isWebUrl("https://"));
    }
    @Test public void handlesLocalhostAndHome() {
        assertEquals("http://localhost:8080/health", BrowserUrlResolver.resolve("localhost:8080/health"));
        assertTrue(BrowserUrlResolver.isHomeUrl(BrowserUrlResolver.HOME_URL));
        assertTrue(BrowserUrlResolver.isHomeUrl("https://nanou.browser/"));
        assertFalse(BrowserUrlResolver.isHomeUrl("https://example.com"));
    }
}
