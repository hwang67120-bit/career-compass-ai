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
        technologyForm: document.getElementById("technology-form"),
        technologyOptions: document.getElementById("technology-options"),
        targetJobTitle: document.getElementById("target-job-title"),
        profileForm: document.getElementById("profile-form"),
        selectedTechnologyTags: document.getElementById("selected-technology-tags"),
        technologyStatus: document.getElementById("technology-status"),
        profileStatus: document.getElementById("profile-status"),
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
        backToDashboardButton: document.getElementById("back-to-dashboard-button")
    };

    const state = {
        selectedTechnologyTags: new Map(),
        userProfile: null,
        profileDirty: false,
        github: null
    };

    /**
     * 2026-08-05 임시 작업(코덱스 사용량 한도 공백기 대응) — 분석 상태 폴링 주기의 운영
     * 정책이 아직 확정되지 않았다(docs/architecture/backend-job-processing-and-sse.md
     * "구현 전에 남은 결정" 참고). 실제 측정 전까지 쓰는 임시값이다.
     */
    const ANALYSIS_POLL_INTERVAL_MS = 3000;

    const JOB_ANALYSIS_TERMINAL_STATUSES = new Set([
        "COMPLETED",
        "PARTIALLY_COMPLETED",
        "FAILED",
        "CANCELLED"
    ]);

    const JOB_ANALYSIS_STEP_LABELS = {
        VALIDATING_INPUTS: "입력값 확인 중",
        ANALYZING_REPOSITORIES: "저장소 분석 중",
        GENERATING_SEARCH_PLAN: "검색 계획 수립 중",
        SEARCHING_JOB_POSTINGS: "채용공고 검색 중",
        EXTRACTING_JOB_POSTINGS: "채용공고 내용 추출 중",
        COMPARING_EVIDENCE: "근거 비교 중",
        FINALIZING_RESULT: "결과 정리 중",
        FINISHED: "완료 처리 중"
    };

    let analysisPollingToken = null;

    function stopAnalysisPolling() {
        if (analysisPollingToken) {
            analysisPollingToken.cancelled = true;
            analysisPollingToken = null;
        }
    }

    function sleep(ms) {
        return new Promise((resolve) => setTimeout(resolve, ms));
    }

    class ApiRequestError extends Error {
        constructor(status, payload) {
            const apiError = payload?.error;
            super(apiError?.message || "요청을 처리하는 중 문제가 발생했습니다.");
            this.name = "ApiRequestError";
            this.status = status;
            this.fieldErrors = apiError?.fieldErrors || [];
        }
    }

    async function request(path, {returnResponse = false, ...fetchOptions} = {}) {
        const response = await fetch(path, {
            credentials: "same-origin",
            ...fetchOptions,
            headers: {
                Accept: "application/json",
                ...(fetchOptions.body ? {"Content-Type": "application/json"} : {}),
                ...(fetchOptions.headers || {})
            }
        });
        const payload = response.status === 204
            ? null
            : await response.json().catch(() => null);
        if (!response.ok) {
            throw new ApiRequestError(response.status, payload);
        }
        const data = payload?.data ?? null;
        return returnResponse ? {data, response} : data;
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

    async function mutation(path, body, method = "POST", {returnResponse = false} = {}) {
        return request(path, {
            method,
            returnResponse,
            headers: await csrfHeaders(),
            ...(body === undefined ? {} : {body: JSON.stringify(body)})
        });
    }

    function showOnly(target) {
        [
            elements.loadingView,
            elements.loginView,
            elements.dashboardView,
            elements.analysisProgressView
        ].forEach((view) => view.classList.toggle("is-hidden", view !== target));
    }

    function updateAnalysisAvailability() {
        const hasSavedProfile = Boolean(state.userProfile) && !state.profileDirty;
        const hasGitHubRepository = Boolean(state.github);
        const ready = hasSavedProfile && hasGitHubRepository;
        elements.startAnalysisButton.disabled = !ready;
        elements.analysisBarIcon.classList.toggle("is-ready", ready);

        if (ready) {
            elements.analysisBarTitle.textContent = "분석 입력이 준비됐어요.";
            elements.analysisBarDescription.textContent =
                "선택한 기술 태그와 공개 GitHub 저장소를 기준으로 분석합니다.";
        } else if (!hasSavedProfile) {
            elements.analysisBarTitle.textContent = "분석 프로필을 저장해 주세요.";
            elements.analysisBarDescription.textContent =
                "희망 직무와 기술 태그를 서버에 저장해야 합니다.";
        } else {
            elements.analysisBarTitle.textContent = "GitHub 저장소를 연결해 주세요.";
            elements.analysisBarDescription.textContent =
                "분석 근거로 사용할 공개 저장소가 필요합니다.";
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

    function updateStatus(element, status, title, description) {
        element.className = `status-box status-${status}`;
        element.querySelector("strong").textContent = title;
        element.querySelector("p").textContent = description;
    }

    function setFormBusy(form, busy) {
        const button = form.querySelector("button[type='submit']");
        const label = button.querySelector(".button-label");
        const spinner = button.querySelector(".button-spinner");
        form.querySelectorAll("input, button").forEach((control) => {
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
        updateStatus(
            statusElement,
            "error",
            "요청 실패",
            fieldMessage || error.message || "잠시 후 다시 시도해 주세요."
        );
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
            await loadUserProfile();
            await searchTechnologyTags();
        } catch (error) {
            showOnly(elements.loginView);
            console.error(error.message || "서버에 연결할 수 없습니다.");
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

    async function searchTechnologyTags(event) {
        event?.preventDefault();
        clearGlobalMessage();
        const query = String(new FormData(elements.technologyForm).get("query") || "").trim();
        setFormBusy(elements.technologyForm, true);
        updateStatus(elements.technologyStatus, "loading", "검색 중", "표준 기술 태그를 조회하고 있어요.");

        try {
            const result = await request(
                `/api/v1/technology-tags?query=${encodeURIComponent(query)}`
            );
            renderTechnologyOptions(result?.technologyTags || []);
            updateTechnologyStatus();
        } catch (error) {
            displayError(elements.technologyStatus, error);
        } finally {
            setFormBusy(elements.technologyForm, false);
        }
    }

    function renderTechnologyOptions(technologyTags) {
        elements.technologyOptions.innerHTML = "";
        if (technologyTags.length === 0) {
            elements.technologyOptions.innerHTML =
                '<span class="empty-tag-message">검색 결과가 없습니다.</span>';
            return;
        }
        technologyTags.forEach((technologyTag) => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "technology-tag-button";
            button.dataset.technologyTagId = technologyTag.technologyTagId;
            button.textContent = technologyTag.displayName;
            button.classList.toggle("is-selected", hasSelectedStandardTag(
                technologyTag.technologyTagId
            ));
            button.addEventListener("click", () => toggleTechnologyTag(technologyTag));
            elements.technologyOptions.appendChild(button);
        });
    }

    function toggleTechnologyTag(technologyTag) {
        const key = standardTechnologyTagKey(technologyTag.technologyTagId);
        if (state.selectedTechnologyTags.has(key)) {
            state.selectedTechnologyTags.delete(key);
        } else {
            removeCustomAliasOf(technologyTag.technologyTagId);
            state.selectedTechnologyTags.set(key, {
                ...technologyTag,
                rawName: technologyTag.displayName,
                normalizedName: technologyTag.key,
                sourceType: "USER_SELECTED"
            });
        }
        markProfileDirty();
        renderSelectedTechnologyTags();
        syncTechnologyOptionSelection();
        updateTechnologyStatus();
        updateAnalysisAvailability();
    }

    function renderSelectedTechnologyTags() {
        elements.selectedTechnologyTags.innerHTML = "";
        if (state.selectedTechnologyTags.size === 0) {
            elements.selectedTechnologyTags.innerHTML =
                '<span class="empty-tag-message">선택한 기술이 없습니다.</span>';
            return;
        }
        state.selectedTechnologyTags.forEach((technologyTag) => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "technology-tag-button is-selected";
            button.textContent = `${technologyTag.displayName} ×`;
            button.addEventListener("click", () =>
                removeSelectedTechnologyTag(technologyTag));
            elements.selectedTechnologyTags.appendChild(button);
        });
    }

    function updateTechnologyStatus() {
        if (state.selectedTechnologyTags.size === 0) {
            updateStatus(
                elements.technologyStatus,
                "idle",
                "선택 전",
                "분석에 사용할 기술을 하나 이상 선택해 주세요."
            );
            return;
        }
        updateStatus(
            elements.technologyStatus,
            "success",
            "선택 완료",
            `${state.selectedTechnologyTags.size}개 기술이 선택됐습니다.`
        );
    }

    function standardTechnologyTagKey(technologyTagId) {
        return `standard:${technologyTagId}`;
    }

    function customTechnologyTagKey(technologyTag) {
        return `custom:${technologyTag.normalizedName || technologyTag.rawName}`;
    }

    function hasSelectedStandardTag(technologyTagId) {
        return state.selectedTechnologyTags.has(
            standardTechnologyTagKey(technologyTagId)
        );
    }

    function removeCustomAliasOf(technologyTagId) {
        state.selectedTechnologyTags.forEach((technologyTag, key) => {
            if (technologyTag.sourceType === "USER_CUSTOM"
                && technologyTag.technologyTagId === technologyTagId) {
                state.selectedTechnologyTags.delete(key);
            }
        });
    }

    function removeSelectedTechnologyTag(technologyTag) {
        const key = technologyTag.sourceType === "USER_CUSTOM"
            ? customTechnologyTagKey(technologyTag)
            : standardTechnologyTagKey(technologyTag.technologyTagId);
        state.selectedTechnologyTags.delete(key);
        markProfileDirty();
        renderSelectedTechnologyTags();
        syncTechnologyOptionSelection();
        updateTechnologyStatus();
        updateAnalysisAvailability();
    }

    function syncTechnologyOptionSelection() {
        elements.technologyOptions
            .querySelectorAll("[data-technology-tag-id]")
            .forEach((button) => button.classList.toggle(
                "is-selected",
                hasSelectedStandardTag(button.dataset.technologyTagId)
            ));
    }

    function markProfileDirty() {
        state.profileDirty = true;
        updateProfileStatus();
    }

    function updateProfileStatus() {
        if (state.userProfile && !state.profileDirty) {
            updateStatus(
                elements.profileStatus,
                "success",
                `프로필 버전 ${state.userProfile.version} 저장됨`,
                "희망 직무와 기술 태그가 서버에 저장됐습니다."
            );
            return;
        }
        updateStatus(
            elements.profileStatus,
            "idle",
            state.userProfile ? "변경사항 저장 필요" : "프로필 저장 전",
            "희망 직무와 선택한 기술 태그를 저장해 주세요."
        );
    }

    async function loadUserProfile() {
        try {
            applyUserProfile(await request("/api/v1/user-profile"));
        } catch (error) {
            if (error instanceof ApiRequestError && error.status === 404) {
                state.userProfile = null;
                state.profileDirty = false;
                state.selectedTechnologyTags.clear();
                elements.targetJobTitle.value = "";
                renderSelectedTechnologyTags();
                updateTechnologyStatus();
                updateProfileStatus();
                updateAnalysisAvailability();
                return;
            }
            throw error;
        }
    }

    function applyUserProfile(userProfile) {
        state.userProfile = userProfile;
        state.profileDirty = false;
        state.selectedTechnologyTags.clear();
        elements.targetJobTitle.value = userProfile.targetJobTitle;
        userProfile.technologyTags.forEach((technologyTag) => {
            const key = technologyTag.sourceType === "USER_CUSTOM"
                ? customTechnologyTagKey(technologyTag)
                : standardTechnologyTagKey(technologyTag.technologyTagId);
            state.selectedTechnologyTags.set(key, technologyTag);
        });
        renderSelectedTechnologyTags();
        syncTechnologyOptionSelection();
        updateTechnologyStatus();
        updateProfileStatus();
        updateAnalysisAvailability();
    }

    async function saveUserProfile(event) {
        event.preventDefault();
        clearGlobalMessage();
        const targetJobTitle = elements.targetJobTitle.value.trim();
        if (!targetJobTitle) {
            updateStatus(
                elements.profileStatus,
                "error",
                "희망 직무 입력 필요",
                "분석할 개발 직무를 입력해 주세요."
            );
            elements.targetJobTitle.focus();
            return;
        }
        if (state.selectedTechnologyTags.size === 0) {
            updateStatus(
                elements.profileStatus,
                "error",
                "기술 태그 선택 필요",
                "보유 기술을 하나 이상 선택해 주세요."
            );
            return;
        }

        setFormBusy(elements.profileForm, true);
        updateStatus(
            elements.profileStatus,
            "loading",
            "프로필 저장 중",
            "희망 직무와 기술 태그를 서버에 저장하고 있어요."
        );
        const requestBody = {
            ...(state.userProfile
                ? {expectedVersion: state.userProfile.version}
                : {}),
            targetJobTitle,
            technologyTags: [...state.selectedTechnologyTags.values()]
                .map((technologyTag) => technologyTag.sourceType === "USER_CUSTOM"
                    ? {
                        technologyTagId: null,
                        customName: technologyTag.rawName
                    }
                    : {
                        technologyTagId: technologyTag.technologyTagId,
                        customName: null
                    })
        };

        try {
            const userProfile = await mutation(
                "/api/v1/user-profile",
                requestBody,
                "PUT"
            );
            applyUserProfile(userProfile);
            showGlobalMessage(
                `분석 프로필 버전 ${userProfile.version}이 저장됐습니다.`
            );
        } catch (error) {
            if (error instanceof ApiRequestError && error.status === 409) {
                updateStatus(
                    elements.profileStatus,
                    "error",
                    "프로필 버전 충돌",
                    "다른 변경이 먼저 저장됐습니다. 새로고침 후 다시 시도해 주세요."
                );
            } else {
                displayError(elements.profileStatus, error);
            }
        } finally {
            setFormBusy(elements.profileForm, false);
        }
    }

    async function registerGitHubRepository(event) {
        event.preventDefault();
        clearGlobalMessage();
        elements.githubResult.classList.add("is-hidden");
        const repositoryUrl = String(
            new FormData(elements.githubForm).get("repositoryUrl") || ""
        ).trim();
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
            "GitHub에서 공개 저장소 정보를 확인하고 있어요."
        );

        try {
            const result = await mutation("/api/v1/project-sources/github", {repositoryUrl});
            state.github = result;
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
            showGlobalMessage(`GitHub 프로젝트 등록 완료: ${result.repositoryFullName}`);
            updateAnalysisAvailability();
        } catch (error) {
            displayError(elements.githubStatus, error);
        } finally {
            setFormBusy(elements.githubForm, false);
        }
    }

    async function logout() {
        stopAnalysisPolling();
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
        avatar.innerHTML = '<span class="spinner" aria-hidden="true"></span>';
        const bubble = document.createElement("div");
        bubble.className = "chat-bubble";
        const textElement = document.createElement("p");
        textElement.className = "chat-text";
        textElement.textContent = text;
        bubble.appendChild(textElement);
        appendLogDetailToBubble(bubble, detail);
        entry.append(avatar, bubble);
        elements.progressLog.appendChild(entry);
        return entry;
    }

    function appendLogDetailToBubble(bubble, detail) {
        if (!detail) {
            return;
        }
        const detailElement = document.createElement("p");
        detailElement.className = "chat-detail";
        detailElement.textContent = detail;
        bubble.appendChild(detailElement);
    }

    function appendLogDetail(entry, detail) {
        appendLogDetailToBubble(entry.querySelector(".chat-bubble"), detail);
    }

    function setLogEntryText(entry, text) {
        entry.querySelector(".chat-text").textContent = text;
    }

    function setLogEntryDetail(entry, detail) {
        const bubble = entry.querySelector(".chat-bubble");
        let detailElement = bubble.querySelector(".chat-detail");
        if (!detail) {
            detailElement?.remove();
            return;
        }
        if (!detailElement) {
            detailElement = document.createElement("p");
            detailElement.className = "chat-detail";
            bubble.appendChild(detailElement);
        }
        detailElement.textContent = detail;
    }

    function markLogEntryDone(entry) {
        entry.classList.remove("chat-message-active");
        entry.classList.add("chat-message-done");
        entry.querySelector(".chat-avatar").textContent = "✓";
    }

    function markLogEntryFailed(entry) {
        entry.classList.remove("chat-message-active");
        entry.classList.add("chat-message-failed");
        entry.querySelector(".chat-avatar").textContent = "×";
    }

    function appendCompletedStep(text, detail) {
        const entry = appendLogEntry(text, detail);
        markLogEntryDone(entry);
    }

    async function runRealStep(text, task) {
        const entry = appendLogEntry(text, null);
        try {
            appendLogDetail(entry, await task());
            markLogEntryDone(entry);
            return true;
        } catch (error) {
            appendLogDetail(entry, error.message || "확인하지 못했습니다.");
            markLogEntryFailed(entry);
            return false;
        }
    }

    async function startAnalysis() {
        if (!state.userProfile || state.profileDirty || !state.github) {
            return;
        }
        stopAnalysisPolling();
        elements.progressLog.innerHTML = "";
        showOnly(elements.analysisProgressView);

        appendCompletedStep(
            "기술 태그 확인",
            [...state.selectedTechnologyTags.values()]
                .map((technologyTag) => technologyTag.displayName)
                .join(", ")
        );
        appendCompletedStep(
            "분석 프로필 확인",
            `${state.userProfile.targetJobTitle} · 버전 ${state.userProfile.version}`
        );
        appendCompletedStep(
            "GitHub 저장소 확인",
            `${state.github.repositoryFullName} · ${state.github.defaultBranch} · ${shortCommit(state.github.commitSha)}`
        );
        const pythonReady = await runRealStep("Python 서버 연결 확인", async () => {
            const status = await request("/api/v1/system/python-status");
            if (!status?.connected) {
                throw new Error("Python 서버에 연결하지 못했습니다.");
            }
            return `연결됨 · status=${status.status} · modelReady=${status.modelReady}`;
        });
        if (!pythonReady) {
            return;
        }

        const jobAnalysisId = await requestJobAnalysis();
        if (!jobAnalysisId) {
            return;
        }

        const statusEntry = appendLogEntry("분석 상태 확인 중", null);
        await pollJobAnalysis(jobAnalysisId, statusEntry);
    }

    async function requestJobAnalysis() {
        const requestEntry = appendLogEntry("분석 작업 생성 요청", null);
        try {
            const requestBody = {
                userProfileId: state.userProfile.userProfileId,
                userProfileVersion: state.userProfile.version,
                projectSourceIds: [state.github.projectSourceId]
            };
            const {response} = await mutation(
                "/api/v1/job-analyses",
                requestBody,
                "POST",
                {returnResponse: true}
            );
            const location = response.headers.get("Location");
            const jobAnalysisId = location ? location.split("/").pop() : null;
            if (!jobAnalysisId) {
                throw new Error("응답에 분석 작업 위치 정보가 없습니다.");
            }
            appendLogDetail(requestEntry, `분석 작업이 생성됐습니다 (${jobAnalysisId}).`);
            markLogEntryDone(requestEntry);
            return jobAnalysisId;
        } catch (error) {
            appendLogDetail(requestEntry, error.message || "분석 작업을 시작하지 못했습니다.");
            markLogEntryFailed(requestEntry);
            return null;
        }
    }

    /**
     * 이전 요청이 끝난 뒤에만 다음 조회를 보낸다(중첩 polling 금지). 로그아웃·화면
     * 이탈·새 분석 시작 시 stopAnalysisPolling()이 token.cancelled를 표시해 이 루프를
     * 안전하게 멈춘다.
     */
    async function pollJobAnalysis(jobAnalysisId, statusEntry) {
        const token = {cancelled: false};
        analysisPollingToken = token;

        while (!token.cancelled) {
            let jobAnalysis;
            try {
                jobAnalysis = await request(`/api/v1/job-analyses/${jobAnalysisId}`);
            } catch (error) {
                if (token.cancelled) {
                    return;
                }
                setLogEntryText(statusEntry, "분석 상태 확인 실패");
                setLogEntryDetail(
                    statusEntry,
                    error.message || "네트워크 오류로 상태를 확인하지 못했습니다."
                );
                markLogEntryFailed(statusEntry);
                return;
            }
            if (token.cancelled) {
                return;
            }
            renderJobAnalysisState(statusEntry, jobAnalysis);
            if (JOB_ANALYSIS_TERMINAL_STATUSES.has(jobAnalysis.analysisStatus)) {
                if (analysisPollingToken === token) {
                    analysisPollingToken = null;
                }
                return;
            }
            await sleep(ANALYSIS_POLL_INTERVAL_MS);
        }
    }

    function renderJobAnalysisState(statusEntry, jobAnalysis) {
        const {analysisStatus, currentStep, postings} = jobAnalysis;
        switch (analysisStatus) {
            case "QUEUED":
                setLogEntryText(statusEntry, "분석 작업 대기 중");
                setLogEntryDetail(statusEntry, "워커가 작업을 아직 선점하지 않았습니다.");
                break;
            case "RUNNING":
                setLogEntryText(statusEntry, "분석 진행 중");
                setLogEntryDetail(
                    statusEntry,
                    JOB_ANALYSIS_STEP_LABELS[currentStep] || currentStep
                );
                break;
            case "CANCELLATION_REQUESTED":
                setLogEntryText(statusEntry, "취소 처리 중");
                setLogEntryDetail(statusEntry, "취소 요청을 반영하고 있습니다.");
                break;
            case "PARTIALLY_COMPLETED":
                setLogEntryText(statusEntry, "일부 완료");
                setLogEntryDetail(statusEntry, describePostings(postings, true));
                markLogEntryDone(statusEntry);
                break;
            case "COMPLETED":
                setLogEntryText(statusEntry, "분석 완료");
                setLogEntryDetail(statusEntry, describePostings(postings, false));
                markLogEntryDone(statusEntry);
                break;
            case "FAILED":
                setLogEntryText(statusEntry, "분석 실패");
                setLogEntryDetail(statusEntry, "분석 작업이 실패했습니다.");
                markLogEntryFailed(statusEntry);
                break;
            case "CANCELLED":
                setLogEntryText(statusEntry, "분석 취소됨");
                setLogEntryDetail(statusEntry, "사용자 요청으로 분석이 취소됐습니다.");
                markLogEntryFailed(statusEntry);
                break;
            default:
                setLogEntryText(statusEntry, analysisStatus);
                setLogEntryDetail(statusEntry, null);
        }
    }

    function describePostings(postings, isPartial) {
        if (!postings || postings.length === 0) {
            return isPartial
                ? "성공적으로 추출된 채용공고가 없습니다."
                : "저장된 채용공고 결과가 없습니다.";
        }
        const summary = postings
            .map((posting) =>
                `${posting.companyName || "회사명 미상"} · ${posting.originalJobTitle || "직무명 미상"}`)
            .join(", ");
        return `${postings.length}건 추출: ${summary}`;
    }

    function shortCommit(commitSha) {
        return commitSha ? commitSha.slice(0, 12) : "-";
    }

    elements.technologyForm.addEventListener("submit", searchTechnologyTags);
    elements.profileForm.addEventListener("submit", saveUserProfile);
    elements.targetJobTitle.addEventListener("input", () => {
        markProfileDirty();
        updateAnalysisAvailability();
    });
    elements.githubForm.addEventListener("submit", registerGitHubRepository);
    elements.logoutButton.addEventListener("click", logout);
    elements.startAnalysisButton.addEventListener("click", startAnalysis);
    elements.backToDashboardButton.addEventListener(
        "click",
        () => {
            stopAnalysisPolling();
            showOnly(elements.dashboardView);
        }
    );

    updateAnalysisAvailability();
    window.addEventListener("pageshow", loadSession);
})();
