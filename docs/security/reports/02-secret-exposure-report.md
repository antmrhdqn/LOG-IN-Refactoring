# 작업 B: 비밀 정보 노출 차단 · 비밀번호 경로 인가 — 작업 보고서

> 작업일: 2026-08-12
> 실행: Claude Code (Sonnet 5) / 검증: 사용자 (2026-08-12)
> 명세: `docs/security/tasks/02-secret-exposure.md` (**확정본 v3** — D1~D13 확정)
> 선행: 작업 A 쓰기 경로 권한 경계 정리 완료·커밋·푸시 (`91de70c`(코드)·`8731d37`(문서))
> **상태: 구현·빌드·검색 확인 8종·`bootRun`·수동 검증(S0~S8) 전부 완료. 커밋·푸시만 남았다** (§5)

---

## 1. 변경 파일

**코드 16개** (명세 §5 그대로 — 17번째 코드 파일 없음, §3의 `git diff --stat`로 확인):

| 파일 | 처방 |
|---|---|
| `application.yml` | [B1] 비밀 3종 → 환경변수, `type: trace` 제거 |
| `member/controller/MemberController.java` | [B2] 헬퍼 2개 신설 + 인가 2건 / [B4] 로그 3곳 |
| `member/dto/MemberDTO.java` | [B3] `WRITE_ONLY` / [B4] `toString` 마스킹 |
| `member/dto/ShowMemberDTO.java` | [B3] `@JsonIgnore` |
| `member/dto/UpdatePasswordRequestDTO.java` | [B4] `toString` 마스킹 3필드 |
| `approval/dto/MemberDTO.java` | [B3] `@JsonIgnore` |
| `member/entity/Member.java` | [B4] `toString` 마스킹 |
| `auth/model/dto/LoginDTO.java` | [B4] `toString` 마스킹 |
| `member/service/MemberService.java` | [B4] 로그 3곳 |
| `auth/handler/CustomAuthenticationProvider.java` | [B4] 로그 1곳 |
| `auth/handler/CustomAuthSuccessHandler.java` | [B4] 로그 1곳 + 주석의 하드코딩 JWT |
| `member/controller/TestController.java` | [B4] 로그 2곳 |
| `commute/entity/CommuteMember.java` | [B3] `@JsonIgnore` |
| `commute/dto/CommuteMemberDTO.java` | [B3] `WRITE_ONLY` / [B4] `@ToString(exclude)` |
| `commute/service/CommuteService.java` | [B4] 로그 1곳 |
| `commute/controller/CommuteController.java` | [B4] 로그 2곳 |

**문서**: `docs/security/spec.md`(이동+정정), `AGENTS.md`, `CLAUDE.md`, 본 보고서(신규).

### git diff --stat (코드)

```
 .../com/insider/login/approval/dto/MemberDTO.java  |  3 ++
 .../auth/handler/CustomAuthSuccessHandler.java     |  1 -
 .../auth/handler/CustomAuthenticationProvider.java |  1 -
 .../com/insider/login/auth/model/dto/LoginDTO.java |  2 +-
 .../commute/controller/CommuteController.java      |  8 -----
 .../login/commute/dto/CommuteMemberDTO.java        |  4 ++-
 .../login/commute/entity/CommuteMember.java        |  2 ++
 .../login/commute/service/CommuteService.java      |  2 --
 .../login/member/controller/MemberController.java  | 34 +++++++++++++---------
 .../login/member/controller/TestController.java    |  2 --
 .../com/insider/login/member/dto/MemberDTO.java    |  4 ++-
 .../insider/login/member/dto/ShowMemberDTO.java    |  2 ++
 .../login/member/dto/UpdatePasswordRequestDTO.java |  6 ++--
 .../com/insider/login/member/entity/Member.java    |  2 +-
 .../login/member/service/MemberService.java        |  3 --
 final/src/main/resources/application.yml           |  7 ++---
 16 files changed, 39 insertions(+), 30 deletions(-)
```

`ErrorCode.java`·`GlobalExceptionHandler.java`·`config/BeanConfig.java`·`JwtAuthorizationFilter.java`·
`WebSecurityConfig.java`·`common/utils/TokenUtils.java`·`common/utils/ConvertUtil.java`·
`approval/**`(DTO 1개 제외)·`webSocket/**`·`insite/**`·`src/test/**` **전부 무변경.**

