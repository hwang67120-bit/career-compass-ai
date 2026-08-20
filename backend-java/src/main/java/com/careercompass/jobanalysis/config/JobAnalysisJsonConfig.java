package com.careercompass.jobanalysis.config;

import com.careercompass.jobanalysis.service.JobAnalysisJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JobAnalysisJsonConfig {

    /**
     * 기능: 분석 저장 JSON에 사용하는 Jackson 2 설정과 직렬화 책임을 하나의 코덱으로 제공한다.
     */
    @Bean
    public JobAnalysisJsonCodec jobAnalysisJsonCodec() {
        return new JobAnalysisJsonCodec(new ObjectMapper());
    }
}
