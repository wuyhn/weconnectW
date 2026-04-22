package com.weconnect.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WeconnectApplication {

	public static void main(String[] args) {
		SpringApplication.run(WeconnectApplication.class, args);
	}

}