---

## 2. 처방별 처리

### [B1] 비밀 분리 (`application.yml`)

| 위치 | Before | After |
|---|---|---|
| `:9`·`:10` | `username`/`password` 평문 | `${DB_USERNAME}` / `${DB_PASSWORD}` |
| `:44` | `key: ‹평문›` | `${JWT_KEY}` |
| `:52` | `type: trace` | 제거 |

기본값(`${X:기본값}`) 없음 (R5 — 누락 시 기동 실패가 정상). `show-sql: true`·`hibernate.sql: debug`는 유지.

**사용자가 설정해야 할 환경변수 3개**: `JWT_KEY`, `DB_USERNAME`, `DB_PASSWORD`
(키 로테이션 값 생성과 실제 DB 계정 설정은 명세 D1·P5에 따라 사용자 담당 — 실값을 이 보고서에 남기지 않는다)

### [B2] 인가 — 비밀번호 경로 2종

**[B2-a] `PUT /resetPassword/{memberId}`** — `MemberController:145~155`에 ADMIN 검증 삽입,
`try`(:155) **앞**(R1 — `try` 안에 넣으면 403이 500이 된다):

```java
if (!isAdmin()) {
    throw new BusinessException(ErrorCode.HANDLE_ACCESS_DENIED);
}
```

헬퍼 2개 신설(`getCurrentMemberId()`·`isAdmin()`) — `approval/controller/ApprovalController.java:179~181`의
기존 `getCurrentMemberId()` 패턴을 따랐다. `isAdmin()`은 `getAuthority()` 문자열 비교만 쓴다(R9 —
`DetailsMember`의 권한 구현체가 람다라 `SimpleGrantedAuthority` 비교는 실패한다).

**[B2-b] `PUT /updateOwnPassword`** — 검증 우회 분기(`:319~331`, 필수값 누락 시 `encode("0000")`로
초기화하고 200 반환)를 전체 제거하고 아래로 교체:

```java
if (updatePasswordRequestDTO.getNewPassword1() == null || updatePasswordRequestDTO.getCurrentPassword() == null) {
    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
}
```

기존 검증 3종(현재 비밀번호 일치·`newPassword1`/`newPassword2` 일치)은 그대로 뒀다.

신규 `ErrorCode` **0건** — `HANDLE_ACCESS_DENIED`(C005, 선언만 있고 사용처 0건이었다 — 이번에 활성화)·
`INVALID_INPUT_VALUE`(C001, 기존) 재사용.

### [B3] 응답 `password` 제거 — DTO 4 + 엔티티 1

| 파일 | 역직렬화 필요? | 처방 |
|---|---|---|
| `member/dto/MemberDTO.java` | 예 (`signUp`) | `@JsonProperty(access = WRITE_ONLY)` |
| `commute/dto/CommuteMemberDTO.java` | 예(가능성, 미확인) | 〃 |
| `approval/dto/MemberDTO.java` | 아니오 | `@JsonIgnore` |
| `member/dto/ShowMemberDTO.java` | 아니오 | `@JsonIgnore` |
| `commute/entity/CommuteMember.java` | 아니오 (세터 없음) | `@JsonIgnore` |

**R2 준수**: `member.dto.MemberDTO`·`CommuteMemberDTO`에는 `@JsonIgnore`를 붙이지 않았다 —
역직렬화까지 막으면 `signUp`이 죽는다. 반드시 `WRITE_ONLY`로 구분했다.

`CommuteController`의 응답 맵 키(`member`/`members`/`notice`)는 지우지 않았다(D13) — 이번엔
필드(직렬화)만 막는다. 응답 구조는 그대로다.

### [B4] 로그 위생

**`toString()` 마스킹 — 5파일**: `Member.java`·`member/dto/MemberDTO.java`·`auth/model/dto/LoginDTO.java`
는 `password='***'`로, `UpdatePasswordRequestDTO.java`는 3필드 전부 `'***'`로 바꿨다.
`CommuteMemberDTO.java`는 Lombok `@ToString` → `@ToString(exclude = "password")`.

**직접 출력 제거 — 활성 11건 + 도달 불가 2건** (합산하지 않음 — D11):

