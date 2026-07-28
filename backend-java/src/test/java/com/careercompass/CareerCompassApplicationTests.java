package com.careercompass;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class CareerCompassApplicationTests {

    @Test
    void application_hasSpringBootConfiguration() {
        assertThat(CareerCompassApplication.class).hasAnnotation(SpringBootApplication.class);
    }
}