package com.careercompass.jobsearch.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class Work24ClientConfig {

    /**
     * 기능: 자동 리다이렉트를 차단하고 고용24 공식 호스트만 사용하는 HTTP 클라이언트를 구성한다.
     */
    @Bean
    RestClient work24ApiRestClient(RestClient.Builder builder, Work24ApiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl().toString())
                .build();
    }
}
