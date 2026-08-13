# 작업 04: 인증 실패 응답 정상화 (HTTP 200 → 401) — 작업 보고서

> 작업일: 2026-08-14
> 실행: Claude Code (Opus 5) / 검증: 사용자 (캡처 매트릭스 17항목 + 화면 검증)
> 명세: `docs/security/tasks/04-auth-failure-status.md` (**v2** — D1~D8 확정)
> 선행: 작업 E 읽기 경로 인가 — `7f66056`(코드) · `0b1cc92`(문서) · `18e9298`(배치 평평화)
> **상태: 코드 수정·자동 검증 완료. 캡처 판정과 화면 검증은 사용자 몫이며 §5가 비어 있다**

---

> **최종 상태 (2026-08-14): 검증 완료.** 캡처 17항목 전 항목 PASS · 화면 검증 통과 ·
> 성공 기준 8항목 전부 충족. 남은 것은 문서 갱신 반영과 커밋 2분할이다(§6-2·§6-3).

---

## 1. 변경 파일

**코드 2개** (명세 §5 그대로. 3번째 코드 파일 없음):

| 파일 | 처방 |
|---|---|
| `auth/filter/JwtAuthorizationFilter.java` | [F1] `catch` 블록에 `isCommitted()` 가드 + `SC_UNAUTHORIZED` |
| `auth/handler/CustomAuthFailureHandler.java` | [F2] 응답 기록 직전에 `SC_UNAUTHORIZED` |

**문서**: 본 보고서(신규). `spec.md`·`AGENTS.md` 갱신은 §6에 남겼다.

### git diff --stat (코드)

```
 .../java/com/insider/login/auth/filter/JwtAuthorizationFilter.java  | 6 ++++++
 .../com/insider/login/auth/handler/CustomAuthFailureHandler.java    | 3 +++
 2 files changed, 9 insertions(+)
```

**삭제 0줄 · 기존 라인 수정 0줄.** `+`만 있고 `-`가 한 줄도 없다는 것이 이 작업의 판정 기준이다.

`roleLessList` · `jsonResponseWrapper` · `jsonMap` · `failMsg` 산출 · `resultMap` ·
`CustomAuthSuccessHandler` · `CustomAuthenticationFilter` · `WebSecurityConfig` ·
`ErrorCode.java` · `GlobalExceptionHandler` · `application.yml` · `src/test/**` · 프론트엔드
**전부 무변경.**

> **라인 드리프트**: `JwtAuthorizationFilter`가 순증 6줄이라 아래쪽이 밀렸다.
> `jsonResponseWrapper` 선언은 `:129` → **`:135`**, `jsonMap.put` 3건은 `:143~145` → **`:149~151`**이다.
> **위치만 밀렸을 뿐 내용은 무변경**이며, 밀린 폭이 순증과 정확히 일치한다.
> `CustomAuthFailureHandler`는 삽입 지점(`:56`)이 예외 분기·`resultMap`보다 아래라
> `:1~56`이 전혀 밀리지 않았다.

---

## 2. 구현 내용

### [F1] `JwtAuthorizationFilter` — `catch` 블록 (`:115~121`)

```java
} catch (Exception e) {
    // 인증 실패는 401로 나가야 한다.
    // chain.doFilter 가 try 안에 있어 하위 처리 예외도 이 블록에 도달할 수 있고,
    // 그때는 응답이 이미 커밋돼 setStatus 가 조용히 무시된다. 커밋 여부를 먼저 본다.
    if (!response.isCommitted()) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
    // 예외 발생 시
    response.setContentType("application/json");   // ← 이하 무변경
```

- 삽입 위치는 명세 §4 [F1] 그대로 — `catch` 진입 직후, `setContentType`(현 `:123`) **앞**,
  `getWriter()`(현 `:124`)보다 반드시 먼저다
- **`isCommitted()` 가드는 D4 확정 사항이다.** `chain.doFilter`(`:108`)가 `try` **안**에 있어
  하위 처리 예외가 이 `catch`에 도달할 수 있고, 그 시점엔 응답이 커밋된 뒤라 가드가 없으면
  `setStatus`가 **무성 실패**한다. 커밋된 경로의 동작은 가드 덕분에 완전히 불변이다
- 기존 주석 `// 예외 발생 시`는 **삭제하지 않고 원위치에 뒀다** (기존 라인 무변경 원칙)
- 본문을 만드는 `jsonResponseWrapper(e)` 호출(현 `:126`)은 **한 글자도 건드리지 않았다**

