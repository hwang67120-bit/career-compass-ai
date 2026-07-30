(() => {
    "use strict";

    const elements = {
        loadingView: document.getElementById("loading-view"),
        loginView: document.getElementById("login-view"),
        dashboardView: document.getElementById("dashboard-view"),
        sessionChip: document.getElementById("session-chip"),
        logoutButton: document.getElementById("logout-button"),
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
        progressLog: document.getElementById("progress-log"),
        analysisResultView: document.getElementById("analysis-result-view"),
        resultChecklist: document.getElementById("result-checklist"),
        resultSimilarity: document.getElementById("result-similarity"),
        resultSkillGaps: document.getElementById("result-skill-gaps"),
        backToDashboardButton: document.getElementById("back-to-dashboard-button")
    };

    const state = {
        document: null,
        github: null
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

            elements.currentUserId.textContent = currentUser.userId;
            elements.currentUserId.title = currentUser.userId;
            elements.sessionChip.classList.remove("is-hidden");
            showOnly(elements.dashboardView);
            await configureSessionActions();
        } catch (error) {
            showOnly(elements.loginView);
            const message = error instanceof ApiRequestError
                ? error.message
                : "서버에 연결할 수 없습니다.";
            console.error(message);
        }
    }

    async function configureSessionActions() {
        try {
            await request("/api/v1/auth/csrf");
            elements.logoutButton.classList.remove("is-hidden");
        } catch (error) {
            if (error instanceof ApiRequestError && error.status === 404) {
                elements.logoutButton.classList.add("is-hidden");
                return;
            }
            console.error("세션 동작을 확인하지 못했습니다.");
        }
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

    function appendLogEntry(text, detail) {
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
        textElement.textContent = text;
        bubble.appendChild(textElement);

        if (detail) {
            const detailElement = document.createElement("p");
            detailElement.className = "chat-detail";
            detailElement.textContent = detail;
            bubble.appendChild(detailElement);
        }

        entry.append(avatar, bubble);
        elements.progressLog.appendChild(entry);
        entry.scrollIntoView({behavior: "smooth", block: "end"});
        return entry;
    }

    function markLogEntryDone(entry) {
        entry.classList.remove("chat-message-active");
        entry.classList.add("chat-message-done");
        entry.querySelector(".chat-avatar").innerHTML =
            '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 13 4 4 10-10"></path></svg>';
    }

    function markLogEntryFailed(entry) {
        entry.classList.remove("chat-message-active");
        entry.classList.add("chat-message-failed");
        entry.querySelector(".chat-avatar").innerHTML =
            '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"></path></svg>';
    }

    function appendLogDetail(entry, detail) {
        if (!detail) {
            return;
        }
        const detailElement = document.createElement("p");
        detailElement.className = "chat-detail";
        detailElement.textContent = detail;
        entry.querySelector(".chat-bubble").appendChild(detailElement);
    }

    async function runStep(text, detail, delayMs) {
        const entry = appendLogEntry(text, detail);
        await sleep(delayMs);
        markLogEntryDone(entry);
    }

    /**
     * 고정 지연이 아니라 실제 비동기 작업(task)의 결과로 성공·실패를 표시하는 단계다.
     * task는 완료 시 상세 문구를 반환하고, 실패하면 Error를 던져야 한다.
     */
    async function runRealStep(text, task) {
        const entry = appendLogEntry(text, null);
        try {
            const detail = await task();
            appendLogDetail(entry, detail);
            markLogEntryDone(entry);
        } catch (error) {
            appendLogDetail(entry, error.message || "확인하지 못했습니다.");
            markLogEntryFailed(entry);
        }
    }

    async function startAnalysis() {
        if (!state.document) {
            return;
        }

        elements.progressLog.innerHTML = "";
        showOnly(elements.analysisProgressView);

        await runStep(
            "이력서 내용 확인",
            `${documentTypeLabel(state.document.documentType)} · ${formatDate(state.document.createdAt)}`,
            700
        );

        if (state.github) {
            await runStep(
                "GitHub 저장소 확인",
                `${state.github.repositoryFullName} · 기본 브랜치 ${state.github.defaultBranch} · ${shortCommit(state.github.commitSha)}`,
                700
            );
        } else {
            await runStep(
                "GitHub 저장소 미등록",
                "이력서 내용만으로 분석을 진행합니다.",
                500
            );
        }

        await runRealStep("Python 서버 연결 확인", async () => {
            const status = await request("/api/v1/system/python-status");
            if (!status?.connected) {
                throw new Error("Python 서버에 연결하지 못했습니다.");
            }
            return `연결됨 · status=${status.status} · modelReady=${status.modelReady}`;
        });

        await runStep(
            "기술 스택·경력 임베딩 계산",
            "Python 분석 서비스 연동 준비 중이라 샘플 값으로 진행합니다.",
            900
        );

        await runStep(
            "채용 조건과 유사도 비교",
            "샘플 값으로 계산 중입니다.",
            900
        );

        await runStep("결과 정리", null, 500);

        renderAnalysisResult();
        showOnly(elements.analysisResultView);
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
        elements.resultSkillGaps.innerHTML = skillGaps
            .map((skill) => `<span class="skill-tag">${skill}</span>`)
            .join("");
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
    elements.backToDashboardButton.addEventListener("click", () => showOnly(elements.dashboardView));

    updateAnalysisAvailability();
    window.addEventListener("pageshow", loadSession);
})();
