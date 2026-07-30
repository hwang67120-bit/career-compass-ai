package com.careercompass.pythonworker.service;

import com.careercompass.pythonworker.client.PythonHealthClient;
import com.careercompass.pythonworker.dto.PythonHealthResponse;
import com.careercompass.pythonworker.dto.PythonStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class PythonConnectivityService {

    private final PythonHealthClient pythonHealthClient;

    public PythonConnectivityService(PythonHealthClient pythonHealthClient) {
        this.pythonHealthClient = pythonHealthClient;
    }

    /*
     * Python 분석 서버의 연결 상태를 확인한다.
     * 반환값은 연결 여부와 Python 상태 및 모델 준비 상태다.
     */
    public PythonStatusResponse checkPythonConnectivity() {
        try {
            PythonHealthResponse health = pythonHealthClient.getHealth();
            if (health == null) {
                return disconnectedStatus();
            }
            return new PythonStatusResponse(
                    true,
                    health.status(),
                    health.modelReady()
            );
        } catch (RestClientException exception) {
            return disconnectedStatus();
        }
    }

    private PythonStatusResponse disconnectedStatus() {
        return new PythonStatusResponse(false, null, null);
    }
}