### [F2] `CustomAuthFailureHandler` — 응답 기록 직전 (`:56~61`)

```java
jsonObject = new JSONObject(resultMap);

// 인증 실패는 예외 종류와 무관하게 401 단일. 상태 코드로 계정 존재 여부를 흘리지 않는다.
response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

response.setCharacterEncoding("UTF-8");
```

- 삽입 위치는 명세 §4 [F2](v2-1로 확정된 표현) 그대로 — `jsonObject = new JSONObject(resultMap);`
  **직후**, `setCharacterEncoding`(현 `:61`) **바로 앞**. `getWriter()`(현 `:63`)보다 먼저다
- **예외 분기 8종(`:25~51`)에 분기를 넣지 않았다** (D5). `BadCredentials`·`Locked`·`Disabled`·
  `AccountExpired` 어느 것이든 **401 단일**이다. 상태 코드로 계정 존재 여부를 흘리면
  본문이 이미 만들어 놓은 열거 축에 **하나를 더 더하는 것**이 된다
- `failMsg` 산출과 `resultMap` 구성은 삽입 지점보다 **위쪽**에 있고 무변경이다

### 신규 import — 0건

두 파일 모두 `jakarta.servlet.http.HttpServletResponse`를 이미 import 하고 있다
(필터 `:17` · 핸들러 `:5`). 명세 v2-2의 실측이 그대로 성립했다.

### ErrorCode — 사용 0건

작업 E(재사용만, 신규 0건)보다 한 칸 더 보수적인 **사용 자체가 0건**이다(D3).
근거는 명세 §3-3의 실증이다 — `@RestControllerAdvice`는 DispatcherServlet **밖**의 필터 예외에
닿지 않으므로(기준선 `07` vs `16`·`17`), 필터·핸들러에는 `ErrorCode`/`ErrorResponse`로
위임할 경로가 애초에 없다. 상태 코드 직접 세팅이 유일한 수단이다.

---

## 3. 검증 결과 (Claude Code 담당분)

### 자동 검증

| 항목 | 결과 |
|---|---|
| `compileJava` | **통과** (`BUILD SUCCESSFUL in 7s`, exit 0) |
| `compileTestJava` | **통과** (`UP-TO-DATE` — `src/test/**` 무변경이라 재컴파일 불필요. 통과 상태 유지) |
| `bootRun` | **정상 기동** — `Started Application in 9.436 seconds`, **ERROR 0건** |

기동 로그의 `DefaultSecurityFilterChain`에 `CustomAuthenticationFilter` → `JwtAuthorizationFilter`
순서가 그대로 남아 있음을 확인했다. **필터 체인 구성은 바뀌지 않았다.**

> `compileJava` 통과는 이 작업에서 특히 **아무것도 증명하지 않는다.** 상태 코드 세팅은
> 타입 오류를 낼 여지가 없어 컴파일이 언제나 통과한다. 삽입 **위치**가 틀려
> `getWriter()` 뒤에 놓였더라도 컴파일·기동 모두 통과하고 **런타임에 조용히 무시**된다.
> 실제 판정은 §5의 캡처 17항목이 한다.

### 검색 확인 5종 (명세 §10) — 전 항목 기대와 일치

**1) `.setStatus(` 프로덕션 전수 — 기대 3건 / 실측 3건 ✅**

```
CustomAuthenticationFilter.java:43: response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
JwtAuthorizationFilter.java:120:    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
CustomAuthFailureHandler.java:59:   response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
합계: 3건
```

착수 전 실측은 **1건**(`CustomAuthenticationFilter:43`)이었다. 기존 400 1건 + 신규 401 2건 = 3건으로
명세 v2-3의 기대값이 정확히 성립했다. **400은 손대지 않았다.**

**2) 두 파일의 `ErrorCode|ErrorResponse|BusinessException` — 기대 0건 / 실측 0건 ✅**

**3) `roleLessList` — 무변경 (2건) ✅**

```
57: List<String> roleLessList = Arrays.asList("/signUp","/registDepart",…"/wss/chatting");
60: if (roleLessList.contains((request.getRequestURI()))) {
```

`:57` 선언 + `:60` 사용 **2건이 정상**이다(명세 §10의 "1건" 서술은 선언만 센 것 — §4 참조).
**판정은 히트 수가 아니라 라인 내용의 동일성으로 했고**, 두 줄 모두 처방 전과 글자 단위로 같다.
`git diff`에 이 파일의 삭제·수정 라인이 0줄인 것이 같은 사실의 두 번째 증거다.

