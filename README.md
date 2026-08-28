# 맛집 리뷰 서비스 (MSA 실습)

Spring Cloud 기반 마이크로서비스 구조로 만든 맛집 리뷰 서비스입니다.
회원 관리와 리뷰 관리를 별도의 서비스로 분리하고, API 게이트웨이를 통해 하나의 진입점으로 요청을 라우팅합니다.

## 구성 서비스

| 서비스 | 포트 | 역할 |
|---|---|---|
| api-gateway | 8080 | 모든 요청의 진입점. 경로에 따라 각 서비스로 라우팅, CORS 처리 |
| member-service | 8081 | 회원 가입, 회원 조회 |
| review-service | 8082 | 리뷰 작성, 조회, 수정, 삭제 |

## 동작 방식

- 클라이언트는 api-gateway(8080)로만 요청을 보냅니다.
- `/api/members/**` 요청은 member-service(8081)로, `/api/reviews/**` 요청은 review-service(8082)로 전달됩니다.
- review-service는 리뷰에 작성자 정보를 붙일 때 Feign Client를 이용해 member-service에 직접 회원 정보를 요청합니다.
- 각 서비스는 자체 데이터베이스(PostgreSQL)를 따로 사용합니다. (`member_db`, `review_db`)

## API

### 회원 (member-service)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | /api/members | 회원가입 |
| GET | /api/members/{id} | ID로 회원 조회 |
| GET | /api/members?nickname= | 닉네임으로 회원 조회 |

### 리뷰 (review-service)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | /api/reviews | 리뷰 작성 |
| GET | /api/reviews | 리뷰 목록 조회 |
| GET | /api/reviews/{id} | 리뷰 상세 조회 |
| PUT | /api/reviews/{id} | 리뷰 수정 |
| DELETE | /api/reviews/{id} | 리뷰 삭제 |

## 사용 기술

- Java 17
- Spring Boot / Spring Cloud Gateway (WebFlux)
- Spring Data JPA
- Spring Cloud OpenFeign (서비스 간 통신)
- PostgreSQL
- Gradle

## 실행 방법

각 서비스를 개별적으로 실행합니다. (PostgreSQL에 `member_db`, `review_db`가 미리 있어야 합니다)

```bash
# 회원 서비스
cd member-service
./gradlew bootRun

# 리뷰 서비스
cd review-service
./gradlew bootRun

# API 게이트웨이
cd api-gateway
./gradlew bootRun
```

실행 후 `http://localhost:8080`으로 API를 호출하면 됩니다.
