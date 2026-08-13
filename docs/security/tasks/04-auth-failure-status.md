# 작업 04 — 인증 실패 응답 정상화 (HTTP 200 → 401)

> 작성: 2026-08-14 (v1)
> 스트림: 보안 결함 정리. **리팩토링이 아니다.**
> 선행: 작업 E 읽기 경로 인가 완료 — `7f66056`(코드) · `0b1cc92`(문서) · `18e9298`(배치 평평화)
> 근거: `docs/security/spec.md` §4-3 등재 행 "인증 실패가 HTTP 200" · 본 문서 §3 기준선 실측

---

## v2 정정 (명세 리뷰 세션 · 읽기 전용 대조 결과 · 2026-08-14)

| # | 항목 | 정정 |
|---|---|---|
| v2-1 | **§4 [F2] 삽입 위치** | "`setCharacterEncoding` 앞"은 빈 줄 때문에 두 곳으로 읽힌다 → **"`jsonObject = new JSONObject(resultMap);` 직후, `setCharacterEncoding` 바로 앞"** 으로 확정. 실물 구조를 §4에 그대로 실었다 |
| v2-2 | **신규 import 0건** | 전제가 아니라 **실측**이 됐다 — `JwtAuthorizationFilter:17`, `CustomAuthFailureHandler:5`에 `jakarta.servlet.http.HttpServletResponse` 존재 |
| v2-3 | **§10 검색 1번 기대값** | 프로덕션 `.setStatus(` 가 현재 **1건뿐**(`CustomAuthenticationFilter:43` `SC_BAD_REQUEST`)임을 확인. 처방 후 **3건**이라는 기대값이 성립한다 |
| v2-4 | **§3-4 프론트 위험** | `Login.js` 실물 확인 결과 401 분기와 현재 분기의 **내용이 동일**하다. "미검증 분기가 켜져 UX가 바뀐다"는 v1 착수 전 우려는 **철회**한다 |


---

## 0. 착수 상태

| 항목 | 상태 |
|---|---|
| 기준선 캡처 | **완료.** `C:\temp\auth-status\baseline` 17항목 (§3) |
| 결정성 자가검증 | **완료.** 같은 프로세스 1회 + **재기동 후 1회**. drift = `06`·`16`·`17`만 (§3-5) |
| 인증 실패 응답 지점 전수 | **완료. 2곳으로 확정** (§3-1) |
| 프론트 회귀 위험 | **해소.** 401을 읽는 곳은 `Login.js` 1곳, 동작이 현재 분기와 동일 (§3-4) |
| 라인 번호 재확인 | **완료.** `spec.md` §4-3 등재값과 일치, 드리프트 없음 |
| 처방 삽입 위치 | **완료. 실물 대조** — 두 지점 모두 `getWriter()` 호출보다 앞에 자리가 있다 (§4) |
| 신규 import 필요 여부 | **0건 확정.** `HttpServletResponse`가 두 파일에 이미 import 돼 있다 (v2-2) |
| 프로덕션 `.setStatus(` 현황 | **1건** — `CustomAuthenticationFilter:43` (v2-3) |
| 환경변수 | `JWT_KEY` · `DB_USERNAME` · `DB_PASSWORD` 3종 필요 (없으면 `bootRun` 기동 실패) |

---

## 1. 목표

**인증에 실패한 요청이 HTTP 200으로 나가는 것을 401로 바로잡는다. 응답 본문은 건드리지 않는다.**

### 성공 기준

1. 인증 실패 응답 지점 **2곳 전부**가 401을 반환한다 — `JwtAuthorizationFilter`(필터), `CustomAuthFailureHandler`(로그인 실패 핸들러)
2. 캡처 `08~15` **8항목**이 `200 → 401`로 전환된다
3. 같은 8항목의 **응답 본문 해시가 기준선과 동일하다** — 상태 코드만 바뀐다
4. 캡처 `01~06` **정상 경로 6항목**이 불변이다
5. 캡처 `07`·`16`·`17` **범위 밖 3항목**이 동결 상태를 유지한다 (상태 코드·본문 모두)
6. 신규 `ErrorCode` **0건**, `ErrorCode` **사용 0건**, 신규 `import` **0건**
7. `compileJava` + `compileTestJava` + `bootRun` 통과
8. 로그인 화면에서 틀린 비밀번호 입력 시 **현재와 동일한 alert**가 뜬다 (§3-4)