**4) 본문 생성 코드 무변경 ✅**

| 대상 | 실측 |
|---|---|
| 필터 `jsonResponseWrapper` | `:126`(호출) · `:135`(선언) — 내용 동일, 위치만 +6 드리프트 |
| 필터 `jsonMap.put` | `:149` `status:401` · `:150` `message` · `:151` `reason` — 3건 내용 동일 |
| 핸들러 `resultMap` | `:53` 생성 · `:54` `failType` put · `:56` `JSONObject` 변환 — 내용 동일, **드리프트 없음** |

**본문에 들어가는 `jsonMap.put("status",401)`은 그대로 살아 있다.** 이번 작업은 본문의 401을
빼는 것이 아니라 **HTTP 상태 줄에 401을 추가**하는 것이다. 성공 기준 3(본문 해시 동일)이
여기에 걸려 있다.

**5) M002·M003·M004 사용처 — 선언 3건뿐, 사용처 0건 ✅**

```
ErrorCode.java:21: EXPIRED_TOKEN(401, "M002", "만료된 토큰입니다."),
ErrorCode.java:22: INVALID_TOKEN(401, "M003", "유효하지 않은 토큰입니다."),
ErrorCode.java:23: UNSUPPORTED_TOKEN(401, "M004", "지원하지 않는 토큰 방식입니다."),
합계: 3건 (전부 선언)
```

**세 상수 모두 죽은 상수다.** 401로 선언돼 있으나 프로덕션 어디서도 쓰이지 않는다.
**이번 작업은 되살리지 않았다**(D3). → §7 등재.

---

## 4. 명세와 실물의 차이 — 2건

명세 §12는 "이 문서와 실물이 어긋날 때 — 실물이 정답이고, 문서를 고친다"고 정한다.
**둘 다 처방 내용에는 영향이 없는 계수·표기 차이**이며, 코드는 §4 코드 블록을 그대로 따랐다.

### 4-1. 추가 라인 수 — "3~4줄"은 **코드 라인 기준**이다 (사용자 확정)

| 기준 | 수 |
|---|---|
| 명세 §2 표기 | 추가 라인 합계 **3~4줄** |
| 실측 `git diff` | **+9줄** |
| 내역 | **코드 4줄** (F1의 `if`·`setStatus`·`}` 3줄 + F2의 `setStatus` 1줄) + **주석 5줄** |

§2의 "3~4줄"은 **코드 라인 기준**이고 주석·빈 줄은 별도로 센다. §4 코드 블록을 그대로 따라
diff가 +9줄이 되는 것이 **정상이며 불일치가 아니다**. 계수 기준의 차이일 뿐이다.

**주석 5줄은 지우지 않았다.** F1의 3줄은 `isCommitted()` 가드가 왜 필요한지(D4)를,
F2의 1줄은 왜 예외 종류별로 분기하지 않는지(D5)를 담고 있다. **결정의 근거이므로 코드에 남긴다.**
이 둘은 주석이 없으면 후속 세션이 "불필요한 가드"·"누락된 분기"로 오해할 수 있는 자리다.

### 4-2. §10 검색 3번의 기대값 — `roleLessList`는 **1건이 아니라 2건**이다

명세 §10은 `roleLessList`를 `:57` 1건으로 적었으나, `Select-String` 히트는 **2건**이다 —
`:57` 선언과 `:60` 사용(`if (roleLessList.contains(...))`). 명세가 선언만 센 것이다.

**판정 기준을 히트 수가 아니라 라인 내용의 동일성으로 잡았다**(사용자 확정).
2건이 나오는 것이 무변경의 정상 상태이며, 중단 사유가 아니다.

> 착수 전 실물 대조에서 **명세 v2의 그 외 전제·라인 번호는 전부 일치**했다.
> 특히 삽입 지점 두 곳(`catch (Exception e)` `:115` / `jsonObject = new JSONObject(resultMap);` `:56`)과
> `HttpServletResponse` import 위치(`:17` / `:5`), 착수 전 `.setStatus(` 1건이 그대로였다.

---

## 5. 캡처 17항목 판정 — **전 항목 PASS** (사용자 수행)