활성 11건
- `CustomAuthenticationProvider.java:35` — 평문 비밀번호
- `MemberController.java` — 기존 비밀번호 해시(구 `:316`) + `UpdatePasswordRequestDTO` 전체(구 `:317`)
- `MemberService.java` — `showAllMembers()`의 `findAll()` 전체 2회(구 `:191`·`:193`)
- `CustomAuthSuccessHandler.java:38` — 토큰 전문 로그 + 같은 줄 주석의 하드코딩 JWT 예시값
- `TestController.java` — 토큰 헤더 로그 2곳(구 `:27`·`:45`)
- `CommuteService.java:495` — `memberDTOList.forEach(println)`
- `CommuteController.java` — `responseMap`/`result` 전체 dump 2곳(구 `:111~113`·`:284~286`)

도달 불가 2건 (라인만 제거, 메서드는 유지 — D11)
- `MemberController.java` — `/login` 컨트롤러 메서드의 평문 비밀번호 로그(구 `:477`).
  Spring Security 필터 체인이 `/login`을 가로채 이 컨트롤러 메서드에 도달하지 않는다(작업 A 인계 문서 v1-1
  실증 — 로그인 로그에 `"inputted username:"` 줄이 없음).
- `MemberService.java` — `checkLoggedInfo(LoginDTO)`의 `LoginDTO` 전체 로그(구 `:272`).
  로그인 흐름은 `checkLoggedMemberInfo(int)`를 쓰고 이 메서드는 호출부가 없다.

---

## 3. 빌드 · 검증 결과

### compileJava / compileTestJava

```
> cd final; .\gradlew.bat compileJava
BUILD SUCCESSFUL in 12s

> cd final; .\gradlew.bat compileTestJava
BUILD SUCCESSFUL in 4s
```

**R14 확인**: `src/test/**`의 `commute` 테스트를 포함해 `compileTestJava`가 통과했다. 테스트 파일은
수정하지 않았다.

### bootRun · 수동 검증 (사용자 수행 — 2026-08-12)

환경변수 3개 설정 후 정상 기동 확인. **S0~S8 전 항목 통과.**

| # | 항목 | 결과 |
|---|---|---|
| **S6** | env 없이 `bootRun` | **기동 실패** — `Could not resolve placeholder 'JWT_KEY' in value "${JWT_KEY}"` ✅ R5 |
| **S5** | 로테이션 전 토큰으로 API 호출 | **HTTP 200 + `{"message":"other Token error","reason":"token이 유효하지 않습니다.","status":401}`** ✅ |
| **S0-a** | 로그인 | 200 + 새 키로 토큰 발급 ✅ |
| **S0-b** ★ | `POST /signUp` (multipart, `memberDTO` + `memberProfilePicture`) | **200** — 역직렬화 정상, 비밀번호 INSERT 확인 ✅ **R2 실증** |
| **S0-d** | 정상 비밀번호 변경 (세 필드) | 200 `Successfully changed the password` + 새 비밀번호 로그인 200 ✅ |
| **S0-f** | 결재 스모크 | 폼 목록 7건 / 상신 목록 35건, 응답 구조 불변 ✅ R13 |
| **S0-g** | 출퇴근 (`/commutes?target=depart`·`/corrections`) | 200, 구조 불변 ✅ |
| **S2-a** | ADMIN(Z) → `resetPassword/123` | **200** `Password reset successfully` ✅ |
| **S2-b** ★ | MEMBER(B) → `resetPassword/240501629` | **403** `{"status":403,"code":"C005","message":"접근 권한이 없습니다."}` ✅ **R1 실증 — 500이 아니다** |
| **S2-c** | 토큰 없음 | 200 + `{"status":401,"reason":"token이 존재하지 않습니다"}` (기존 동작 유지) ✅ |
| **S3** | 빈 body `{}` / `currentPassword`만 | **400** `{"status":400,"code":"C001","message":"잘못된 입력값입니다."}` ✅ |
| **S3 부작용** | 차단 직후 기존 비밀번호 로그인 | Z·B 둘 다 **200** — 차단 전에 초기화가 실행되지 않았다 ✅ |
| **S7** | S2-a 직후 계정 123 DB 대조 | `password` 외 **13개 컬럼 불변**, `HEX(image_url)` 35바이트 유지 ✅ **R6** |
| **S0-c** | 프로필 수정 | **미수행.** 명세 리뷰 3-b에서 `MemberController:265`가 서버측에서 DB의 기존 비밀번호를 다시 채우는 것이 원문으로 확인됐다 — 클라이언트가 보낸 `password`를 쓰지 않으므로 R10 위험이 코드로 이미 닫혀 있다 |

