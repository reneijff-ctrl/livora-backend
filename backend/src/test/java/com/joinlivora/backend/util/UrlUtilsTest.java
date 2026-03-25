package com.joinlivora.backend.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UrlUtilsTest {

    @Test
    public void testSanitizeUrl_NullOrEmpty() {
        assertNull(UrlUtils.sanitizeUrl(null));
        assertNull(UrlUtils.sanitizeUrl(""));
        assertNull(UrlUtils.sanitizeUrl("  "));
    }

    @Test
    public void testSanitizeUrl_Localhost() {
        assertEquals("/uploads/content/jordan.mp4", UrlUtils.sanitizeUrl("http://localhost:8080/uploads/content/jordan.mp4"));
        assertEquals("/uploads/content/jordan.mp4", UrlUtils.sanitizeUrl("https://localhost/uploads/content/jordan.mp4"));
        assertEquals("/uploads/content/jordan.mp4", UrlUtils.sanitizeUrl("http://localhost/uploads/content/jordan.mp4"));
    }

    @Test
    public void testSanitizeUrl_AlreadyRelative() {
        assertEquals("/uploads/content/jordan.mp4", UrlUtils.sanitizeUrl("/uploads/content/jordan.mp4"));
        assertEquals("/uploads/content/jordan.mp4", UrlUtils.sanitizeUrl("uploads/content/jordan.mp4"));
    }

    @Test
    public void testSanitizeUrl_MissingPrefix() {
        assertEquals("/uploads/content/jordan.mp4", UrlUtils.sanitizeUrl("content/jordan.mp4"));
        assertEquals("/uploads/content/jordan.mp4", UrlUtils.sanitizeUrl("/content/jordan.mp4"));
    }
    
    @Test
    public void testSanitizeUrl_OtherHosts() {
        // Requirement says "Strip protocol and host if starts with http://localhost:"
        // and "Always return paths starting with /uploads/"
        // If it's another host, it currently prepends /uploads/ which is a bit weird but follows the strict rule.
        // However, usually we don't have other hosts in stored mediaUrl.
        assertEquals("/uploads/http://example.com/img.jpg", UrlUtils.sanitizeUrl("http://example.com/img.jpg"));
    }
}