```powershell
cd C:\temp\auth-status
.\capture-auth-status.ps1 -Phase after *>&1 | Tee-Object -FilePath .\after-console.txt
.\capture-auth-status.ps1 -Compare        *>&1 | Tee-Object -FilePath .\verdict-console.txt
```

`$Dormant = $null`이라 `18`(휴면 계정)은 캡처되지 않았다 — **17항목**이다.
DB에 휴면 계정이 없었고, 검증을 위해 계정을 새로 만들지 않는다(명세 §2).

### S1. 인증 실패 — 200 → 401 (이번 작업의 본체)

판정식: **해시 동일 AND before=200 AND after=401**

| # | 항목 | Before | After | 본문 | 판정 |
|---|---|---|---|---|---|
| 08 | 무토큰 · 상세 | 200 / 90B | **401** / 90B | `hashSame=True` | PASS |
| 09 | 만료 토큰 | 200 / 91B | **401** / 91B | `hashSame=True` | PASS |
| 10 | 위조 토큰(서명) | 200 / 91B | **401** / 91B | `hashSame=True` | PASS |
| 11 | 헤더 형식오류 | 200 / 90B | **401** / 90B | `hashSame=True` | PASS |
| 12 | 빈 토큰 | 200 / 90B | **401** / 90B | `hashSame=True` | PASS |
| 13 | 무토큰 · 첨부 | 200 / 90B | **401** / 90B | `hashSame=True` | PASS |
| 14 | 로그인 · 틀린 비밀번호 | 200 / 62B | **401** / 62B | `hashSame=True` | PASS |
| 15 | 로그인 · 없는 사번 | 200 / 55B | **401** / 55B | `hashSame=True` | PASS |

- **8건 전부 `hashSame = True`.** 바이트 수도 전부 동일하다. 상태 코드만 바뀌고 응답 본문은
  한 바이트도 달라지지 않았다 — **D2(본문 무변경)가 숫자로 증명됐다**
- `08~13`이 [F1], `14`·`15`가 [F2]의 결과다. **양쪽 다 전환돼 D1(2곳 처방)이 실증됐다**
- 캡처 콘솔의 `[본문 401]` 표시가 `08~13`에 그대로 남아 있는 것도 정상이다.
  필터 본문의 `"status":401` 필드를 건드리지 않았다는 뜻이다

### S0. 정상 경로 (먼저 본다)

| # | 항목 | 결과 | 판정 |
|---|---|---|---|
| 01 | 상세 · 기안자 · D1 | 200 / 1425B | PASS (해시 동일) |
| 02 | 목록 · given | 200 / 5968B | **PASS — 키 순서만 다름 (R11)** ★ |
| 03 | 파일 · 기안자 · D1 | 200 / 55048B | PASS (해시 동일) |
| 04 | `roleLessList` · 무토큰 (`/announces`) | 200 / 107245B | PASS (해시 동일) |
| 05 | `roleLessList` · 무토큰 (`/showAllMembersPage`) | 200 / 31852B | PASS (해시 동일) |
| 06 | 로그인 성공 | 200 / 877B | PASS (shape 동일 + 성공 메시지 존재) |

`04`·`05`가 불변이므로 **`roleLessList` 통과 경로는 이번 처방의 영향을 받지 않았다** — D6 실증.

### S2. 동결

| # | 항목 | 결과 | 판정 |
|---|---|---|---|
| 07 | 정적 리소스 · 무토큰 | 500 / 84B | PASS (해시 동일 + 상태 동일) |
| 16 | 깨진 JSON 본문 | 500 / 8091B | PASS (shape 동일 + 상태 동일) |
| 17 | 사번 int 범위 초과 | 500 / 9352B | PASS (shape 동일 + 상태 동일) |

`16`·`17`은 `CustomAuthenticationFilter`를 빠져나간 예외다. **401로 바뀌지 않았으므로 범위 이탈이 없다.**

> ⚠ 기준선 단계에서 확인된 것: `17`(사번 int 범위 초과)은 `SC_BAD_REQUEST` 분기가 아니라
> **500으로 떨어진다.** Jackson의 범위 초과 역직렬화가 `NumberFormatException`이 아니라
> `IOException` 계열로 잡혀 `RuntimeException`으로 재던져지기 때문으로 보인다.
> 명세 §2가 `CustomAuthenticationFilter`를 하드 가드로 묶었으므로 **손대지 않았고, 동결만 확인했다.**

### ★ 02 — R11 정규화 재판정이 실제로 발동했다