### 성공 기준이 아닌 것

- 응답 본문 구조 통일 (`failType` vs `status/message/reason`) — **범위 밖** (D2)
- 계정 열거 차단, 스택트레이스 노출 차단, 만료/위조 구분 — **범위 밖, 등재만** (§7)

---

## 2. 경계 (확정)

### 범위 안 — 코드 2파일

| 파일 | 수정 | 라인 |
|---|---|---|
| `auth/filter/JwtAuthorizationFilter.java` | `catch (Exception e)` 블록에 상태 코드 세팅 1줄 추가 | 115~124 |
| `auth/handler/CustomAuthFailureHandler.java` | 응답 기록 직전에 상태 코드 세팅 1줄 추가 | 20~66 |

**추가 라인 합계 3~4줄. 삭제·이동 0줄.**

### 범위 밖 (명시 · 🚫 하드 가드)

| 대상 | 이유 |
|---|---|
| 🚫 **`roleLessList`** (`JwtAuthorizationFilter:57`) | 고치면 `/approvals`와 회원 조회·프로필 수정이 **동시에 무인증 개방**된다. 기준선 `04`·`05`가 이 경로의 현재 동작을 고정한다 |
| 🚫 **응답 본문 · `jsonResponseWrapper`** | D2. 본문이 바뀌면 성공 기준 3이 깨진다 |
| 🚫 **`CustomAuthSuccessHandler`** | 휴면 계정 200은 Spring 기준 "성공"이다. `Login.js`의 **살아 있는** 문자열 비교 경로다 |
| 🚫 **`CustomAuthenticationFilter`** | 이미 400을 세팅한다(`SC_BAD_REQUEST`). 기준선 `17`이 이를 동결한다 |
| 🚫 **`@PreAuthorize` 도입** | 거부 시 `AccessDeniedException`이 `GlobalExceptionHandler` catch-all에 걸려 **500이 나간다** (실측 확인분) |
| 🚫 **`AuthenticationEntryPoint` 신설** | 전수 검색 결과 현재 0건. 새 진입점을 만들면 필터 흐름이 바뀐다 |
| 🚫 **`ErrorCode` · `ErrorResponse` 사용** | D3 |
| 🚫 **`server.error.include-stacktrace`** | §7 등재. 설정 파일은 건드리지 않는다 |
| 🚫 프론트엔드 전체 | 백엔드만 고치면 된다 (§3-4) |

---

## 3. 실측 근거 — 기준선 17항목 (2026-08-14)

### 3-1. 인증 실패 응답 지점은 **2곳뿐이다** (전수 검색)

```powershell
Get-ChildItem -Recurse -Filter *.java |
  Select-String "AuthenticationFailureHandler|AuthenticationEntryPoint|setStatus|SC_UNAUTHORIZED"
```

| 파일 | 히트 | 정체 |
|---|---|---|
| `WebSecurityConfig` | 1 | 핸들러 **등록부**. 상태 코드 세팅 아님 |
| `CustomAuthenticationFilter` | 1 | `SC_BAD_REQUEST` — 프로덕션 유일의 `setStatus`, **400은 이미 올바르다** |
| `CustomAuthFailureHandler` | 2 | `import` + `implements`. **`setStatus` 0건** |
| `JwtAuthorizationFilter` | **0** | 히트 자체가 없다 |

- **`AuthenticationEntryPoint` 0건** → Spring 기본 401 진입점도 설정돼 있지 않다
- ⇒ 아무도 401을 세팅하지 않는다. 처방 지점은 **정확히 2곳**

### 3-2. 두 지점 모두 200으로 나간다 — 본문 5종

