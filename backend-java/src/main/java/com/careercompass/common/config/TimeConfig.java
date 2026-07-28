package com.careercompass.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /**
     * 기능: 서버의 생성 시각과 응답 시각을 같은 시간 기준으로 제공한다.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}