> **before 실측(§3-5 파괴적 실측)과의 대비**: S2-b는 이전에 **200**(MEMBER가 ADMIN 계정을 초기화),
> S3는 이전에 **200 `Password reset successfully`**(빈 body로 검증 우회)였다. 둘 다 차단됐다.

#### S1 — 응답 `password` 소멸 + 키 개수 (**9/9 PASS**)

기준선(`C:\temp\secret\baseline`) 대비 after 캡처(`C:\temp\secret\after`).
**모든 파일에서 `password` 개수만큼만 키가 줄었다.**

| 파일 | before Keys / Pw | after Keys / Pw | 판정 |
|---|---|---|---|
| `00-login` | 22 / 1 | **21 / 0** | PASS |
| `01-members-id` | 19 / 1 | **18 / 0** | PASS |
| `02-approval-member` | 17 / 1 | **16 / 0** | PASS |
| `03-approval-members` | 1263 / 90 | **1173 / 0** | PASS |
| `04-rooms-members` | 1710 / 90 | **1620 / 0** | PASS |
| `05-getTokenInfo` | 19 / 1 | **18 / 0** | PASS |
| `06-showAllMembers` | 1710 / 90 | **1620 / 0** | PASS |
| `07-commutes-depart` | 4944 / 83 | **4861 / 0** | PASS |
| `08-corrections` | 1496 / 2 | **1494 / 0** | PASS |
| `09` `POST /corrections` | **미캡처** (R15) | — | 캡처 시 정정 신청 데이터가 실제로 생성되므로 부작용 회피. 필드 구성이 엔티티 코드로 확정돼 있어 처방 실행에는 영향 없음 |

> 이 표가 **D7(응답 구조 변경 근거 기록)의 핵심 증거**다. 응답 불변 원칙을 의도적으로 깬 범위가
> 정확히 `password` 9지점뿐이며, **다른 필드에 애너테이션이 잘못 적용되지 않았음을 숫자로 확정**한다.
> 명세 리뷰가 지적한 검증 구멍(v2-6 — 조건절로만 적으면 오적용을 못 잡는다)을 이 방식이 막았다.
>
> 참고: 전 사원 90명 중 83명이 부서 1 소속이다. before 기준 `03`(632KB)·`04`(638KB)·`06`은
> 한 번 호출에 **90개 해시 전부**를, `07`(109KB)은 **83개**를 내보내고 있었다.

#### S4 — 로그 위생 (`bootRun` 콘솔 전문 before/after 대조)

로그를 `Tee-Object`로 파일에 저장한 뒤 정규식으로 집계했다.

| 항목 | Before (로그인 1회) | After |
|---|---|---|
| 비밀번호 해시 (`$2[aby]$`) | **4회** | **0회** ✅ |
| JWT 토큰 전문 (`eyJ…`) | **1회** | **0회** ✅ |
| 평문 비밀번호 | **1회** | **0회** ✅ |
| `password='***'` | 0회 | **4회** (마스킹 동작 확인) |

`0000` 패턴 2건이 잡혔으나 전부 Hibernate 로그(`HHH000026` 2차 캐시 비활성 / `HHH90000025`
dialect deprecation)의 오탐으로 확인. 교차 확인으로 `CustomAuthenticationProvider`에 남은
`println`은 `:28`(고정 문자열)·`:33`(`loginToken` — `Credentials=[PROTECTED]`로 출력)·`:34`(사번)
셋뿐이며 `pass`를 찍던 줄이 제거됐음을 확인했다.

> **추가 관찰 — 마스킹이 명세가 집계한 범위보다 넓게 작동했다.**
> 회원가입 1회(S0-b) 콘솔에서 `password='***'`가 **5회** 나타났다
> (`MemberController` 1회 + `MemberService` 계열 4회: `new member` / `구성원 등록 성공` /
> `회원 가입한 구성원 정보` / `save transfer history`).
> 명세 §3-1은 **로그인 경로 4지점만** 집계했으나 회원가입 경로에도 동일 규모가 있었다.
> **D6(`toString()` 마스킹)이 호출부만 수정하는 방식보다 넓은 범위를 닫았다는 실증**이다 —
> 호출부 수정안이었다면 이 5지점이 그대로 뚫려 있었을 것이다.

