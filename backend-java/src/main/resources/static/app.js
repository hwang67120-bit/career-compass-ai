(() => {
    "use strict";

    const elements = {
        loadingView: document.getElementById("loading-view"),
        loginView: document.getElementById("login-view"),
        dashboardView: document.getElementById("dashboard-view"),
        sessionChip: document.getElementById("session-chip"),
        logoutButton: document.getElementById("logout-button"),
        sessionLabel: document.getElementById("session-label"),
        currentUserId: document.getElementById("current-user-id"),
        globalMessage: document.getElementById("global-message"),
        documentForm: document.getElementById("document-form"),
        documentStatus: document.getElementById("document-status"),
        githubForm: document.getElementById("github-form"),
        githubStatus: document.getElementById("github-status"),
        githubResult: document.getElementById("github-result"),
        resultRepository: document.getElementById("result-repository"),
        resultBranch: document.getElementById("result-branch"),
        resultCommit: document.getElementById("result-commit"),
        analysisBarIcon: document.getElementById("analysis-bar-icon"),
        analysisBarTitle: document.getElementById("analysis-bar-title"),
        analysisBarDescription: document.getElementById("analysis-bar-description"),
        startAnalysisButton: document.getElementById("start-analysis-button"),
        analysisProgressView: document.getElementById("analysis-progress-view"),
        analysisLiveStatus: document.getElementById("analysis-live-status"),
        runStatus: document.getElementById("run-status"),
        runElapsed: document.getElementById("run-elapsed"),
        runTokens: document.getElementById("run-tokens"),
        runBudget: document.getElementById("run-budget"),
        progressLog: document.getElementById("progress-log"),
        pauseAnalysisButton: document.getElementById("pause-analysis-button"),
        adjustAnalysisButton: document.getElementById("adjust-analysis-button"),
        cancelAnalysisButton: document.getElementById("cancel-analysis-button"),
        progressDashboardButton: document.getElementById("progress-dashboard-button"),
        analysisAdjustDialog: document.getElementById("analysis-adjust-dialog"),
        analysisAdjustForm: document.getElementById("analysis-adjust-form"),
        analysisInstructionInput: document.getElementById("analysis-instruction-input"),
        tokenBudgetInput: document.getElementById("token-budget-input"),
        closeAnalysisSettingsButton: document.getElementById("close-analysis-settings-button"),
        executionJobId: document.getElementById("execution-job-id"),
        executionRequestId: document.getElementById("execution-request-id"),
        executionProvider: document.getElementById("execution-provider"),
        executionMode: document.getElementById("execution-mode"),
        executionInstruction: document.getElementById("execution-instruction"),
        analysisResultView: document.getElementById("analysis-result-view"),
        resultChecklist: document.getElementById("result-checklist"),
        resultSimilarity: document.getElementById("result-similarity"),
        resultSkillGaps: document.getElementById("result-skill-gaps"),
        resultEvidence: document.getElementById("result-evidence"),
        backToDashboardButton: document.getElementById("back-to-dashboard-button")

    };

    const state = {
        document: null,
        github: null,
        analysis: {
            status: "IDLE",
            paused: false,
            cancelled: false,
            waitingForBudget: false,
            activeElapsedMs: 0,
            tokenUsed: 0,
            tokenBudget: 3000,
            instruction: "",
            jobId: null,
            requestId: null,
            completedSteps: []
        }
    };

    class ApiRequestError extends Error {
        constructor(status, payload) {
            const apiError = payload?.error;
            super(apiError?.message || "요청을 처리하는 중 문제가 발생했습니다.");
            this.name = "ApiRequestError";
            this.status = status;
            this.errorType = apiError?.errorType;
            this.fieldErrors = apiError?.fieldErrors || [];
            this.retryable = apiError?.retryable === true;
        }
    }

    async function request(path, options = {}) {
        const response = await fetch(path, {
            credentials: "same-origin",
            ...options,
            headers: {
                Accept: "application/json",
                ...(options.body ? {"Content-Type": "application/json"} : {}),
                ...(options.headers || {})
            }
        });

        const payload = response.status === 204
            ? null
            : await response.json().catch(() => null);

        if (!response.ok) {
            throw new ApiRequestError(response.status, payload);
        }

        return payload?.data ?? null;
    }

    async function csrfHeaders() {
        try {
            const csrf = await request("/api/v1/auth/csrf");
            return csrf?.headerName && csrf?.token
                ? {[csrf.headerName]: csrf.token}
                : {};
        } catch (error) {
            if (error instanceof ApiRequestError && error.status === 404) {
                return {};
            }
            throw error;
        }
    }

    async function mutation(path, body, method = "POST") {
        return request(path, {
            method,
            headers: await csrfHeaders(),
            ...(body === undefined ? {} : {body: JSON.stringify(body)})
        });
    }

    function showOnly(target) {
        [
            elements.loadingView,
            elements.loginView,
            elements.dashboardView,
            elements.analysisProgressView,
            elements.analysisResultView
        ].forEach((view) => view.classList.toggle("is-hidden", view !== target));
    }

    function sleep(ms) {
        return new Promise((resolve) => setTimeout(resolve, ms));
    }

    function updateAnalysisAvailability() {
        const ready = Boolean(state.document);
        elements.startAnalysisButton.disabled = !ready;
        elements.analysisBarIcon.classList.toggle("is-ready", ready);

        if (ready) {
            elements.analysisBarTitle.textContent = "분석을 시작할 수 있어요.";
            elements.analysisBarDescription.textContent = state.github
                ? "이력서와 GitHub 저장소 정보로 분석을 진행합니다."
                : "이력서 내용만으로 분석을 진행합니다.";
        } else {
            elements.analysisBarTitle.textContent = "이력서 내용을 먼저 등록해 주세요.";
            elements.analysisBarDescription.textContent = "문서 등록이 확인되면 분석을 시작할 수 있어요.";
        }
    }

    function showGlobalMessage(message, isError = false) {
        elements.globalMessage.textContent = message;
        elements.globalMessage.classList.remove("is-hidden", "message-error");
        elements.globalMessage.classList.toggle("message-error", isError);
    }

    function clearGlobalMessage() {
        elements.globalMessage.classList.add("is-hidden");
        elements.globalMessage.textContent = "";
    }

    function updateStatus(element, state, title, description) {
        element.className = `status-box status-${state}`;
        const titleElement = element.querySelector("strong");
        const descriptionElement = element.querySelector("p");
        titleElement.textContent = title;
        descriptionElement.textContent = description;
    }

    function setFormBusy(form, busy) {
        const button = form.querySelector("button[type='submit']");
        const label = button.querySelector(".button-label");
        const spinner = button.querySelector(".button-spinner");
        form.querySelectorAll("input, textarea, select, button")
            .forEach((control) => {
                control.disabled = busy;
            });
        label.classList.toggle("is-hidden", busy);
        spinner.classList.toggle("is-hidden", !busy);
        button.setAttribute("aria-busy", String(busy));
    }

    function displayError(statusElement, error) {
        const fieldMessage = error instanceof ApiRequestError
            ? error.fieldErrors[0]?.message
            : null;
        const description = fieldMessage || error.message
            || "잠시 후 다시 시도해 주세요.";
        updateStatus(statusElement, "error", "등록 실패", description);

        if (error instanceof ApiRequestError && error.status === 401) {
            showOnly(elements.loginView);
            elements.sessionChip.classList.add("is-hidden");
        }
    }

    async function loadSession() {
        try {
            const currentUser = await request("/api/v1/auth/me");
            if (!currentUser?.authenticated) {
                showOnly(elements.loginView);
                return;
            }

            if (!configureAuthenticatedSession(currentUser)) {
                showOnly(elements.loginView);
                elements.sessionChip.classList.add("is-hidden");
                return;
            }

            elements.currentUserId.textContent = currentUser.userId;
            elements.currentUserId.title = currentUser.userId;
            elements.sessionChip.classList.remove("is-hidden");
            showOnly(elements.dashboardView);
        } catch (error) {
            showOnly(elements.loginView);
            const message = error instanceof ApiRequestError
                ? error.message
                : "서버에 연결할 수 없습니다.";
            console.error(message);
        }
    }

    function configureAuthenticatedSession(currentUser) {
        if (currentUser.authenticationMode === "GITHUB"
                && typeof currentUser.githubLogin === "string"
                && currentUser.githubLogin.trim()) {
            elements.sessionLabel.textContent =
                `GitHub @${currentUser.githubLogin} 연결됨`;
            elements.logoutButton.classList.remove("is-hidden");
            return true;
        }

        return false;
    }

    async function registerDocument(event) {
        event.preventDefault();
        clearGlobalMessage();

        const formData = new FormData(elements.documentForm);
        const text = String(formData.get("text") || "").trim();
        if (!text) {
            updateStatus(
                elements.documentStatus,
                "error",
                "입력 필요",
                "등록할 문서 내용을 입력해 주세요."
            );
            document.getElementById("document-text").focus();
            return;
        }

        setFormBusy(elements.documentForm, true);
        updateStatus(
            elements.documentStatus,
            "loading",
            "등록 중",
            "문서 내용을 검증하고 저장하고 있어요."
        );

        try {
            const result = await mutation("/api/v1/documents", {
                documentType: formData.get("documentType"),
                text
            });
            state.document = result;
            updateAnalysisAvailability();
            updateStatus(
                elements.documentStatus,
                "success",
                "등록 완료",
                `${documentTypeLabel(result.documentType)} · ${formatDate(result.createdAt)}`
            );
            showGlobalMessage(`문서 등록이 완료됐습니다. 문서 ID: ${result.documentId}`);
        } catch (error) {
            displayError(elements.documentStatus, error);
        } finally {
            setFormBusy(elements.documentForm, false);
        }
    }

    async function registerGitHubRepository(event) {
        event.preventDefault();
        clearGlobalMessage();
        elements.githubResult.classList.add("is-hidden");

        const formData = new FormData(elements.githubForm);
        const repositoryUrl = String(formData.get("repositoryUrl") || "").trim();
        if (!repositoryUrl) {
            updateStatus(
                elements.githubStatus,
                "error",
                "입력 필요",
                "GitHub 저장소 주소를 입력해 주세요."
            );
            document.getElementById("repository-url").focus();
            return;
        }

        setFormBusy(elements.githubForm, true);
        updateStatus(
            elements.githubStatus,
            "loading",
            "조회 중",
            "주소를 검증하고 GitHub에서 저장소 정보를 확인하고 있어요."
        );

        try {
            const result = await mutation("/api/v1/project-sources/github", {
                repositoryUrl
            });
            state.github = result;
            updateAnalysisAvailability();
            updateStatus(
                elements.githubStatus,
                "success",
                "등록 완료",
                "GitHub 저장소의 실제 조회 결과가 저장됐습니다."
            );
            elements.resultRepository.textContent = result.repositoryFullName;
            elements.resultBranch.textContent = result.defaultBranch;
            elements.resultCommit.textContent = shortCommit(result.commitSha);
            elements.resultCommit.title = result.commitSha;
            elements.githubResult.classList.remove("is-hidden");
            showGlobalMessage(`GitHub 프로젝트 등록이 완료됐습니다. 저장소: ${result.repositoryFullName}`);
        } catch (error) {
            displayError(elements.githubStatus, error);
        } finally {
            setFormBusy(elements.githubForm, false);
        }
    }

    async function logout() {
        elements.logoutButton.disabled = true;
        try {
            await mutation("/api/v1/auth/logout", undefined);
            window.location.assign("/");
        } catch (error) {
            showGlobalMessage(error.message || "로그아웃에 실패했습니다.", true);
            elements.logoutButton.disabled = false;
        }
    }

    class AnalysisCancelledError extends Error {
        constructor() {
            super("분석이 사용자 요청으로 중단됐습니다.");
            this.name = "AnalysisCancelledError";
        }
    }

    const analysisStatusLabels = {
        IDLE: "대기",
        RUNNING: "실행 중",
        PAUSED: "일시정지",
        WAITING_FOR_USER: "사용자 확인 대기",
        CANCEL_REQUESTED: "중단 요청",
        CANCELLED: "중단됨",
        COMPLETED: "완료",
        FAILED: "실패"
    };

    function createRunIdentifier(prefix) {
        const identifier = globalThis.crypto?.randomUUID?.()
            || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
        return `${prefix}-${identifier}`;
    }

    function formatDuration(durationMs) {
        return `${(durationMs / 1000).toFixed(1)}초`;
    }

    function updateRunMetrics() {
        const analysis = state.analysis;
        const statusLabel = analysisStatusLabels[analysis.status] || analysis.status;
        elements.analysisLiveStatus.textContent = `샘플 모드 · ${statusLabel}`;
        elements.runStatus.textContent = statusLabel;
        elements.runElapsed.textContent = formatDuration(analysis.activeElapsedMs);
        elements.runTokens.textContent = analysis.tokenUsed.toLocaleString("ko-KR");
        elements.runBudget.textContent = analysis.tokenBudget.toLocaleString("ko-KR");
        elements.executionJobId.textContent = analysis.jobId || "-";
        elements.executionRequestId.textContent = analysis.requestId || "-";
        elements.executionProvider.textContent = "연결 전";
        elements.executionMode.textContent = "프론트 샘플 · 외부 AI 호출 없음";
        elements.executionInstruction.textContent =
            analysis.instruction || "추가 분석 지시 없음";
    }

    function setAnalysisStatus(status) {
        state.analysis.status = status;
        updateRunMetrics();
    }

    function resetAnalysisRun() {
        state.analysis.status = "RUNNING";
        state.analysis.paused = false;
        state.analysis.cancelled = false;
        state.analysis.waitingForBudget = false;
        state.analysis.activeElapsedMs = 0;
        state.analysis.tokenUsed = 0;
        state.analysis.jobId = createRunIdentifier("sample-job");
        state.analysis.requestId = createRunIdentifier("sample-request");
        state.analysis.completedSteps = [];
        elements.pauseAnalysisButton.textContent = "일시정지";
        elements.pauseAnalysisButton.disabled = false;
        elements.adjustAnalysisButton.disabled = false;
        elements.cancelAnalysisButton.disabled = false;
        updateRunMetrics();
    }

    function createLogMeta(label, value) {
        const item = document.createElement("span");
        item.className = "chat-meta-item";

        const labelElement = document.createElement("strong");
        labelElement.textContent = `${label}: `;

        const valueElement = document.createElement("span");
        valueElement.textContent = value;

        item.append(labelElement, valueElement);
        return item;
    }

    function appendLogEntry(step) {
        const entry = document.createElement("div");
        entry.className = "chat-message chat-message-active";

        const avatar = document.createElement("span");
        avatar.className = "chat-avatar";
        avatar.setAttribute("aria-hidden", "true");
        avatar.innerHTML = '<span class="spinner" aria-hidden="true"></span>';

        const bubble = document.createElement("div");
        bubble.className = "chat-bubble";

        const textElement = document.createElement("p");
        textElement.className = "chat-text";
        textElement.textContent = step.title;
        bubble.appendChild(textElement);

        if (step.detail) {
            const detailElement = document.createElement("p");
            detailElement.className = "chat-detail";
            detailElement.textContent = step.detail;
            bubble.appendChild(detailElement);
        }

        const metadata = document.createElement("div");
        metadata.className = "chat-meta";
        metadata.append(
            createLogMeta("출처", step.source),
            createLogMeta("근거", step.evidence),
            createLogMeta("토큰", step.tokens > 0
                ? `샘플 ${step.tokens.toLocaleString("ko-KR")}`
                : "사용 안 함")
        );
        bubble.appendChild(metadata);

        entry.append(avatar, bubble);
        elements.progressLog.appendChild(entry);
        entry.scrollIntoView({behavior: "smooth", block: "end"});
        return entry;
    }

    function markLogEntry(entry, outcome, durationMs) {
        entry.classList.remove("chat-message-active");
        entry.classList.add(`chat-message-${outcome}`);
        const avatar = entry.querySelector(".chat-avatar");
        avatar.innerHTML = outcome === "done"
            ? '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 13 4 4 10-10"></path></svg>'
            : '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"></path></svg>';
        entry.querySelector(".chat-meta").append(
            createLogMeta("처리 시간", formatDuration(durationMs))
        );
    }

    async function waitForAnalysis(durationMs) {
        let remainingMs = durationMs;
        while (remainingMs > 0) {
            if (state.analysis.cancelled) {
                throw new AnalysisCancelledError();
            }
            if (state.analysis.paused) {
                await sleep(100);
                continue;
            }

            const sliceMs = Math.min(100, remainingMs);
            await sleep(sliceMs);
            remainingMs -= sliceMs;
            state.analysis.activeElapsedMs += sliceMs;
            updateRunMetrics();
        }
    }

    async function waitForTokenBudget(step) {
        const expectedTokens = state.analysis.tokenUsed + step.tokens;
        if (expectedTokens <= state.analysis.tokenBudget) {
            return;
        }

        state.analysis.paused = true;
        state.analysis.waitingForBudget = true;
        elements.pauseAnalysisButton.textContent = "예산 확인 필요";
        setAnalysisStatus("WAITING_FOR_USER");

        const budgetEntry = appendLogEntry({
            title: "토큰 예산 확인 필요",
            detail: `다음 단계를 실행하려면 샘플 토큰 예산을 ${expectedTokens.toLocaleString("ko-KR")} 이상으로 조정해 주세요.`,
            source: "사용자가 설정한 실행 예산",
            evidence: "예산을 초과하는 다음 호출은 아직 시작하지 않았습니다.",
            tokens: 0
        });

        while (state.analysis.tokenUsed + step.tokens > state.analysis.tokenBudget) {
            if (state.analysis.cancelled) {
                throw new AnalysisCancelledError();
            }
            await sleep(100);
        }
        markLogEntry(budgetEntry, "done", 0);

        state.analysis.waitingForBudget = false;
        state.analysis.paused = false;
        elements.pauseAnalysisButton.textContent = "일시정지";
        setAnalysisStatus("RUNNING");
    }

    async function runAnalysisStep(step) {
        await waitForTokenBudget(step);
        const entry = appendLogEntry(step);
        await waitForAnalysis(step.durationMs);
        state.analysis.tokenUsed += step.tokens;
        state.analysis.completedSteps.push(step);
        updateRunMetrics();
        markLogEntry(entry, "done", step.durationMs);
    }

    function sampleAnalysisSteps() {
        const documentEvidence =
            `${documentTypeLabel(state.document.documentType)} · ${formatDate(state.document.createdAt)}`;
        const githubEvidence = state.github
            ? `${state.github.repositoryFullName} · ${state.github.defaultBranch} · ${shortCommit(state.github.commitSha)}`
            : "GitHub 저장소가 등록되지 않아 이 단계에서는 사용하지 않습니다.";

        return [
            {
                title: "등록 문서 확인",
                detail: "서버가 실제로 등록한 문서 식별자와 상태를 확인합니다.",
                source: "Java 문서 등록 API",
                evidence: documentEvidence,
                tokens: 0,
                durationMs: 600
            },
            {
                title: "GitHub 자료 사용 여부 확인",
                detail: state.github
                    ? "등록된 공개 저장소의 고정 커밋을 분석 근거 후보로 사용합니다."
                    : "이력서 내용만으로 다음 단계를 준비합니다.",
                source: state.github ? "GitHub 저장소 등록 API" : "사용자 입력 상태",
                evidence: githubEvidence,
                tokens: 0,
                durationMs: 500
            },
            {
                title: "개인정보 제거 상태 확인",
                detail: "외부 AI에는 개인정보가 제거된 최소 텍스트만 전달해야 합니다.",
                source: "Java DocumentTextSanitizer",
                evidence: "원문 전체와 제거된 개인정보는 진행 화면에 표시하지 않습니다.",
                tokens: 0,
                durationMs: 500
            },
            {
                title: "기술·경력 후보 추출",
                detail: "Python 분석 API 연결 전이므로 외부 AI를 호출하지 않고 샘플 이벤트만 표시합니다.",
                source: "샘플 모드 · 외부 검색 미실행",
                evidence: "실제 연결 후 후보별 원문 근거 식별자를 표시할 예정입니다.",
                tokens: 680,
                durationMs: 900
            },
            {
                title: "채용 조건과 의미 유사도 비교",
                detail: "채용공고 입력 계약이 연결되면 검색어, 확인한 URL과 비교 근거를 여기에 표시합니다.",
                source: "샘플 모드 · 외부 검색 미실행",
                evidence: "현재 화면의 유사도와 조건 결과는 UI 확인용 샘플입니다.",
                tokens: 920,
                durationMs: 1000
            },
            {
                title: "근거와 결과 정리",
                detail: "확인된 사실, AI 해석과 확인 필요 항목을 분리합니다.",
                source: "프론트 샘플 결과",
                evidence: "실제 서버 결과가 연결되기 전에는 사용자 판정에 사용하지 않습니다.",
                tokens: 180,
                durationMs: 500
            }
        ];
    }

    async function startAnalysis() {
        if (!state.document
                || ["RUNNING", "PAUSED", "WAITING_FOR_USER"].includes(state.analysis.status)) {
            return;
        }

        elements.progressLog.innerHTML = "";
        resetAnalysisRun();
        showOnly(elements.analysisProgressView);

        try {
            for (const step of sampleAnalysisSteps()) {
                await runAnalysisStep(step);
            }

            setAnalysisStatus("COMPLETED");
            elements.pauseAnalysisButton.disabled = true;
            elements.cancelAnalysisButton.disabled = true;
            renderAnalysisResult();
            await sleep(300);
            showOnly(elements.analysisResultView);
        } catch (error) {
            if (error instanceof AnalysisCancelledError) {
                setAnalysisStatus("CANCELLED");
                const cancelledEntry = appendLogEntry({
                    title: "분석 중단 완료",
                    detail: "아직 시작하지 않은 다음 샘플 단계는 실행하지 않았습니다.",
                    source: "사용자 제어",
                    evidence: "실제 AI 연결 후에는 이미 사용된 토큰은 복구되지 않습니다.",
                    tokens: 0
                });
                markLogEntry(cancelledEntry, "cancelled", 0);
            } else {
                setAnalysisStatus("FAILED");
                showGlobalMessage("분석 진행 화면에서 오류가 발생했습니다.", true);
            }
            elements.pauseAnalysisButton.disabled = true;
            elements.cancelAnalysisButton.disabled = true;
        }
    }

    function toggleAnalysisPause() {
        if (!["RUNNING", "PAUSED", "WAITING_FOR_USER"].includes(state.analysis.status)) {
            return;
        }
        if (state.analysis.waitingForBudget) {
            elements.analysisLiveStatus.textContent =
                "샘플 모드 · 토큰 예산을 조정해야 계속할 수 있습니다.";
            return;
        }

        state.analysis.paused = !state.analysis.paused;
        elements.pauseAnalysisButton.textContent =
            state.analysis.paused ? "계속 진행" : "일시정지";
        setAnalysisStatus(state.analysis.paused ? "PAUSED" : "RUNNING");
    }

    function cancelAnalysis() {
        if (!["RUNNING", "PAUSED", "WAITING_FOR_USER"].includes(state.analysis.status)) {
            return;
        }
        state.analysis.cancelled = true;
        state.analysis.paused = false;
        elements.pauseAnalysisButton.disabled = true;
        elements.cancelAnalysisButton.disabled = true;
        setAnalysisStatus("CANCEL_REQUESTED");
    }

    function openAnalysisSettings() {
        elements.analysisInstructionInput.value = state.analysis.instruction;
        elements.tokenBudgetInput.value = String(state.analysis.tokenBudget);
        if (typeof elements.analysisAdjustDialog.showModal === "function") {
            elements.analysisAdjustDialog.showModal();
        } else {
            elements.analysisAdjustDialog.setAttribute("open", "");
        }
    }

    function closeAnalysisSettings() {
        if (typeof elements.analysisAdjustDialog.close === "function") {
            elements.analysisAdjustDialog.close();
        } else {
            elements.analysisAdjustDialog.removeAttribute("open");
        }
    }

    function saveAnalysisSettings(event) {
        event.preventDefault();
        if (!elements.analysisAdjustForm.reportValidity()) {
            return;
        }

        state.analysis.instruction = elements.analysisInstructionInput.value.trim();
        state.analysis.tokenBudget = Number(elements.tokenBudgetInput.value);
        updateRunMetrics();

        if (state.analysis.waitingForBudget
                && state.analysis.tokenUsed < state.analysis.tokenBudget) {
            elements.analysisLiveStatus.textContent =
                "샘플 모드 · 조정된 예산을 다음 단계 전에 확인합니다.";
        }
        closeAnalysisSettings();
    }

    function returnToDashboardFromProgress() {
        if (["RUNNING", "PAUSED", "WAITING_FOR_USER"].includes(state.analysis.status)) {
            cancelAnalysis();
        }
        showOnly(elements.dashboardView);
    }

    function renderAnalysisResult() {
        const checklistItems = [
            {label: "3년 이상 백엔드 개발 경험", met: true},
            {label: "Spring Boot 기반 서비스 운영 경험", met: true},
            {label: "클라우드 인프라 운영 경험", met: false}
        ];
        elements.resultChecklist.innerHTML = checklistItems.map((item) => `
            <li class="checklist-item ${item.met ? "is-met" : "is-missing"}">
                <span class="checklist-icon" aria-hidden="true">
                    ${item.met
                        ? '<svg viewBox="0 0 24 24"><path d="m5 13 4 4 10-10"></path></svg>'
                        : '<svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6 6 18"></path></svg>'}
                </span>
                <span>${item.label}</span>
            </li>
        `).join("");

        const similarityItems = [
            {label: "백엔드 개발", score: 82},
            {label: "클라우드·인프라", score: 54},
            {label: "AI·LLM 활용", score: 38}
        ];
        elements.resultSimilarity.innerHTML = similarityItems.map((item) => `
            <div class="similarity-row">
                <div class="similarity-label">
                    <span>${item.label}</span>
                    <span>${item.score}%</span>
                </div>
                <div class="similarity-track">
                    <div class="similarity-fill" style="width: ${item.score}%"></div>
                </div>
            </div>
        `).join("");

        const skillGaps = ["Kubernetes", "RAG 파이프라인 설계", "클라우드 비용 최적화"];
        elements.resultSkillGaps.innerHTML = "";
        skillGaps.forEach((skill) => {
            const tag = document.createElement("span");
            tag.className = "skill-tag";
            tag.textContent = skill;
            elements.resultSkillGaps.appendChild(tag);
        });

        elements.resultEvidence.innerHTML = "";
        state.analysis.completedSteps.forEach((step) => {
            const item = document.createElement("li");
            const title = document.createElement("strong");
            title.textContent = step.title;
            const evidence = document.createElement("span");
            evidence.textContent = `${step.source} · ${step.evidence}`;
            item.append(title, evidence);
            elements.resultEvidence.appendChild(item);
        });
    }

    function documentTypeLabel(documentType) {
        return documentType === "PORTFOLIO" ? "포트폴리오" : "이력서";
    }

    function formatDate(value) {
        if (!value) {
            return "등록 시각 확인 불가";
        }
        return new Intl.DateTimeFormat("ko-KR", {
            dateStyle: "medium",
            timeStyle: "short"
        }).format(new Date(value));
    }

    function shortCommit(commitSha) {
        return commitSha ? commitSha.slice(0, 12) : "-";
    }

    elements.documentForm.addEventListener("submit", registerDocument);
    elements.githubForm.addEventListener("submit", registerGitHubRepository);
    elements.logoutButton.addEventListener("click", logout);
    elements.startAnalysisButton.addEventListener("click", startAnalysis);
    elements.pauseAnalysisButton.addEventListener("click", toggleAnalysisPause);
    elements.adjustAnalysisButton.addEventListener("click", openAnalysisSettings);
    elements.cancelAnalysisButton.addEventListener("click", cancelAnalysis);
    elements.progressDashboardButton.addEventListener("click", returnToDashboardFromProgress);
    elements.analysisAdjustForm.addEventListener("submit", saveAnalysisSettings);
    elements.closeAnalysisSettingsButton.addEventListener("click", closeAnalysisSettings);
    elements.backToDashboardButton.addEventListener("click", () => showOnly(elements.dashboardView));

    updateAnalysisAvailability();
    window.addEventListener("pageshow", loadSession);
})();
