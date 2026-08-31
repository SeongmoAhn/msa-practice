# 맛집 리뷰 서비스 (MSA 실습)

Spring Cloud 기반 마이크로서비스 구조로 만든 맛집 리뷰 서비스입니다.
회원 관리와 리뷰 관리를 별도의 서비스로 분리하고, API 게이트웨이를 통해 하나의 진입점으로 요청을 라우팅합니다.

## 구성 서비스

| 서비스 | 포트 | 역할 |
|---|---|---|
| eureka-server | 8761 | 서비스 디스커버리 (각 서비스 등록/조회) |
| api-gateway | 8080 | 모든 요청의 진입점. 경로에 따라 각 서비스로 라우팅 |
| member-service | 8081 | 회원 가입, 회원 조회 |
| review-service | 8082 | 리뷰 작성, 조회, 수정, 삭제 |
| member-db / review-db | - | 각 서비스 전용 PostgreSQL (docker-compose 내부 네트워크에서만 접근 가능) |

## 동작 방식

- 모든 서비스는 기동 시 eureka-server(8761)에 자신을 등록합니다.
- 클라이언트는 api-gateway(8080)로만 요청을 보냅니다.
- `/api/members/**` 요청은 `lb://MEMBER-SERVICE`로, `/api/reviews/**` 요청은 `lb://REVIEW-SERVICE`로 Eureka를 통해 라우팅됩니다.
- review-service는 리뷰에 작성자 정보를 붙일 때 Feign Client(`MemberServiceClient`)를 이용해 member-service에 직접 회원 정보를 요청합니다. 즉 리뷰를 작성하려면 `memberId`에 해당하는 회원이 미리 등록되어 있어야 합니다.
- member-service 호출은 `MemberClientWrapper`가 감싸고 있고, Resilience4j Circuit Breaker(`member-service`)가 적용되어 있습니다. member-service 호출이 반복적으로 실패하면 회로가 열려(open) 즉시 fallback으로 `"알 수 없는 사용자"`를 반환하고, member-service로의 실제 호출은 잠시 차단됩니다.
- review-service는 로드밸런싱 실습을 위해 컨테이너 2개(`review-review-service-1`, `-2`)로 띄워두고 있습니다. `ReviewController.getReview()`에서 요청을 처리한 컨테이너의 hostname을 로그로 남겨 어느 인스턴스가 응답했는지 확인할 수 있습니다.
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
- Spring Boot 4.1.1 / Spring Cloud 2025.1.3
- Spring Cloud Gateway (`spring-cloud-starter-gateway-server-webflux`)
- Spring Cloud Netflix Eureka (서비스 디스커버리)
- Spring Data JPA
- Spring Cloud OpenFeign (서비스 간 통신)
- Resilience4j (`spring-cloud-starter-circuitbreaker-resilience4j`) — member-service 호출 Circuit Breaker
- PostgreSQL
- Gradle / Docker Compose

## 실행 방법

### Docker Compose로 전체 실행 (권장)

각 서비스 모듈의 Dockerfile은 소스를 빌드하지 않고 **호스트에 이미 빌드된 jar(`build/libs/*.jar`)를 그대로 복사**하는 방식입니다. 따라서 이미지를 빌드하기 전에 반드시 Gradle 빌드를 먼저 해야 합니다.

```bash
# 1. 각 서비스 jar 빌드 (코드를 수정했다면 매번 다시 실행)
cd eureka-server && ./gradlew clean build -x test && cd ..
cd member-service && ./gradlew clean build -x test && cd ..
cd review-service && ./gradlew clean build -x test && cd ..
cd api-gateway && ./gradlew clean build -x test && cd ..

# 2. 이미지 빌드 + 컨테이너 기동
docker compose up -d --build
```

실행 후 `http://localhost:8080`으로 API를 호출하면 됩니다. Eureka 대시보드는 `http://localhost:8761`에서 확인할 수 있습니다.

### 로컬에서 개별 실행

PostgreSQL에 `member_db`, `review_db`가 미리 준비되어 있어야 합니다.