| 캡처 | 요청 | 상태 | 크기 | 본문 해시(앞 8) |
|---|---|---|---|---|
| 08 | 무토큰 · 상세 | **200** | 90B | `69b23489` |
| 13 | 무토큰 · 첨부 | **200** | 90B | `69b23489` |
| 09 | 만료 토큰 | **200** | 91B | `6c000f76` |
| 10 | 위조 토큰(서명) | **200** | 91B | `6c000f76` |
| 11 | 헤더 형식오류 | **200** | 90B | `f693052f` |
| 12 | 빈 토큰 | **200** | 90B | `f693052f` |
| 14 | 로그인 · 틀린 비밀번호 | **200** | 62B | — |
| 15 | 로그인 · 없는 사번 | **200** | 55B | — |

- **08 = 13** : 파일 다운로드 경로도 같은 필터·같은 본문
- **09 = 10** : 만료와 위조가 **바이트 단위로 동일**. `TokenUtils.isValidToken()`이 예외를 삼키고 `false`만 반환하므로, `jsonResponseWrapper`의 `Token Expired`·`SignatureException` 분기는 **도달 불가능한 죽은 코드**다
- **11 = 12** : 헤더 형식오류와 빈 토큰이 동일
- **14·15는 본문에 `status:401`조차 없다.** 필터(08~13)는 본문에라도 401을 넣는데 핸들러는 그것도 없다. **핸들러 쪽이 한 단계 더 나쁘다**

### 3-3. 필터는 `GlobalExceptionHandler`가 잡지 못한다 — 같은 기준선 안에서 대조 실증

| 캡처 | 예외 발생 위치 | 응답 | 크기 |
|---|---|---|---|
| 07 | DispatcherServlet **안** | `ErrorResponse` — `{"status":500,"code":"C999",…}` | 90B |
| 16·17 | **필터 안** | Spring 기본 에러 + **스택트레이스 전문** | 8~9KB |

`@RestControllerAdvice`는 DispatcherServlet 밖의 필터 예외에 닿지 않는다.
⇒ **필터·핸들러에서는 `ErrorCode`/`ErrorResponse`로 위임할 경로가 없다. 상태 코드를 직접 세팅하는 것이 유일한 수단이다** (D3의 근거).

### 3-4. 프론트 회귀 위험 — 없다

`Login.js`에 인터셉터도 만료 처리도 없고, 401을 읽는 곳은 한 군데다.

```js
} else {                                   // 현재 틀린 비밀번호가 떨어지는 곳 (200)
    alert('로그인 중에 오류가 발생했습니다. 다시 시도해주세요.');
    setForm({ memberId: '', password: '' });
}
} else if (response.status === 401) {      // 처방 후 떨어질 곳
    alert('로그인 중에 오류가 발생했습니다. 다시 시도해주세요.');
    setForm({ memberId: '', password: '' });
}
```

**두 분기의 내용이 동일하다.** 지금까지 실행된 적 없는 분기가 켜지지만, 켜진 결과가 현재와 같다.
⇒ 사용자가 보는 화면은 **바뀌지 않는다.**

### 3-5. 결정성 — 재기동을 건너 안정적이다 (R11)

| 회차 | drift |
|---|---|
| baseline vs baseline2 (같은 프로세스) | 06 · 16 · 17 |
| baseline vs baseline2 (**애플리케이션 재기동 후**) | 06 · 16 · 17 |

- `06`은 응답에 새 토큰·만료시각이 실려 가변 — shape로 판정
- `16`·`17`은 Spring 기본 에러 본문의 `timestamp`(ms)가 가변 — shape로 판정
- **`08~15`가 재기동을 건너서도 해시 동일.** 인증 실패 본문은 `json-simple`의 `JSONObject`(HashMap 기반)라 키 순서 변동을 우려했으나 실제로는 일어나지 않았다 (키 3개) → **R1로 등급 하향**

---

## 4. 처방

### 공통 원칙