### 검색 확인 8종 (§10 원문 — PowerShell 5.1)

**1. `application.yml` 비밀 문자열 (참조만 남아야 함)**

```
application.yml:9:    username: ${DB_USERNAME}
application.yml:10:    password: ${DB_PASSWORD}
application.yml:44:  key: ${JWT_KEY}
```

`type: trace` 매치 없음(제거 확인). ✅ 평문 문자열 0건, 참조 3건.

**2. `HANDLE_ACCESS_DENIED`(C005) 사용 — 기대 1건**

```
ErrorCode.java:12:    HANDLE_ACCESS_DENIED(403, "C005", "접근 권한이 없습니다."),   ← 선언
MemberController.java:150:  throw new BusinessException(ErrorCode.HANDLE_ACCESS_DENIED);  ← 사용 1건
```
✅

**3. `ErrorCode.java`의 `M008` — 기대 0건**

매치 없음. ✅ `ErrorCode.java` 무변경.

**4. `password`를 찍는 로그 잔존 — 눈으로 판정**

```
MemberService.java:252:  System.out.println("incorrect password");
```

`loggedInMember(MemberDTO)` 메서드의 정적 문자열("비밀번호가 틀렸다"는 안내 문구)이며 실제 비밀번호
값을 출력하지 않는다. 이 메서드는 명세 §5 [B4] 로그 인벤토리에 없고, 값 노출이 아니므로 손대지
않았다.

**5. 토큰 전문 로그 잔존 — 기대 0건, 결과 2건(둘 다 범위 밖)**

```
JwtTokenInterceptor.java:22:  System.out.println("token내용: " + token);
TokenUtils.java:46: (주석 처리된 코드, 비활성)
```

- `JwtTokenInterceptor`는 `WebConfig`에 `@Bean`으로만 등록되고 `addInterceptors()`에 실제로
  붙어 있지 않아 **요청 파이프라인에 도달하지 않는다** (직접 확인). 이 파일은 명세 §5 16파일에
  없고, 구 `docs/security/approval/spec.md` §4-3에 "`JwtTokenInterceptor` 도달 불가"로 이미
  등재돼 있던 항목이다. **작업 B 범위 밖 — 새로 발견한 결함이 아니다.**