```bash
# 유레카 서버
cd eureka-server
./gradlew bootRun

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

## 장애 대응: Circuit Breaker

review-service가 member-service를 Feign으로 호출하는 부분(`ReviewService` → `MemberClientWrapper` → `MemberServiceClient`)에 Resilience4j Circuit Breaker를 적용했습니다.

- `review-service/src/main/resources/application.yaml`에 `member-service` 인스턴스로 설정 등록:
  ```yaml
  resilience4j:
    circuitbreaker:
      instances:
        member-service:
          sliding-window-size: 5
          failure-rate-threshold: 50
          wait-duration-in-open-state: 30s
          permitted-number-of-calls-in-half-open-state: 3
  ```
- `MemberClientWrapper.getMember()`에 `@CircuitBreaker(name = "member-service", fallbackMethod = "getMemberFallback")`를 붙여서, 최근 5번 호출 중 50% 이상 실패하면 회로가 열립니다(open). 열린 상태에서는 실제 member-service 호출 없이 바로 `getMemberFallback()`이 실행되어 `"알 수 없는 사용자"`로 채워진 `MemberDto`를 반환합니다. 30초 후 half-open 상태로 전환해 3번까지 실제 호출을 시도해보고 성공률에 따라 다시 닫히거나 열립니다.
- member-service가 죽어도 review-service의 리뷰 조회/작성 자체는 500으로 죽지 않고 "알 수 없는 사용자"로 표시된 응답을 계속 내려줄 수 있습니다.

## 트러블슈팅 기록

`eureka-api.http`로 API를 테스트하며 겪었던 문제와 원인을 정리합니다. 비슷한 증상이 재발했을 때 참고용입니다.

### 1. `GET https://localhost:8080/...` 요청이 응답 없이 실패함

- **증상**: 회원 조회 요청에서 연결이 안 됨.
- **원인**: 요청 URL에 `http` 대신 `https`가 적혀 있었음. api-gateway는 8080 포트에서 TLS 없이 평문 HTTP만 서비스하므로 TLS 핸드셰이크 단계에서 실패.
- **해결**: `https://` → `http://`로 수정.

### 2. `/api/members/**`, `/api/reviews/**` 등 모든 경로가 즉시 404를 반환함 (백엔드 호출 흔적 없음)

- **증상**: `GET /api/members/1`뿐 아니라 `POST /api/members`, `/actuator` 등 게이트웨이의 **모든 경로**가 404. 응답 시간이 매우 짧고(수 ms) member-service 쪽 로그에는 아무 요청도 찍히지 않음. Eureka에는 `MEMBER-SERVICE`, `REVIEW-SERVICE`가 정상 등록되어 있어 서비스 디스커버리 자체는 문제 없었음.
- **원인**: `api-gateway/src/main/resources/application.yaml`의 라우트 설정이 `spring.cloud.gateway.routes` 경로로 되어 있었는데, 사용 중인 `spring-cloud-starter-gateway-server-webflux` (Spring Cloud Gateway 5.x)에서는 해당 프로퍼티가 `spring.cloud.gateway.server.webflux.routes`로 이름이 바뀜. 실제로 해당 jar를 까보면 `GatewayProperties` 클래스가 `@ConfigurationProperties("spring.cloud.gateway.server.webflux")`로 선언되어 있음. 잘못된 prefix로 작성된 라우트 설정은 조용히 무시되어 게이트웨이에 라우트가 하나도 로드되지 않았고, 그 결과 모든 요청이 매칭되는 라우트 없이 기본 404로 응답됨.
- **해결**: `application.yaml`에서 `spring.cloud.gateway.routes` 아래에 있던 라우트 설정을 `spring.cloud.gateway.server.webflux.routes`로 한 단계 더 들여서 이동.

  ```yaml
  spring:
    cloud:
      gateway:
        server:
          webflux:
            routes:
              - id: member-service
                uri: lb://MEMBER-SERVICE
                predicates:
                  - Path=/api/members/**
              - id: review-service
                uri: lb://REVIEW-SERVICE
                predicates:
                  - Path=/api/reviews/**
  ```

### 3. `application.yaml`을 고치고 `docker compose up -d --build`를 했는데도 그대로 404가 남

- **증상**: 위 설정을 수정한 뒤 재빌드/재기동했는데도 동일한 404가 계속 발생.
- **원인**: `api-gateway/Dockerfile`이 소스를 컴파일하는 게 아니라 `COPY build/libs/*.jar app.jar`로 **호스트에 이미 만들어진 jar를 그대로 복사**하는 구조. `docker compose --build`는 이미지 레이어만 다시 만들 뿐 Gradle 빌드는 실행하지 않기 때문에, `application.yaml`을 수정하기 전에 만들어진 옛날 jar가 그대로 이미지에 들어감.
- **해결**: 이미지를 빌드하기 전에 `./gradlew clean build -x test`로 jar를 먼저 새로 만들어야 함 (위 "Docker Compose로 전체 실행" 절차 참고). 다른 서비스(`member-service`, `review-service`, `eureka-server`)의 Dockerfile도 동일한 구조이므로 코드를 바꿀 때마다 항상 해당 모듈을 먼저 재빌드해야 함.

