# Postman API 확인

이 폴더의 Collection과 환경 파일을 Postman에 Import하면 사용자가 실제 요청과 응답을 직접 확인할 수 있다.

## 가져오기

1. Postman에서 `Import`를 선택한다.
2. `career-compass-api.postman_collection.json`을 가져온다.
3. `career-compass-linux-dev.postman_environment.json`을 가져온다.
4. 오른쪽 위 환경을 `Career Compass Linux Dev`로 선택한다.

## 실행 순서

1. `서버 상태 확인`을 보내고 `200`, `UP`을 확인한다.
2. `GitHub 공개 저장소 등록 - 201`을 보낸다.
3. 응답의 `repositoryFullName`, `defaultBranch`, `commitSha`, `REGISTERED`를 확인한다.
4. Postman 환경의 `projectSourceId`, `commitSha`가 저장됐는지 확인한다.
5. 400과 404 요청도 각각 실행해 오류 응답을 확인한다.

기본 공개 저장소는 `https://github.com/octocat/Hello-World`다. 본인 저장소를 시험하려면 환경의 `githubRepositoryUrl`만 변경한다.

## 현재 범위

- 공개 GitHub 저장소만 허용한다.
- GitHub API를 읽기 전용으로 호출한다.
- 등록 시점의 기본 브랜치와 최신 `commitSha`를 저장한다.
- 자동 변경 감지와 자동 재분석은 하지 않는다.
- 같은 URL 반복 등록 정책은 아직 확정되지 않았다.
