package com.joinlivora.backend.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HashUtilTest {

    @Test
    void sha256_ShouldReturnCorrectHash() {
        String input = "hello";
        // sha256 of "hello" is 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        String expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
        
        String result = HashUtil.sha256(input);
        
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void sha256_WithEmptyString_ShouldReturnCorrectHash() {
        String input = "";
        // sha256 of empty string is e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        
        String result = HashUtil.sha256(input);
        
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void sha256_WithNull_ShouldReturnNull() {
        assertThat(HashUtil.sha256(null)).isNull();
    }
}








