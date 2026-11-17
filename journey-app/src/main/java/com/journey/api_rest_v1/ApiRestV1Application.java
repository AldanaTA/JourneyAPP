package com.journey.api_rest_v1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ApiRestV1Application {

	public static void main(String[] args) {
		SpringApplication.run(ApiRestV1Application.class, args);
	}

}
