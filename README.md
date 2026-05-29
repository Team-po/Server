- Commit Convention
  - feat: 새로운 기능
  - fix: 버그 수정
  - refactor: 리팩토링
  - docs: 문서 수정
  - chore: 기타 작업
  - test: 테스트 코드
  - style: 코드 스타일 수정

## 로컬 실행

### 1. 로컬 인프라 실행

```bash
docker compose up -d
```

기본 포트:

```text
MySQL: localhost:3307
Redis: localhost:6380
```

### 2. 애플리케이션 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

실행 확인:

```bash
curl -i http://localhost:8080/swagger-ui/index.html
```

### 3. 테스트와 빌드

전체 테스트:

```bash
./gradlew test
```

클린 빌드:

```bash
./gradlew clean build
```

Flyway MySQL 연동 테스트는 기본으로 비활성화되어 있습니다. 로컬 MySQL이 실행 중이고 `src/test/resources/application-flyway.yml` 설정을 사용할 때만 아래처럼 실행합니다.

```bash
RUN_DB_TESTS=true ./gradlew test
```