- **추가만 한다.** 기존 라인을 고치거나 옮기지 않는다
- **본문 생성 코드에 손대지 않는다** — `jsonResponseWrapper`, `resultMap`, `JSONObject` 전부 그대로
- 두 파일 모두 `HttpServletResponse`를 이미 import 하고 있다 → **신규 import 0건**

### [F1] `JwtAuthorizationFilter` — `catch` 블록

**위치:** `catch (Exception e) {` (115) 직후, `response.setContentType(...)` 앞

```java
} catch (Exception e) {
    // 인증 실패는 401로 나가야 한다.
    // chain.doFilter 가 try 안에 있어 하위 처리 예외도 이 블록에 도달할 수 있고,
    // 그때는 응답이 이미 커밋돼 setStatus 가 조용히 무시된다. 커밋 여부를 먼저 본다.
    if (!response.isCommitted()) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
    response.setContentType("application/json");
    ...  // 이하 무변경
```

**`isCommitted()` 가드가 필수인 이유:** `chain.doFilter(request, response)`가 `try` 블록 **안**에 있다. 하위 처리 예외가 `GlobalExceptionHandler`를 빠져나오면 이 `catch`가 받는데, 그 시점엔 응답이 커밋된 뒤다. 가드 없이 `setStatus`를 부르면 무시되고, **무시됐다는 사실도 남지 않는다** (D4).

### [F2] `CustomAuthFailureHandler` — 응답 기록 직전

**위치:** `jsonObject = new JSONObject(resultMap);` **직후**, `response.setCharacterEncoding("UTF-8")` **바로 앞**.
`getWriter()` 호출보다 반드시 먼저다 — 뒤에 넣으면 응답이 커밋돼 **무성 실패한다.**

실물 구조(2026-08-14 확인):

```java
jsonObject = new JSONObject(resultMap);

// ↓ 여기에 2줄 추가
// 인증 실패는 예외 종류와 무관하게 401 단일. 상태 코드로 계정 존재 여부를 흘리지 않는다.
response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

response.setCharacterEncoding("UTF-8");
response.setContentType("application/json");
PrintWriter out = response.getWriter();
```

- 예외 분기 8종(`BadCredentials`·`Locked`·`Disabled`·`AccountExpired` 등)에 **분기를 넣지 않는다** (D5)
- `failMsg` 산출 로직과 `resultMap` 구성은 이 위치보다 **위쪽**에 있다. **한 글자도 바꾸지 않는다**
- `HttpServletResponse` import는 5줄에 이미 있다 → 신규 import 0건

---

## 5. Scope — 수정 허용 파일

```
final/src/main/java/com/insider/login/auth/filter/JwtAuthorizationFilter.java
final/src/main/java/com/insider/login/auth/handler/CustomAuthFailureHandler.java
```

**이 2파일 외 코드 변경은 전부 범위 이탈이다.** 설정 파일(`application.yml`), 프론트엔드, 다른 필터·핸들러 포함.

문서는 별도:
```
docs/security/tasks/04-auth-failure-status.md      (본 문서)
docs/security/reports/04-auth-failure-status-report.md
docs/security/spec.md                              (§4-3 → §4-5 이동 + 등재 추가)
AGENTS.md                                          (로드맵)
```

---

## 6. ErrorCode — 사용 0건

**신규 0건이 아니라 사용 자체가 0건이다.** 작업 E(재사용만, 신규 0건)보다 한 칸 더 보수적이다.

- `M002 EXPIRED_TOKEN` · `M003 INVALID_TOKEN` · `M004 UNSUPPORTED_TOKEN`은 이미 401로 선언돼 있으나, **이번 작업은 쓰지 않는다**
- 이유: 본문을 바꾸지 않기로 했으므로(D2) `ErrorCode`를 꺼낼 자리가 없다. 그리고 필터는 `GlobalExceptionHandler`에 위임할 수 없다 (§3-3)
- 착수 전 확인: 위 3개 상수의 **현재 사용처**를 검색해 보고서에 기록한다. 사용처가 0이면 죽은 상수이며, **이번 작업이 되살리지 않는다는 사실을 명시한다**

