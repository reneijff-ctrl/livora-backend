package com.joinlivora.backend.testutil;

import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;

/**
 * Shared test utility for creating valid User entities in @DataJpaTest tests.
 * Ensures all NOT NULL fields (including username) are populated.
 */
public final class TestUserFactory {

    private TestUserFactory() {
    }

    /**
     * Creates a valid User with all required fields populated.
     * Username is derived from the email prefix to ensure uniqueness
     * when different emails are used per test.
     */
    public static User createUser(String email, Role role) {
        User user = new User(email, "password", role);
        user.setUsername(deriveUsername(email));
        return user;
    }

    public static User createCreator(String email) {
        return createUser(email, Role.CREATOR);
    }

    public static User createViewer(String email) {
        return createUser(email, Role.USER);
    }

    private static String deriveUsername(String email) {
        String prefix = email.contains("@") ? email.substring(0, email.indexOf("@")) : email;
        // Truncate to 30 chars (username column max length)
        if (prefix.length() > 30) {
            prefix = prefix.substring(0, 30);
        }
        return prefix;
    }
}
