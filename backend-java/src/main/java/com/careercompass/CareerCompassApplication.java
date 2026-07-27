package com.careercompass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CareerCompassApplication {

	public static void main(String[] args) {
		SpringApplication.run(CareerCompassApplication.class, args);
	}

}