### 4. 게이트웨이 라우팅 성공 후 `GET /api/members/1`에서 500 Internal Server Error

- **증상**: 라우팅은 member-service까지 정상 도달했지만 500 응답.
- **원인**: member-service 로그에 `java.lang.IllegalArgumentException: Not found ID: 1`. id가 1인 회원이 아직 DB에 없는 상태에서 조회를 시도함. `MemberService.getMember()`는 못 찾으면 `IllegalArgumentException`을 던지는데, `@ControllerAdvice`/`@ExceptionHandler`로 별도 예외 처리를 하지 않아 예외가 그대로 전파되어 (원래 의도는 404겠지만) 기본 500으로 응답됨.
- **해결**: 조회 전에 먼저 회원 등록(`POST /api/members`)을 실행해서 id=1 회원을 생성. (참고: `MemberService`에 존재하지 않는 리소스 조회 시 404를 반환하도록 예외 처리를 추가하면 근본적으로 개선 가능 — 현재 코드에는 미구현.)

### 5. `POST /api/members` 실행 시 500 Internal Server Error

- **증상**: 회원 등록 요청 자체가 500으로 실패.
- **원인**: 요청 body가 `{"name": "...", "email": "..."}`이었는데, `MemberRequestDto`는 `nickname`, `email`, `password` 필드로 정의되어 있음. `name`은 매핑되는 필드가 없어 무시되고 `nickname`은 `null`, `password`도 누락되어 `null`인 채로 저장을 시도. `members.nickname` 컬럼이 `NOT NULL` 제약이라 `org.hibernate.PropertyValueException`이 발생하며 500으로 응답됨.
- **해결**: 요청 body를 DTO 필드에 맞게 수정.

  ```json
  {
    "nickname": "홍길동",
    "email": "hong@test.com",
    "password": "1234"
  }
  ```

### 6. review-service 재빌드/재기동을 여러 번 반복한 뒤 `POST /api/reviews`, `GET /api/reviews/1`이 간헐적으로 500

- **증상**: 게이트웨이 라우팅과 요청 자체는 정상인데 가끔 500. api-gateway 로그에 `java.net.UnknownHostException: Failed to resolve '934e5d89d66c'` / `DnsErrorCauseException: Query failed with NXDOMAIN`.
- **원인**: `docker compose up -d --build`로 review-service를 여러 번 재빌드하면서, Docker가 매번 새 컨테이너에 랜덤 hostname을 부여함. Eureka는 이걸 새 인스턴스 등록으로 처리하고, 이전 컨테이너가 정상 종료되지 않으면 등록 정보가 남음. `eureka-server`가 기본값인 self-preservation 모드로 동작 중이라 갱신 비율이 떨어져도 죽은 인스턴스를 만료시키지 않고 계속 레지스트리에 남겨둠. api-gateway의 로드밸런서가 라운드로빈으로 죽은 컨테이너의 hostname을 골랐다가 DNS 조회(NXDOMAIN)에 실패해 500이 발생.
- **해결**: `docker compose restart eureka-server`로 레지스트리를 초기화. 재시작 후 살아있는 인스턴스들이 몇 초 안에 재등록되고 죽은 인스턴스 항목은 사라짐.
- **재발 방지 (선택)**: 개발 환경에서는 `eureka-server`에 아래 설정을 추가해 self-preservation을 끄고 만료 주기를 짧게 가져가면 죽은 인스턴스가 빨리 정리됨.
  ```yaml
  eureka:
    server:
      enable-self-preservation: false
      eviction-interval-timer-in-ms: 5000
  ```

### 정리: 정상 동작을 위한 API 호출 순서

리뷰 작성이 member-service를 Feign으로 조회하기 때문에, 반드시 아래 순서로 호출해야 함.

1. `POST /api/members` — 회원 등록 (nickname/email/password 모두 채워서)
2. `GET /api/members/{id}` — 등록된 회원 조회 확인
3. `POST /api/reviews` — 리뷰 작성 (`memberId`는 1에서 등록한 회원의 id)
4. `GET /api/reviews/{id}` — 작성된 리뷰 조회
