package com.vanmanh49.auth;

import com.vanmanh49.auth.user.Role;
import com.vanmanh49.auth.user.User;
import com.vanmanh49.auth.user.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the configured admin account on first start. */
@Component
public class AdminSeeder implements ApplicationRunner {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final SecurityProperties.Admin admin;

	public AdminSeeder(UserRepository users, PasswordEncoder passwordEncoder, SecurityProperties properties) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.admin = properties.admin();
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!this.users.existsByUsername(this.admin.username())) {
			this.users.save(new User(this.admin.username(), this.admin.username() + "@shop.local",
					this.passwordEncoder.encode(this.admin.password()), Role.ADMIN));
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class PasswordEncoderConfiguration {

		@Bean
		PasswordEncoder passwordEncoder() {
			return new BCryptPasswordEncoder();
		}

	}

}
