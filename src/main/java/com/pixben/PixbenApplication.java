package com.pixben;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class PixbenApplication {

	public static void main(String[] args) {
		SpringApplication.run(PixbenApplication.class, args);
	}

}