`02`는 바이트 수가 5968B로 동일한데 `hashSame = False`, `canonSame = True`로 판정됐다.
**키 순서만 다르고 내용은 같다.** 애플리케이션 재기동을 건너며 Jackson 파생 속성의 직렬화
순서가 바뀐 것으로, 작업 E `reports/03` §5의 `08·09·11·12`와 같은 계열이다.

> **명세 §8 R1의 예상과 반대 방향이었다.** R1은 **인증 실패 본문**(`json-simple`의 `JSONObject`,
> HashMap 기반)의 키 순서 변동을 우려했는데, `08~15`는 재기동을 건너서도 전부 해시가 동일했고
> **정상 응답 쪽(`02`, Jackson)이 흔들렸다.**
> 정규화 재판정이 없었다면 **FAIL로 오판할 건이었다.** R11 대응을 유지한 판단이 맞았다.
> → 명세 v3-2로 정정 기록.

### 화면 검증 (성공 기준 8) — 전 항목 통과

| # | 항목 | 결과 |
|---|---|---|
| 1 | 틀린 비밀번호 로그인 — Network | **`POST /login` → 401** (`Login.js:96`, fetch) |
| 2 | 틀린 비밀번호 로그인 — 화면 | `로그인 중에 오류가 발생했습니다. 다시 시도해주세요.` alert — **처방 전과 동일** |
| 3 | 정상 로그인 | 진입 정상 |
| 4 | 결재 목록 · 상세 · 첨부 다운로드 | 정상 (S0 재확인) |

`Login.js`의 `=== 401` 분기가 이번 작업으로 **처음 실행됐다.** 명세 §3-4의 실물 확인대로
그 분기의 내용이 기존 분기와 동일해 **화면은 바뀌지 않았다** — R4 해소.

CORS preflight는 200, 본 요청은 401로 분리돼 찍혔다. **preflight가 401의 영향을 받지 않는다** —
이번 처방이 CORS 경로를 건드리지 않았음이 부수적으로 확인됐다.

> 화면만으로는 200/401 전환 여부를 가를 수 없다. 두 분기의 alert 문구와 폼 초기화가 동일하기
> 때문이다. **Network 탭 확인이 화면 검증의 실질적 판정 근거다.**

---

## 6. 남은 일 (사용자)

### 6-1. 캡처 판정 + 화면 검증 — ✅ **완료**

§5. **캡처 17항목 전 항목 PASS + 화면 검증 4항목 통과.**
R3(A 토큰 만료) 이전에 `after` 캡처를 끝냈다.

### 6-2. 문서 갱신 (명세 §9)

| 문서 | 변경 |
|---|---|
| `spec.md` §4-3 | "인증 실패가 HTTP 200" 행 **제거** |
| `spec.md` §4-5 | **신설** — 작업 04 (작업 E의 §4-4와 같은 형식) |
| `spec.md` §4 표 | 작업 04 행 추가 |
| `spec.md` §4-3 | **등재 추가 4건** — 명세 §9-1의 실측 3건 + 죽은 `ErrorCode` 상수 3건(§7-1) |
| `AGENTS.md` | 로드맵 `D. 등재만`에서 "인증 실패 200" 제거 · `F. 인증 실패 응답 정상화 ✅` 추가 · "현재 진행 작업" 갱신 · **"작업 F에서 확정된 것" 선례 절 신설** |
| `tasks/04-auth-failure-status.md` | **v3 정정 5건** 추가 (§4-1·§4-2의 두 건 + R11 발동 방향 + 등재 4건 + 결과) |
| `docs/security/tools/` | `capture-auth-status.ps1` 편입 (명세 §9-4) |

> ⚠ **`tokens.ps1`과 캡처 산출물은 리포에 넣지 않는다** — 유효 토큰과 사원 PII가 들어 있다.

### 6-3. 커밋 2분할

```powershell
cd C:\env\GitHub\INSIDER\LOG-IN-Refactoring

# ① 코드 — 정확히 2개여야 한다
git add final/src
git diff --cached --stat
git commit -m "..."

# ② 문서
git add -A docs/ AGENTS.md
git diff --cached --stat
git commit -m "..."
```

> ⚠ `docs/security/tasks/04-auth-failure-status.md`는 아직 untracked다. ②에 함께 담긴다.

---

## 7. 범위 밖 · 등재 항목

