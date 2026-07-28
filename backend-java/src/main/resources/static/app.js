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
        resultCommit: document.getElementById("result-commit")
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
        [elements.loadingView, elements.loginView, elements.dashboardView]
            .forEach((view) => view.classList.toggle("is-hidden", view !== target));
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

    loadSession();
})();
