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

    class ApiRequestError extends Error {
        constructor(status, payload) {
            const apiError = payload?.error;
            super(apiError?.message || "요청을 처리하는 중 문제가 발생했습니다.");
            this.name = "ApiRequestError";
            this.status = status;
            this.fieldErrors = apiError?.fieldErrors || [];
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
        } catch (error) {
            appendLogDetail(entry, error.message || "확인하지 못했습니다.");
            markLogEntryFailed(entry);
        }
    }

    async function startAnalysis() {
        if (!state.userProfile || state.profileDirty || !state.github) {
            return;
        }
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
        await runRealStep("Python 서버 연결 확인", async () => {
            const status = await request("/api/v1/system/python-status");
            if (!status?.connected) {
                throw new Error("Python 서버에 연결하지 못했습니다.");
            }
            return `연결됨 · status=${status.status} · modelReady=${status.modelReady}`;
        });

        const pendingEntry = appendLogEntry("실제 분석 작업 연결 대기", null);
        appendLogDetail(
            pendingEntry,
            "분석 시작 API와 작업 이벤트가 아직 연결되지 않아 결과를 생성하지 않았습니다."
        );
        markLogEntryFailed(pendingEntry);
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
        () => showOnly(elements.dashboardView)
    );

    updateAnalysisAvailability();
    window.addEventListener("pageshow", loadSession);
})();