---

## 7. 결정 사항

### D1. 처방 대상 — **필터 + 핸들러 2곳 모두**

전수 검색으로 2곳이 전부임이 확인됐다(§3-1). 핸들러를 빼면 로그인 실패가 계속 200으로 나간다.

### D2. 응답 본문 — **완전 무변경**

선례 S4(응답 구조 불변)와 일치. `Login.js`가 성공 응답의 **메시지 문자열을 비교**하고 있어 본문은 위험 구간이다. 판정도 "본문 해시 동일 + 상태 코드 401" 두 숫자로 단순해진다.

> 등재: 본문 이중 구조(`failType` vs `status/message/reason`), `ErrorResponse` 미통일 → `spec.md` §4-3

### D3. `ErrorCode` — **사용하지 않는다**

§6·§3-3 참조.

### D4. `isCommitted()` 가드 — **넣는다**

`chain.doFilter`를 `try` 밖으로 빼는 구조 변경(제어 흐름이 바뀐다)이나, 가드 없이 `setStatus`만 넣는 안(무시되는 경로가 남는다)보다 **2줄 가드**가 안전하다. 커밋된 경로의 동작은 완전히 불변이다.

### D5. 예외 종류별 분기 — **없음. 전부 401**

1. 본문이 이미 `"존재하지 않는 사용자입니다"` / `"아이디 또는 비밀번호가 틀립니다"`로 계정 존재 여부를 흘린다. 상태 코드까지 나누면 **열거 축을 하나 더 만든다**
2. ★ `Login.js`는 `=== 401` 하나만 본다. 잠긴 계정·비활성 계정을 403으로 내보내면 **그 경우만 조용히 실패하는 상태가 남는다**

> 등재: 실패 메시지에 의한 계정 열거 → §7 등재 목록

### D6. `roleLessList` — **불변**

기준선 `04`·`05`(무토큰 200)가 현재 동작을 고정한다. 이 경로는 `chain.doFilter` 후 `return`이라 `catch`에 진입하지 못하므로 처방이 닿지 않는다.

### D7. 기준선 재사용 — **불가. 새로 찍었다**

