package com.codecraft.eventsuggestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EventsuggestionApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventsuggestionApplication.class, args);
	}

}