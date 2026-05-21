# LOG-IN 프로젝트 개요

## 프로젝트 소개
사내 그룹웨어 시스템 "LOG-IN". 회원 관리, 출퇴근, 전자결재, 휴가, 공지사항, 일정,
채팅, 설문, 제안 등 그룹웨어 기능을 포괄적으로 다룬다.

본 저장소는 LOG-IN의 백엔드 리팩토링 프로젝트로, 포트폴리오 목적으로 진행된다.

## 기술 스택

| 항목 | 버전/도구 |
|---|---|
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 3.2.4 |
| ORM | JPA (Hibernate) |
| 빌드 도구 | Gradle |
| 데이터베이스 | MySQL |
| 인증 | Spring Security (SecurityContext 기반) |

## 패키지 구조 (루트)

```
com.insider.login/
├── common/          공통 컴포넌트
│   ├── error/       ErrorCode, BusinessException, GlobalExceptionHandler
│   └── response/    ResponseMessage, ErrorResponse
├── approval/        전자결재 도메인 (현재 리팩토링 중)
├── leave/           휴가 도메인 (리팩토링 완료, 패턴 레퍼런스)
├── member/          회원 도메인
├── commute/         출퇴근 도메인
├── notice/          공지사항 도메인
├── calendar/        일정 도메인
├── chat/            채팅 도메인 (WebSocket)
├── survey/          설문 도메인
└── proposal/        제안 도메인
```

## DB 관리 방침

- `spring.jpa.generate-ddl: false` — JPA 자동 DDL 생성 사용 안 함
- DDL은 직접 관리
- 마이그레이션 스크립트는 `src/main/resources/db/migration/` 에 위치
- Flyway 등 마이그레이션 도구는 현재 미사용. 파일명은 `V{n}__{description}.sql` 규칙
  (향후 Flyway 도입 가능성 고려)

## 빌드 / 실행

```bash
./gradlew compileJava    # 컴파일만
./gradlew build          # 컴파일 + 테스트
./gradlew bootRun        # 실행
```

## 인증 처리

Spring Security 기반. 컨트롤러에서 현재 사용자 정보 추출 패턴:

```java
Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
int memberId = Integer.parseInt(authentication.getName());
```

향후 리팩토링에서 이 패턴은 헬퍼 메서드(`getCurrentMemberId()`)로 추출 예정.

## 공통 응답 체계

### 정상 응답
```java
ResponseMessage<T>
```

### 에러 응답
```java
ErrorResponse  // BusinessException → GlobalExceptionHandler가 자동 변환
```

### 도메인 에러
- 도메인별 접두사 코드 사용: C(공통), M(회원), L(휴가), CA(일정), S(설문), AP(전자결재)
- 발생: `throw new BusinessException(ErrorCode.XXX)`

## 리팩토링 진행 상태

| 도메인 | 상태 | 문서 |
|---|---|---|
| 전역 에러 처리 | ✅ 완료 | `docs/refactoring/completed/error-handling.md` |
| 휴가 (Leave) | ✅ 완료 | `docs/refactoring/completed/leave-domain.md` |
| 전자결재 (Approval) | 🔄 진행 중 | `docs/refactoring/approval/` |
| 그 외 도메인 | ⏸ 미정 | |