작업 E 기준선은 응답 코드가 바뀌는 항목을 포함하지 않는다. `C:\temp\auth-status\`에 신규 17항목을 캡처했다.

### D8. 문서 배치 — `docs/security/tasks/04-auth-failure-status.md`

2026-08-12 평평화 규칙. 도메인 하위 폴더를 만들지 않는다.

---

## 8. 위험 목록

| # | 위험 | 대응 |
|---|---|---|
| **R1** | 인증 실패 본문의 키 순서 변동(`json-simple` HashMap) | **등급 하향.** 재기동을 건너서도 해시 동일함이 실측됐다(§3-5). `-Compare`의 정규화 재판정은 안전망으로 유지 |
| **R2** | `catch`가 커밋된 응답에 도달해 `setStatus`가 무시된다 | D4 가드. 무시되는 경로 자체를 없앤다 |
| **R3** | A 토큰이 **2026-08-15 06:35 KST** 만료 | after 캡처를 그 전에 끝낸다. 넘기면 A만 재발급(응답 본문에 토큰이 실리지 않아 기준선 재촬영 불필요) + **`FORGED`를 새 A에서 다시 생성** |
| **R4** | 401 전환으로 `Login.js`의 미실행 분기가 켜진다 | 두 분기 내용이 동일해 화면이 바뀌지 않는다(§3-4). 그래도 **로그인 화면에서 직접 확인**한다 (성공 기준 8) |
| **R5** | 다른 프론트 화면이 200을 성공으로 오판하고 있었다면 동작이 바뀐다 | 토큰 만료 처리가 프론트에 **전무**하므로 만료 시 지금도 화면이 깨진다. 401로 바뀌어도 깨지는 방식만 달라진다 — 회귀가 아니다 |
| **R6** | 캡처 `06`·`16`·`17`의 해시 불일치를 회귀로 오판 | shape 판정 그룹으로 분리돼 있다. `-Compare`가 자동 처리 |

---

## 9. 문서 갱신

### 9-1. `spec.md`

1. **§4-3에서 "인증 실패가 HTTP 200" 행을 제거**하고 **§4-5(작업 04) 신설** — 작업 E의 §4-4와 같은 형식
2. §4 작업 분할 표에 작업 04 행 추가
3. **§4-3에 신규 등재 3건 추가** — 전부 **실측**분이다 (코드 대조와 구분할 것)

| 항목 | 지점 | 근거 |
|---|---|---|
| **필터 예외 시 스택트레이스 전문 노출** | `CustomAuthenticationFilter`의 `IOException → RuntimeException`이 필터를 빠져나가 Spring 기본 `/error`로 떨어진다 | 기준선 `16`·`17` — 인증 없이 `POST /login`만으로 8~9KB 스택트레이스. 내부 패키지 구조·라이브러리 노출 |
| **만료 토큰과 위조 토큰이 구분되지 않는다** | `TokenUtils.isValidToken()`이 예외를 삼키고 `false`만 반환 → `jsonResponseWrapper`의 `Token Expired`·`SignatureException` 분기가 죽은 코드 | 기준선 `09` = `10` 해시 동일 |
| **로그인 실패 메시지에 의한 계정 열거** | `CustomAuthFailureHandler`가 "존재하지 않는 사용자" / "아이디 또는 비밀번호가 틀립니다"를 구분 | 기준선 `14`(62B) ≠ `15`(55B) |

### 9-2. `AGENTS.md`

- 로드맵의 `D. 등재만` 줄에서 "인증 실패 200" 제거
- `F. 인증 실패 응답 정상화 ✅` 항목 추가 (커밋 해시 기입)
- "현재 진행 작업" 갱신

### 9-3. 보고서

`docs/security/reports/04-auth-failure-status-report.md` — 작업 E 보고서 구조를 따른다.
§1 변경 파일 / §2 구현 내용 / §3 검증 결과 / §4 명세와 실물의 차이 / §5 캡처 17항목 판정 / §6 남은 일 / §7 범위 밖·등재

### 9-4. 캡처 도구

`capture-auth-status.ps1`을 `docs/security/tools/`에 편입한다.
**`tokens.ps1`·캡처 산출물은 리포에 넣지 않는다** — 유효 토큰과 사원 PII가 들어 있다.

---

## 10. 검증

### 자동 검증 (Claude Code)

```powershell
cd final
.\gradlew.bat compileJava
.\gradlew.bat compileTestJava
.\gradlew.bat bootRun          # 80% EXECUTING 에서 멈춘 듯 보이는 것이 정상
```

⚠ **`compileJava`만으로는 부족하다.** 단계 1.5의 JPQL 리터럴 무성 실패 선례. 수동 API 확인이 필수다.

### 검색 확인 (PowerShell 5.1 · 보고서에 결과 기록)

```powershell
cd final\src\main\java\com\insider\login

# 1) setStatus 는 프로덕션에 3건이어야 한다 (기존 400 1건 + 신규 2건)
Get-ChildItem -Recurse -Filter *.java | Select-String "\.setStatus\("

# 2) ErrorCode 사용 0건 — 두 파일에 히트가 없어야 한다
Select-String -Path .\auth\filter\JwtAuthorizationFilter.java,.\auth\handler\CustomAuthFailureHandler.java `
              -Pattern "ErrorCode|ErrorResponse|BusinessException"

# 3) roleLessList 무변경 확인
Select-String -Path .\auth\filter\JwtAuthorizationFilter.java -Pattern "roleLessList"

# 4) 본문 생성 코드 무변경 확인
Select-String -Path .\auth\filter\JwtAuthorizationFilter.java -Pattern "jsonResponseWrapper|jsonMap.put"
Select-String -Path .\auth\handler\CustomAuthFailureHandler.java -Pattern "failType|resultMap"

# 5) M002·M003·M004 현재 사용처 (§6)
Get-ChildItem -Recurse -Filter *.java | Select-String "EXPIRED_TOKEN|INVALID_TOKEN|UNSUPPORTED_TOKEN"
```

