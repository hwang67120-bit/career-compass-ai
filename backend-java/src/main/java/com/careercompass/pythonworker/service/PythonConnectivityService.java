package com.careercompass.pythonworker.service;

import java.util.concurrent.TimeUnit;

import com.careercompass.pythonworker.client.PythonHealthClient;
import com.careercompass.pythonworker.dto.PythonHealthResponse;
import com.careercompass.pythonworker.dto.PythonStatusResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@RequiredArgsConstructor
@Service
public class PythonConnectivityService {

    private static final Logger log = LoggerFactory.getLogger(PythonConnectivityService.class);

    private final PythonHealthClient pythonHealthClient;

    /**
     * 기능: Python 분석 서버의 연결 상태를 확인한다.
     * 반환 값: 연결 여부와 Python 상태 및 모델 준비 상태를 반환한다.
     */
    public PythonStatusResponse checkPythonConnectivity() {
        long startedAt = System.nanoTime();
        try {
            PythonHealthResponse health = pythonHealthClient.getHealth();
            if (health == null) {
                log.warn("python_health_check_completed connected=false durationMs={}",
                        elapsedMillis(startedAt));
                return disconnectedStatus();
            }
            log.info("python_health_check_completed connected=true modelReady={} durationMs={}",
                    health.modelReady(), elapsedMillis(startedAt));
            return new PythonStatusResponse(
                    true,
                    health.status(),
                    health.modelReady()
            );
        } catch (RestClientException exception) {
            log.warn("python_health_check_completed connected=false failure={} durationMs={}",
                    exception.getClass().getSimpleName(), elapsedMillis(startedAt));
            return disconnectedStatus();
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private PythonStatusResponse disconnectedStatus() {
        return new PythonStatusResponse(false, null, null);
    }
}