작업 04 범위 밖이므로 손대지 않았다. `spec.md` §4-3 편입을 제안한다.
**반영 여부는 문서 갱신 단계에서 사용자가 판단한다.**

### 7-1. 이번 작업에서 새로 확인된 것

| 항목 | 지점 | 근거 |
|---|---|---|
| **죽은 `ErrorCode` 상수 3건** | `ErrorCode.java:21~23` — `EXPIRED_TOKEN`(M002) · `INVALID_TOKEN`(M003) · `UNSUPPORTED_TOKEN`(M004) | **검색 5번 실측: 선언 3건, 사용처 0건.** 셋 다 401로 선언돼 있으나 프로덕션 어디서도 쓰이지 않는다. 이번 작업이 되살리지 않았다(D3 — 본문 무변경이라 꺼낼 자리가 없다). 토큰 오류를 `ErrorCode` 체계로 통일하려면 필터가 `GlobalExceptionHandler`에 닿지 못하는 구조(§2)부터 풀어야 한다 |

### 7-2. 명세 §9-1이 등재를 지시한 실측 3건

| 항목 | 지점 | 근거 |
|---|---|---|
| **필터 예외 시 스택트레이스 전문 노출** | `CustomAuthenticationFilter`의 `IOException → RuntimeException`이 필터를 빠져나가 Spring 기본 `/error`로 떨어진다 | 기준선 `16`·`17` — 인증 없이 `POST /login`만으로 8~9KB. 내부 패키지 구조·라이브러리 노출 |
| **만료 토큰과 위조 토큰이 구분되지 않는다** | `TokenUtils.isValidToken()`이 예외를 삼키고 `false`만 반환 → `jsonResponseWrapper`의 `Token Expired`·`SignatureException` 분기가 **도달 불가능한 죽은 코드** | 기준선 `09` = `10` 해시 동일 |
| **로그인 실패 메시지에 의한 계정 열거** | `CustomAuthFailureHandler`가 "존재하지 않는 사용자" / "아이디 또는 비밀번호가 틀립니다"를 구분 | 기준선 `14`(62B) ≠ `15`(55B). **D5가 상태 코드 축은 만들지 않았으나 본문 축은 그대로 남아 있다** |

### 7-3. 명세 §2가 하드 가드로 묶어 둔 것 (유지)

| 항목 | 상태 |
|---|---|
| 응답 본문 이중 구조 — `failType` vs `status/message/reason`, `ErrorResponse` 미통일 | **D2. 등재만** |
| `roleLessList` — `/approvals`·회원 조회·프로필 수정이 무인증 개방 | **D6. 기준선 `04`·`05`가 현재 동작을 고정.** 이 경로는 `chain.doFilter` 후 `return`이라 `catch`에 진입하지 못해 이번 처방이 닿지 않는다 |
| `AuthenticationEntryPoint` 0건 — Spring 기본 401 진입점 미설정 | 신설하면 필터 흐름이 바뀐다. **등재만** |
| `server.error.include-stacktrace` | 설정 파일 미변경. **등재만** |
| CORS 전역 개방 · 저장형 XSS · `TestController` 엔드포인트 | 로드맵 `D. 등재만` 유지 |

### 잔여 위험

| # | 위험 | 상태 |
|---|---|---|
| **R2** | `catch`가 **커밋된 응답**에 도달하는 경로에서는 여전히 상태 코드가 200이다 | D4 가드가 `setStatus`의 **무성 실패를 없앴을 뿐**, 그 경로를 401로 바꾸지는 않는다. 커밋 이후엔 HTTP 규약상 바꿀 수 없다. **`chain.doFilter`를 `try` 밖으로 빼는 구조 변경은 범위 밖**(제어 흐름이 바뀐다) |
| **R5** | 다른 프론트 화면이 200을 성공으로 오판하고 있었다면 동작이 바뀐다 | **해소.** 화면 검증(§5)에서 로그인·목록·상세·첨부 다운로드 전부 정상 확인. 토큰 만료 경로는 프론트에 처리가 **전무**해 만료 시 지금도 화면이 깨지며, 깨지는 방식만 달라진다 — 회귀가 아니다 |
| — | 삽입 **위치** 오류는 컴파일·기동에 드러나지 않는다 | **해소.** `getWriter()` 뒤에 놓였다면 런타임에 조용히 무시됐을 것이다. §5 캡처에서 `08~15`가 실제로 401로 전환됐으므로 두 삽입 위치 모두 유효하다 |
