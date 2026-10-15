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

    @Test public void handlesQueryAndFragmentOnBareDomains() {
        assertEquals("https://example.com?q=nanou", BrowserUrlResolver.resolve("example.com?q=nanou"));
        assertEquals("https://example.com#section", BrowserUrlResolver.resolve("example.com#section"));
        assertEquals("https://example.com/path?q=nanou#section",
                BrowserUrlResolver.resolve("example.com/path?q=nanou#section"));
    }

    @Test public void handlesLocalhostWithoutPortAndWithQuery() {
        assertEquals("http://localhost", BrowserUrlResolver.resolve("localhost"));
        assertEquals("http://localhost/path?q=1", BrowserUrlResolver.resolve("localhost/path?q=1"));
        assertEquals("http://localhost:8080/health?full=true#top",
                BrowserUrlResolver.resolve("localhost:8080/health?full=true#top"));
    }

    @Test public void rejectsInvalidPortNumbers() {
        assertFalse(BrowserUrlResolver.isWebUrl("https://example.com:65536/"));
        assertFalse(BrowserUrlResolver.isWebUrl("https://example.com:99999/"));
        assertTrue(BrowserUrlResolver.isWebUrl("https://example.com:65535/"));
        assertTrue(BrowserUrlResolver.isWebUrl("http://example.com:0/"));
        assertEquals(BrowserUrlResolver.SEARCH_BASE + "localhost%3A99999",
                BrowserUrlResolver.resolve("localhost:99999"));
    }
}
