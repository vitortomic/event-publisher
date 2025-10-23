package com.sporty.homework.event_publisher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EventPublisherApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventPublisherApplication.class, args);
	}

}
