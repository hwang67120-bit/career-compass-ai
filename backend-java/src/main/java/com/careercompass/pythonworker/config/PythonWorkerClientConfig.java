package com.careercompass.pythonworker.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class PythonWorkerClientConfig {

    /**
     * 기능: Python(uvicorn/h11)이 평문 HTTP/1.1만 처리하므로 JDK HttpClient 기본값인
     * HTTP/2 h2c 업그레이드 시도("Unsupported upgrade request" 오류, 실제 확인됨,
     * 2026-08-09)를 피하도록 HTTP/1.1로 고정한 RestClient를 만든다. HttpClient 구성을
     * 클라이언트 생성자 밖으로 빼서, 테스트에서 Mock 서버로 바인딩한 RestClient를 그대로
     * 주입할 수 있게 한다(생성자 안에서 requestFactory를 재설정하면 Mock 바인딩이 실제
     * 네트워크 호출로 덮어써진다 — 코덱스가 PR #48 테스트 7건 실패로 확인함).
     */
    @Bean
    RestClient pythonJobPostingExtractionRestClient(
            RestClient.Builder builder,
            PythonWorkerProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.extractConnectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.extractReadTimeout());

        return builder
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .build();
    }
}