- `TokenUtils.java:46`은 **이미 주석 처리되어 실행되지 않는 줄**이다(D11이 정의한 "도달 불가 죽은
  코드"와는 다른 범주 — 코드가 살아 있으나 호출부가 없는 것이 아니라, 애초에 주석이다).
  `TokenUtils.java`는 명세가 "파일을 열지 않는다"고 명시한 범위 밖 파일이라 손대지 않았다.

**6. 직렬화 차단 애너테이션 — 기대 5건(DTO 4 + 엔티티 1), 결과 5건 ✅**

```
approval/dto/MemberDTO.java:91:          @JsonIgnore
commute/dto/CommuteMemberDTO.java:19:    @JsonProperty(access = WRITE_ONLY)
commute/entity/CommuteMember.java:25:    @JsonIgnore
member/dto/MemberDTO.java:98:            @JsonProperty(access = WRITE_ONLY)
member/dto/ShowMemberDTO.java:65:        @JsonIgnore
```

**7. `@PreAuthorize` 신규 도입 — 기대 0건(기존 2건 유지)**

```
TestController.java:16:      @PreAuthorize("hasAuthority('ADMIN')")   ← 기존
Position.java:11:            @PreAuthorize("hasAuthority(ADMIN)")     ← 기존
```

(`WebSecurityConfig.java`의 매치 2건은 주석 문장 안의 텍스트다.) ✅ 기존 2건 외 신규 없음.

**8. 범위 이탈 확인 — 코드 16파일**

`git status`·`git diff --stat` 결과 §1과 동일. 코드 파일 정확히 16개, 17번째 없음. ✅

---

## 4. 범위 밖 발견 항목 (기록만 — 고치지 않았다)

### CommuteController:232~234 — 부서 전체 PII 대량 출력

`GET /corrections` 부서별 분기(`memberId == null`)에서 `commuteService.selectRequestForCorrectList(...)`
호출 직후 `responseMap.forEach(println)`이 한 번 더 있다(명세가 제거를 지시한 `:284~286`과 같은 맵,
다만 그보다 앞선 시점). 이 지점은 명세 §4(2)/§5의 13지점 로그 인벤토리에 없다.

**판정**: 작업 B 범위 밖이다. R2가 `toString` 마스킹만으로 부족하다고 보고 "라인 자체를 제거"까지
요구하는 대상은 **비밀 필드**(password)이고, `:284~286`을 제거하는 이유도 그 줄이 `password`를
내보내서다. `CommuteMemberDTO`에 `@ToString(exclude = "password")`를 적용한 이후로는
`:232~234`도 `password`는 이미 안 나간다 — 남는 것은 부서원 전원의 이름/주소/연락처/이메일이고,
이는 일반 PII이지 명세 §1이 정의한 작업 B의 대상(비밀 정보 노출·비밀번호 경로 인가)이 아니다.
**손대지 않았다.** `spec.md §4-3`(작업 D 등재 목록) 편입을 제안한다.

### JwtTokenInterceptor.java:22 — 토큰 전문 로그 (도달 불가)

§3 검색 확인 5에서 발견.

**등재 여부**: `docs/security/tasks/02-secret-exposure.md`(작업 B 확정본 v3)에는 **없다** — grep 0건.
`docs/security/spec.md:175`(작업 B 착수 전 `docs/security/approval/spec.md:166`)의 §4-3
"작업 D — 등재만" 표에 다음 원문으로 이미 있다:

> `죽은/무효 권한 코드 | Position.java:11 @PreAuthorize (엔티티라 무효 + SpEL 오류), JwtTokenInterceptor 도달 불가 |`

즉 **작업 B 자신의 명세(`tasks/02-secret-exposure.md`)가 등재한 적은 없고**, 이 대화의 P1 단계에서
읽은 `spec.md`(별개 문서, 작업 A 착수 전 작성)의 D-등재 목록에 이미 있던 항목이다. 이 사실을 보고서
본문에 인용 없이 "already-known"으로만 적은 것은 부정확했다 — 정정한다.

**도달 불가 판정 근거(직접 확인)**: `WebConfig.java`가 `WebMvcConfigurer`를 구현하지만
`addResourceHandlers`(:25)·`addCorsMappings`(:51)만 오버라이드하고 `addInterceptors()`는 없다.
`jwtTokenInterceptor()`(:45~48)는 `@Bean`으로 객체만 만들 뿐 `InterceptorRegistry`에 등록되지 않는다.
코드베이스 전체에서 `WebMvcConfigurer` 구현체는 이 파일과 `WebSocketConfigMapping.java` 둘뿐이며,
후자도 `addCorsMappings`만 오버라이드한다 — `addInterceptors()` 오버라이드가 **어디에도 없다.**
DispatcherServlet이 이 인터셉터를 호출할 경로가 없으므로 도달 불가 판정은 유효하다.

**작업 B 범위 판단**: 명세 16파일 밖이고, 값을 실제로 출력하지 못하는 죽은 코드이므로 손대지 않았다.
`spec.md`에는 이미 등재돼 있으니 추가 등재를 제안하지 않는다.

---

### `final_clone2/FRONT-LOGIN/public/img/` — 상대 경로 파일 업로드

**발견 경위**: S0-b(회원가입) 수동 검증 중 리포 루트에 untracked 폴더가 생성됐다.
업로드한 프로필 이미지 1개가 이 경로에 저장돼 있었고, `.java`·`.yml` 등 소스 파일은 없다.

**판정**: 프로필 이미지가 `application.yml`의 `file.upload-dir`(`C:/login/`)이 아니라
**프론트 프로젝트의 `public/img`를 가리키는 상대 경로**로 저장되고 있다. 절대 경로가 아니므로
저장 위치가 `bootRun` 실행 시점의 작업 디렉터리(CWD)에 의존하며, 그 결과 **리포 안에 사용자
업로드 파일이 쌓인다.**

**작업 B 범위 밖이다.** 명세 §5의 16파일에 해당 저장 로직이 없고, 비밀 정보 노출도 아니다.
커밋에 포함되지 않도록 폴더만 삭제했다(untracked라 이력 영향 없음).

**후속 과제 — 두 가지가 별개다.**
- 저장 경로를 `file.upload-dir` 기준 절대 경로로 교정
- `.gitignore` 등재. 이번 작업에서는 명세 D2가 `.gitignore` **무변경**으로 확정했으므로 손대지 않았다.
  등재 전까지는 `git add .` 류 명령으로 사용자 업로드 파일이 커밋될 수 있으니 주의가 필요하다.

`spec.md §4-3`(작업 D 등재 목록) 편입을 제안한다.

---

## 5. 다음 단계 (사용자)

**1·2단계(env 설정·기동 확인·수동 검증 S0~S8)는 완료됐다** — 결과는 §3에 있다. 남은 것은 커밋·푸시다.

1. **검증 잔여물 정리** (S0-b에서 생성한 계정)
   ```sql
   DELETE FROM transferred_history WHERE member_id = 999001;
   DELETE FROM member_info         WHERE member_id = 999001;
   ```
   업로드 파일 `C:/login/upload/999001_*.png`도 함께 정리 가능(선택).
   리포 루트의 `final_clone2/`는 커밋 전 삭제 완료(§4).
   ⚠ 검증 중 계정 Z(240501629)·B(240501544)·123의 비밀번호가 `0000` 또는 `test1234`로 변경됐다.

2. **커밋 2분할** — 파일을 섞지 않는다
   ```powershell
   cd C:\env\GitHub\INSIDER\LOG-IN-Refactoring

   # ① 코드 — 정확히 16개여야 한다
   git add final/src
   git diff --cached --stat
   git commit -m "..."

   # ② 문서 — spec.md 는 이동(rename)이라 -A 로 담아야 한다
   git add -A docs/ AGENTS.md CLAUDE.md
   git status          # renamed: docs/security/approval/spec.md -> docs/security/spec.md 확인
   git diff --cached --stat
   git commit -m "..."
   ```
   > ⚠ `git add docs/security/spec.md`만 하면 **이동 전 경로의 삭제가 누락돼 파일이 둘로 남는다.**
   > 반드시 `-A`로 담아 `renamed:`로 잡히는지 확인할 것.

   > ⚠ `C:\temp\secret\`의 기준선 JSON·`after-boot.log`에는 해시·토큰이 들어 있다.
   > **리포 안으로 옮기지 말 것.** 커밋 전 `git status`에 `C:\temp` 경로가 없는지 확인.

3. **푸시 → 작업 B 완료**

4. 이후 결정: 작업 E(읽기 경로 인가, 정책 결정 선행) 착수 여부, 그리고 본 보고서 §4의 세 항목과
   §3에서 관찰된 `showAllMembersPage` 무인증(R11)·CORS 전역 개방을 `spec.md §4-3`에 등재할지

### 작업 B 최종 상태

| 지표 | Before | After |
|---|---|---|
| `application.yml`의 비밀 문자열 | 3건 | **0건** — 전부 환경변수 참조 ✅ |
| JWT 서명 키 | 공개 리포 평문 커밋 (유출 간주) | **로테이션 완료 — 옛 키 토큰 무효 실증** ✅ S5 |
| 응답의 `password` 키 | **9지점** (해시 7 · null 2) | **9지점 전부 소멸** ✅ S1 (8지점 실측 + `POST /corrections` 미캡처) |
| 응답 JSON 키 개수 | — | **`password` 개수만큼만 감소 — 9/9 PASS** ✅ S1(b) |
| 로그의 평문 비밀번호 | 활성 2곳 | **0곳** ✅ S4 |
| 로그의 비밀번호 해시 | 로그인 1회당 **4회** | **0회** ✅ S4 |
| 로그의 JWT 토큰 전문 | 3곳(+주석 1) | **0회** ✅ S4 |
| `PUT /resetPassword/{id}` | **MEMBER가 ADMIN 계정 초기화 가능** (200) | **403 / C005** ✅ S2-b |
| `PUT /updateOwnPassword` 우회 | **빈 body로 `0000` 초기화** (200) | **400 / C001** ✅ S3 |
| 회원 정보 보존 (초기화 후) | — | **13개 컬럼 불변** ✅ S7 |
| 신규 ErrorCode | — | **0건** ✅ |
| `compileJava` / `compileTestJava` | — | **통과** ✅ (commute 테스트 포함) |
| 정상 경로 (로그인·가입·비밀번호 변경·결재·출퇴근) | — | **불변** ✅ S0 |
| 미수행 | — | S0-c(코드로 위험 해소 확인) · `POST /corrections` 기준선(부작용 회피) **2건, 사유 기록** |
