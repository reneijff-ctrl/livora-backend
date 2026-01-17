package com.joinlivora.backend.config;

import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initUsers(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {

            if (userRepository.findByEmail("test@test.com").isEmpty()) {

                User user = new User();
                user.setEmail("test@test.com");
                user.setPassword(passwordEncoder.encode("password"));
                user.setRole(Role.USER);

                userRepository.save(user);

                System.out.println("✅ Test user created: test@test.com / password");
            }

            if (userRepository.findByEmail("admin@test.com").isEmpty()) {
                User admin = new User();
                admin.setEmail("admin@test.com");
                admin.setPassword(passwordEncoder.encode("password"));
                admin.setRole(Role.ADMIN);
                userRepository.save(admin);
                System.out.println("✅ Admin user created: admin@test.com / password");
            }
        };
    }
}