### 수동 검증 — 캡처 17항목 (사용자 담당)

```powershell
cd C:\temp\auth-status
.\capture-auth-status.ps1 -Phase after *>&1 | Tee-Object -FilePath .\after-console.txt
.\capture-auth-status.ps1 -Compare        *>&1 | Tee-Object -FilePath .\verdict-console.txt
```

| 그룹 | 항목 | 기대 | 판정식 |
|---|---|---|---|
| **S0 정상 (먼저 본다)** | 01~05 | 불변 | 해시 동일 |
| | 06 | 불변 | shape 동일 + 성공 메시지 존재 |
| **S1 인증 실패** | 08~15 | **200 → 401** | **해시 동일 AND before=200 AND after=401** |
| **S2 동결** | 07 · 18 | 완전 불변 | 해시 동일 + 상태 동일 |
| | 16 · 17 | 동결 | shape 동일 + 상태 동일 |

**전 항목 PASS가 아니면 완료가 아니다.**

### 화면 검증 (사용자 담당 · 성공 기준 8)

1. 로그인 화면에서 **틀린 비밀번호** 입력 → `로그인 중에 오류가 발생했습니다. 다시 시도해주세요.` alert. **현재와 동일해야 한다**
2. **정상 로그인** → 진입 정상
3. 결재 목록·상세·첨부 다운로드 정상 (S0 재확인)

---

## 11. 실행 순서

1. `git status` 확인 — 워킹 트리 클린
2. plan mode로 계획 제시 → 승인
3. [F1] `JwtAuthorizationFilter`
4. [F2] `CustomAuthFailureHandler`
5. `compileJava` → `compileTestJava` → `bootRun`
6. 검색 확인 5종 실행, 결과 기록
7. 보고서 작성 (`docs/security/reports/04-auth-failure-status-report.md`, UTF-8)
8. 변경 사항 보고 → **사용자가 after 캡처 + `-Compare`**
9. 화면 검증
10. 문서 갱신 (§9)
11. 커밋 2분할(코드 / 문서) + 푸시 — **사용자**

---

## 12. 착수 전 체크 (Claude Code)

- [ ] 본 문서를 끝까지 정독했다
- [ ] `docs/security/tasks/03-read-authz.md` + `reports/03-read-authz-report.md`를 읽었다 (선례)
- [ ] §2 "범위 밖 🚫" 8항목을 확인했다
- [ ] 수정 파일이 **2개**임을 확인했다
- [ ] 추가 라인이 **3~4줄**이고 기존 라인 수정·삭제가 **0줄**임을 확인했다
- [ ] `ErrorCode`를 쓰지 않는다는 것을 확인했다
- [ ] `git status`가 클린하다

### 중단하고 보고할 상황

- 두 파일 외 수정이 필요해 보일 때
- `HttpServletResponse` import가 없어 신규 import가 필요할 때
- `catch` 블록 구조가 본 문서 §4와 다를 때
- `compileJava`·`compileTestJava`·`bootRun` 중 하나라도 실패할 때
- 검색 확인 결과가 기대와 다를 때 (특히 `setStatus` 3건이 아닐 때)
- 본문 생성 코드를 건드려야 상태 코드를 바꿀 수 있어 보일 때
- **범위 밖 개선이 눈에 띌 때** — 고치지 말고 보고한다. 등재 후보다

---

## 13. 작업 원칙 리마인더

- **Surgical Changes.** task가 요구하는 것만. "온 김에" 개선 금지
- **task.md가 진실의 원천.** 의도 변경이 필요하면 사용자에게 보고
- 이 프로젝트엔 자동화 테스트가 없다. **새로 만들지 않는다**
- 모든 명령어는 Windows / PowerShell 5.1 기준. 모든 `.md`는 UTF-8
- 단계 완료 = **커밋 + 푸시까지**
