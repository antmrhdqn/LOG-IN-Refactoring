# 작업 05 — 인증 경계 정상화 (`roleLessList` 원소 전수 판정)

> 작성: 2026-08-14 (**v2** — v1.1 §14 리뷰 완료·절 삭제, 실측 반영)
> 스트림: 보안 결함 정리. **리팩토링이 아니다.**
> 선행: 작업 F 인증 실패 응답 정상화 완료 — `c79006f`(코드) · `<문서 해시>`(문서)
> 근거: `docs/security/spec.md` §4-3 등재 행 "`GET /showAllMembersPage`가 무인증" ·
> 본 문서 §3 정적 대조 전수 판정 (2026-08-14, 명세 리뷰 세션 + Claude Code 조사)
> 선례 정본: **`docs/security/precedents.md`** (S1~S9 · P1~P3 · R11)

---

## v2 정정 (2026-08-14)

v1.1의 §14 "리뷰 대기 항목"이 닫혔다. 해소된 항목은 아래로 **옮겼고**, §14 절은 삭제했다.

| v1.1 §14 | 처분 | 옮긴 곳 |
|---|---|---|
| 🔴 1 `{A사번}`·`{ancNo}`·`{D1}` 확정 | **해소** | §7 **D13** (검증 데이터·계정 정책) |
| 🔴 2 파괴적 실측 가드 (`12`·`20`) | **해소 — 실측으로** | §3-6 · §10 (`12`=415 / `20`=500, 레코드 생성 0건) |
| 🔴 3 `16`·`17` 무토큰 캡처 포함 여부 | **해소** | §7 **D13** (테스트 계정이라 초기화돼도 무해) |
| 🔴 4 `/` 원소 처분 | **미해소.** 기준선 `21` 결과를 보고 판단 | §0 착수 상태 표 |
| 🟡 5 주석 분량 | **해소** | §4 [G1] (12줄 → 8줄) · §7 **D11** |
| 🟡 6 등재 7건 | **해소** | §9-1 (6건 §4-3 / `POST /signUp` 1건은 §4 분할표) |
| 🟡 7 WebSocket 선례 승격 | **해소 — 승격하지 않는다** | §7 **D6** · §9-2 |
| 🟡 8 §1 목표 3번째 문단 | **해소 — 목표에서 제외** | §3-5 · §7 **D12** |
| 🟢 9~12 사실 대조 | **미해소** | §0 착수 상태 표 |

### 착수 후 실측으로 추가된 것 (2026-08-14, 실행 세션)

| # | 실측 | 반영 |
|---|---|---|
| **M1** | **`GET /announces/{ancNo}`가 호출마다 `hits`를 +1 한다** (`AnnounceController:72~74` → `incrementHits`). `hits`는 상세·목록 **양쪽 응답 본문에 실린다** | 캡처 `02`·`03`을 **해시 판정에서 제외**하고 shape 판정으로 옮겼다 (§10). 그대로 뒀으면 처방과 무관하게 무조건 FAIL이었다 |
| **M2** | `POST /announces` 무토큰·본문 없음 → **415** (`C007`) · `POST /signUp` 무토큰·본문 없음 → **500** (`C999`). **둘 다 레코드 생성 0건** | §3-6 B2 절반 해소 · §10 매트릭스 |
| **M3** | 멀티파트 파트 본문이 UTF-8로 디코딩되지 않는다 (한글이 전부 깨져 저장됨) | §9-1 등재 |
| **M4** | `PUT /announces/{ancNo}`가 제목·내용만 덮어써 깨진 `ancWriter`가 남는다 | §9-1 등재 |
| **M5** | 검증용 공지 **`ancNo = 26`** 생성 완료 | §7 D13 · §10 매트릭스 |

---

## 0. 착수 상태

| 항목 | 상태 |
|---|---|
| `roleLessList` 원소 전수 판정 | **완료. 12개 전부** (§3-2) — 살아있음 5 / 죽음 6 / 불확정 1 |
| 매칭 메커니즘 확인 | **완료.** `contains(getRequestURI())` 단일. `startsWith`·`matches`·`AntPathMatcher` **0건** (`JwtAuthorizationFilter:60`) |
| `server.servlet.context-path` | **미설정 확정** (`application.yml:27~32`은 `encoding`뿐). ⇒ §3-1의 전제가 성립한다 |
| 다른 경로 화이트리스트 | **전수 완료. 1건** — 정적 리소스 5패턴(`WebSecurityConfig:39~41`). **범위 밖**(§7 D7) |
| 프론트 회귀 위험 | **경로별로 해소** (§3-3). 닫을 4경로는 회귀 0, `/signUp`은 회귀 발생 → 범위 밖 |
| 라인 번호 재확인 | **완료.** 선언 `:57` / 판정문 `:60` |
| 처방 삽입 위치 | **완료.** `:57` 한 줄 재작성 |
| 신규 import 필요 여부 | **0건 확정.** 기존 `Arrays.asList` 그대로 |
| **명세 리뷰** | ✅ **완료 (v2).** 🟡 4건 해소 → D11·D12·D6·§9 / 🔴 3건 해소 → D13 |
| 검증 데이터 | ✅ **확정.** `{ancNo}` = **26** (신규 생성) · `{A사번}`·`{D1}`은 작업 F 값 승계 (§7 D13) |
| 기준선 캡처 | ❌ **미완. 착수 전 필수** — `C:\temp\authn-boundary\` 22항목 (§10) |
| 검증 토큰 | ⚠ **작업 F 토큰이 2026-08-14 시점 유효함을 확인**했으나 **2026-08-15 06:35 KST 만료.** 기준선 캡처가 그 이후면 재발급 |
| 환경변수 | `JWT_KEY` · `DB_USERNAME` · `DB_PASSWORD` 3종 필요 (없으면 `bootRun` 기동 실패) |
| **잔여 미해소 — 🔴 1건** | `/` 원소 처분 (§7 D5). 기준선 `21` 결과를 보고 v3에서 바꿔도 된다. **착수를 막지 않는다** |
| **잔여 미해소 — 🟢 4건** | v1.1 §14의 사실 대조 9~12 (아래) |

### 잔여 🟢 사실 대조 4건 (읽기 전용 · 착수를 막지 않는다)

| # | 확인할 것 | 상태 |
|---|---|---|
| 9 | §4 [G1] Before 블록이 `JwtAuthorizationFilter:57` 원문과 **문자 단위 일치**하는지 + 선언 라인 번호 드리프트 | 미확인 |
| 10 | §3-2 표 12행의 파일:라인 · HTTP 메서드 · URI 패턴 전수 재검증. **직전 조사를 참조하지 말고 새로** | 미확인 |
| 11 | §10 검색 확인 2번의 정규식 `members/\{`가 PS 5.1 `Select-String`에서 의도대로 동작하는지 + 착수 전 기준 히트 수 | 미확인 |
| 12 | 작업 F 토큰 유효성 | ✅ **해소** — 2026-08-14 `GET /departments` 200 확인 |

> ⚠ 9~12 위임 시 제약: 읽기 전용 · **무인증 POST/PUT 요청 0건**.
> (§10 `12`·`20`의 파괴적 실측은 v2에서 **이미 수행돼 결과가 기록됐다** — 재실행 불필요)

> ⚠ **기준선 캡처가 끝나기 전에는 착수하지 않는다.** 본 명세 §3-6에 기준선에서만 확정되는
> 항목이 있고, 그중 하나(**캡처 그룹** S2의 before가 401인가)는 **반증되면 명세를 다시 써야 한다.**
>
> ⚠ **이름 충돌 주의.** 이 문서의 `S0`~`S3`은 **캡처 그룹** 이름이고,
> `precedents.md`의 `S1`~`S9`는 **선례** 번호다. 서로 다른 체계다.

---

## 1. 목표

**`roleLessList`가 무인증으로 열어 둔 경로 중, 프론트 회귀 없이 닫을 수 있는 것을 닫는다.
동시에 어떤 URI와도 매칭되지 않는 죽은 원소를 제거해 리스트를 자기설명적으로 만든다.**

이것은 "정리"가 아니다. 두 가지를 실제로 닫는다.

1. **토큰 없이 전 사원 90명의 이름·부서·직급·입사일·상태가 나가는 것**을 닫는다 (`GET /showAllMembersPage`)
2. **토큰 없이 전사 공지를 등록할 수 있는 것**을 닫는다 (`POST /announces` — 인가도 0건)

> **목표는 이 둘뿐이다.** v1.1은 여기에 "작업 06(`POST /signUp` 무인증 계정 생성)의 성립 조건을
> 무너뜨린다"를 셋째 목표로 두었으나 **v2에서 뺐다.** 이 작업의 성공 기준·캡처 22항목 어디에도
> 그것을 판정하는 항목이 없다. 근거는 §3-5에 남기고, 확인은 작업 06이 한다 (§7 **D12**).

### 성공 기준

1. `roleLessList`의 원소가 **12개 → 4개**가 된다 (`/signUp` · `/login` · `/` · `/wss/chatting`)
2. 캡처 **S1 그룹 5항목**이 무토큰 요청에서 **401**을 반환한다
3. 같은 5항목의 **응답 본문이 인증 실패 본문 하나로 수렴한다** — REF(캡처 `19`)와 해시 동일
4. 캡처 **S0 정상 경로 9항목**이 불변이다 — **해시 동일 6항목** (`01`·`04`~`08`) /
   **shape 동일 3항목** (`02`·`03`은 `hits` 증가, `09`는 토큰 재발급 — §10 M1)
5. 캡처 **S2 그룹 5항목**이 before·after 모두 **401 + 해시 동일**이다 — §3-1 전제의 실증
6. 캡처 **S3 동결 3항목**이 상태·본문 모두 불변이다
7. 신규 `ErrorCode` **0건**, `ErrorCode` **사용 0건**, 신규 `import` **0건**
8. `compileJava` + `compileTestJava` + `bootRun` 통과
9. 화면 검증 4항목 통과 — 구성원 목록 · 공지 목록/상세/등록 · 채팅 · 로그인

### 성공 기준이 아닌 것

- `POST /signUp` 무인증 차단 — **범위 밖. 작업 06** (D2)
- 공지 `PUT`/`DELETE`의 인가 도입 — **범위 밖, 등재만** (§9)
- `/wss/chatting`의 인증 도입 — **구조적으로 불가. 등재만** (D6)
- `member/**`·`commute/**` 읽기 **인가** — 이번 작업은 **인증**만 다룬다 (D3)
- 응답 본문 구조 통일 — 이번 작업은 본문 생성 코드를 건드리지 않는다

---

## 2. 경계 (확정)

### 범위 안 — 코드 1파일

| 파일 | 수정 | 라인 |
|---|---|---|
| `auth/filter/JwtAuthorizationFilter.java` | `roleLessList` 선언 1줄 재작성 (원소 8개 제거) + 주석 | 57 |

**변경 라인: 1줄 재작성. 삭제 라인 1 / 추가 라인 1 + 주석.**

### 범위 밖 (명시 · 🚫 하드 가드)

| 대상 | 이유 |
|---|---|
| 🚫 **`/signUp` 원소** | `MemberAPICalls.js:75`가 `axios.post(url, formData)` — **config 인자가 없다.** 닫으면 구성원 등록 화면이 정지한다. 프론트 인터셉터 **0건**이라 공통 헤더도 없다 (§3-3). **작업 06** |
| 🚫 **`/login` 원소** | 인증 시작점. 제거하면 로그인 자체가 불가능하다 |
| 🚫 **`/wss/chatting` 원소** | 브라우저 `WebSocket` 생성자는 커스텀 헤더를 지정할 수 없다. `Room.js:19`가 만드는 문자열은 헤더가 아니라 연결 후 메시지 본문이다. **프론트 수정으로도 닫히지 않는다** (D6) |
| 🚫 **`/` 원소** | 판정 불확정(`WebConfig:26` `addResourceHandler("**")`와의 상호작용). 닫는 실익 0, 회귀 리스크만 (D5) |
| 🚫 **판정문 `:60`** | `contains(getRequestURI())`를 `AntPathMatcher` 등으로 **바꾸지 않는다.** 바꾸면 죽은 원소가 되살아나 `/members/{id}`·`updateProfile`이 **무인증 개방**된다 (D4) |
| 🚫 **`jsonResponseWrapper` · `catch` 블록** | 작업 F가 확정한 형태. 본문·상태 코드 로직 무변경 |
| 🚫 **정적 리소스 화이트리스트** (`WebSecurityConfig:39~41`) | Spring 표준 설정이고 `/css/**` 등 5패턴 한정. 결함이 아니다 (D7) |
| 🚫 **`@PreAuthorize` 도입** | 거부 시 `AccessDeniedException`이 `GlobalExceptionHandler` catch-all에 걸려 **500이 나간다** (실측분) |
| 🚫 **`ErrorCode` · `ErrorResponse` 사용** | D8 |
| 🚫 **공지 `PUT`/`DELETE` 인가** | 인가 0건 확인됨(`announce` 패키지 전수). 이번 작업은 **인증**이 목표. §9 등재 |
| 🚫 **`member/**` 읽기 인가 · `downloadMemberInfo` · `updateProfile` 인가** | 별개 작업 (D3) |
| 🚫 프론트엔드 전체 | 백엔드 1줄로 끝난다 |

---

## 3. 실측 근거

> 출처 구분 — **[정]** 정적 코드 대조(확정) / **[프]** 프론트 리포 grep(확정) / **[기]** 기준선 캡처에서 확정(미완)

### 3-1. 매칭은 **완전 문자열 일치** 단 하나다 [정]

```java
// JwtAuthorizationFilter.java:60
if (roleLessList.contains((request.getRequestURI()))) {
```

- `List.contains` = `String.equals`. **자리표시자·쿼리스트링·HTTP 메서드를 보지 않는다**
- 같은 파일에 `startsWith` · `matches` · `AntPathMatcher` **0건**
- `getRequestURI()` 반환값에 `getContextPath()` 제거·`substring`·`trim` 등 **가공 0건**
- **`server.servlet.context-path` 미설정** — 설정돼 있었다면 접두사가 붙어 이 전제가 통째로 무너진다

⇒ **자리표시자(`{memberId}`)가 든 원소는 실제 URI(`/members/240001`)와 절대 일치하지 않는다.**
⇒ **쿼리스트링은 제외되므로 `/announces?page=0&…`도 원소 `/announces`에 매칭된다.**

### 3-2. 원소 전수 판정 — 12개 [정]

| # | 원소(원문) | 대응 매핑 | 판정 | 처분 |
|---|---|---|---|---|
| 1 | `/signUp` | `MemberController:83` POST | 살아있음 | 🚫 **유지** (프론트 회귀) |
| 2 | `/registDepart` | **0건** (부서 등록은 `POST /departments` — `DepartmentController:70`) | 살아있음 · **핸들러 없음** | **삭제** |
| 3 | `/registPosition` | **0건** (직급 등록은 `POST /position` — `PositionController:28`) | 살아있음 · **핸들러 없음** | **삭제** |
| 4 | `/login` | `WebSecurityConfig:114` (필터) | 살아있음 | 🚫 **유지** |
| 5 | `/members/{memberId}` | `MemberController:195` GET — 실 URI `/members/240001` | **죽음** (자리표시자) | **삭제** |
| 6 | `/showAllMembersPage` | `MemberController:379` GET | 살아있음 | **삭제** |
| 7 | `/announces` | `AnnounceController:35` GET **+ `:110` POST** | 살아있음 · **2엔드포인트** | **삭제** |
| 8 | `/announces/{ancNo}, /approvals` | 없음 (콤마 포함 한 문자열) | **죽음** | **삭제** |
| 9 | `/resetMemberPassword/{memberId}` | 실 매핑은 **`/resetPassword/{memberId}`** (`MemberController:145`) | **죽음** (경로명 오기 + 자리표시자, **이중**) | **삭제** |
| 10 | `/members/updateProfile/{memberId}` | `MemberController:275` PUT | **죽음** (자리표시자) | **삭제** |
| 11 | `/` | 루트 컨트롤러 매핑 없음. `WebConfig:26` `addResourceHandler("**")` 상호작용 | **불확정** | 🚫 **유지** |
| 12 | `/wss/chatting` | `WebSocketConfig:24` · `WebSocketBrokerConfig:31` | 살아있음 | 🚫 **유지** (구조적 불가) |

**삭제 8개 → 잔존 4개.**

⚠ **삭제 8개는 성격이 두 갈래다. 판정 그룹이 갈린다.**

| 갈래 | 원소 | 제거 시 동작 |
|---|---|---|
| **가. 매칭되던 것** | 2 · 3 · 6 · 7 | **변화 있음.** 무토큰 요청이 401로 전환 → 캡처 **S1** |
| **나. 매칭된 적 없는 것** | 5 · 8 · 9 · 10 | **변화 0.** 애초에 통과한 적이 없다 → 캡처 **S2** |

> 미매핑 원소(2·3)로 들어온 요청은 현재 필터를 통과한 뒤 핸들러가 없어 실패한다.
> `application.yml:3` `throw-exception-if-no-handler-found: true` · `GlobalExceptionHandler:61~63`
> 경로일 것으로 보이나, `addResourceHandler("**")`가 먼저 잡을 수도 있다.
> **before 상태 코드를 미리 적지 않는다. [기]에서 실측값으로 채운다** (§3-6).

### 3-3. 프론트 토큰 첨부 전수 [프]

**인터셉터·공통 헤더 층이 0건이다.** 전 파일이 호출부마다 `Authorization`을 직접 박는다.
⇒ **config 인자가 없으면 그대로 "토큰 없이 나간다"를 의미한다.** 추론이 아니다.

| 경로 | 호출부 | 토큰 | 판정 |
|---|---|---|---|
| `GET /showAllMembersPage` | `MemberAPICalls.js:108` `{ headers }` → `:7~11`(`:10` Authorization) | ✅ | 닫아도 회귀 0 |
| `GET /announces` | `AncAPICalls.js:14` `{ headers }` → `:8` | ✅ | 닫아도 회귀 0 |
| `POST /announces` | `AncAPICalls.js:32~38` 인라인 `:36` | ✅ | 닫아도 회귀 0 |
| `GET/PUT/DELETE /announces/{ancNo}` | `:23` · `:56~61` · `:47` | ✅ | (원소가 죽어 이미 인증 필요) |
| `/registDepart` · `/registPosition` | **호출 0건.** 등록 모달은 살아 있으나 실제 URL이 다르다 (`DepartmentAPICalls.js:40~42` → `POST /departments`, `PositionAPICalls.js:41` → `POST /position`) | — | 닫아도 회귀 0 |
| `POST /signUp` | `MemberAPICalls.js:75` `axios.post(url, formData)` — **config 없음** | ❌ | 🚫 **닫을 수 없다** |
| `/wss/chatting` | `Room.js:50` `new WebSocket(...)` | ❌ **구조적 불가** | 🚫 **닫을 수 없다** |

교차 검증: `MemberAPICalls.js:21` `GET /members/${memberId}`도 `{ headers }`다 — 원소 5가 죽어
**이미 인증이 필요한 상태**라는 §3-2 판정과 프론트 동작이 일치한다.

### 3-4. `/announces` 원소 1개가 엔드포인트 2개를 연다 [정]

`contains()`는 HTTP 메서드를 보지 않는다.

| 파일:라인 | 메서드 | URI |
|---|---|---|
| `AnnounceController:35` | GET | `/announces` |
| **`AnnounceController:110`** | **POST** (`consumes = multipart/form-data`) | `/announces` |

그리고 **`announce` 패키지 전체에 인가 검증이 0건**이다
(`getTokenInfo|isAdmin|SecurityContextHolder|@PreAuthorize|getCurrentMemberId` 전수 → No matches).

⇒ **토큰 없이 전사 공지를 등록할 수 있다.** 이것이 이번 작업의 두 번째 본체다.
(`PUT`/`DELETE /announces/{ancNo}`는 자리표시자라 인증은 필요하나 **인가는 0건** → §9 등재)

### 3-5. `/signUp`을 왜 못 닫는가, 그리고 이번 작업이 무엇을 무너뜨리는가 [정][프]

`POST /signUp`은 무인증이고, 조사 결과 **임의 role 계정 생성의 조건이 대부분 성립한다.**

| 조건 | 결과 |
|---|---|
| `MemberDTO.role` 존재 | `MemberDTO:28`. `@JsonIgnore` **없음** (`WRITE_ONLY`는 `getPassword()` `:98`에만) |
| 서버가 role을 덮어쓰는가 | **0건.** signUp이 세팅하는 것은 `password:89` · `memberId:102` · `imageUrl:127`뿐. `setRole` 전수 1건은 `JwtAuthorizationFilter:81`(토큰 파싱용) |
| `MemberRole` 값 | `MEMBER` / `ALL` / **`ADMIN`** (`MemberRole:5~7`) |
| 필수 여부 | `Member.role`이 `nullable=false`(`Member:35~37`) ⇒ **호출자가 반드시 넣어야 하고, 그 값이 곧 저장값** |

**그럼에도 이번 작업 범위가 아니다** — `MemberAPICalls.js:75`에 토큰이 없어 닫으면 등록 화면이 정지한다.
프론트 수정 + ADMIN 인가 + 파괴적 실측이 함께 붙는다 (D2).

**05가 성립 조건 하나에 영향을 준다 — 단, 이것은 05의 목표도 성공 기준도 아니다 (D12).**
신규 회원 저장에는 유효한 `departNo`·`positionLevel`이 필요하고, 그 값을
**무인증으로 얻을 수 있는 경로는 `GET /showAllMembersPage` 하나뿐**이다
(`ShowMemberDTO:21~22`에 `departmentDTO`·`positionDTO` 포함).
`GET /departments`·`GET /showAllPosition`은 `roleLessList`에 없어 이미 인증이 필요하다.

> **📌 작업 06 착수 시 재확인할 것** — "05가 정보원을 없앴다"를 **전제로 받아들이지 말 것.**
> 05 이후 무인증으로 유효한 `departNo`·`positionLevel`을 얻을 경로가 실제로 남아 있는지
> **06이 직접 확인한다.** 06은 어차피 파괴적 실측(계정 생성)을 하므로 그 자리에서 확인된다.
>
> 미확정 2건 (정적으로 판단 불가, **05에서 확인하지 않는다** — 06으로 이월):
> `modelMapper`가 `MemberDTO.departmentDTO → Member.department`를 실제로 매핑하는지,
> `cascade=PERSIST`가 임의 `departNo`로 신규 Department를 영속화하는지.
> ⚠ **후자가 참이면 유효한 `departNo`를 알 필요 자체가 없어져 위 "영향"이 성립하지 않는다.**
>
> 또한 `@RequestPart("memberProfilePicture")`가 **필수**라 파일 파트 없이는 호출이 성립하지 않는다
> — **실측 확인됨 (M2).** 본문 없이 `POST /signUp` → **500 / `C999`**, 계정 생성 0건.
> 다만 이것은 `MissingServletRequestPartException`이 `GlobalExceptionHandler` catch-all에
> 걸린 결과이지 방어가 아니다. **400이어야 할 자리에 500이 나간다** (§9-1 등재).

### 3-6. 기준선에서만 확정되는 것 — 3건 [기]

| # | 항목 | 기준선이 확정한다 |
|---|---|---|
| **B1** | **캡처 그룹 S2(원소 5·8·9·10 경로)의 before가 401인가** | ★ **이번 작업의 최우선 검증 항목.** 401이면 §3-1 전제가 실증된다. **하나라도 200이면 가설 반증 → 즉시 중단, 명세 재작성** |
| **B2** | 원소 2·3 경로(캡처 `13`·`14`)의 before 상태 코드 (404? 500? 정적 핸들러?) | 숫자를 미리 적지 않는다. 실측값을 보고서에 기록 |
| **B3** | 원소 11(`/`, 캡처 `21`)의 현재 동작 | 유지 대상이지만 동결 항목으로 기록만 한다 |

#### v2에서 미리 해소된 것 — 파괴적 실측 2건 (M2)

v1.1은 `12`·`20`의 before를 "비워 둔 값"으로 두었으나, **2026-08-14 실행 세션에서 실측했다.**
v1.1 §14 🔴-2가 물었던 "본문 없이 보내면 성립하지 않는다"는 추정은 **참으로 확인**됐다 — 단,
**성립하지 않는 방식이 서로 다르다.**

| 캡처 | 요청 | before | 본문 | 부작용 |
|---|---|---|---|---|
| `12` | `POST /announces` 무토큰·본문 없음 | **415** | `{"status":415,"code":"C007","message":"지원하지 않는 미디어 타입입니다."}` (87B) | **없음.** 공지 총건수 불변 |
| `20` | `POST /signUp` 무토큰·본문 없음 | **500** | `{"status":500,"code":"C999","message":"서버 내부 오류가 발생했습니다."}` (84B) | **없음.** 사원 목록 불변 |

- `12`는 `consumes = multipart/form-data` 제약이 있어 **DispatcherServlet이 끊는다.** 핸들러 미도달
- `20`은 `consumes` 제약이 없어 **핸들러 매핑까지 도달**하고, `@RequestPart` 2개가 필수라
  `MissingServletRequestPartException` → `GlobalExceptionHandler` catch-all → 500. `Connection: close`
- 두 응답 모두 스택트레이스 없음(표준 `ErrorResponse`), `Access-Control-Allow-Origin: *` 포함

⇒ **`20`은 처방 후에도 500 동결**이다(`/signUp` 원소 유지). `12`만 401로 전환된다.

---

## 4. 처방

### 공통 원칙

- **원소만 지운다.** 판정문·`catch` 블록·`jsonResponseWrapper`·import를 건드리지 않는다
- **잔존 4개의 문자열·순서를 바꾸지 않는다**
- 왜 4개만 남았는지 **주석으로 남긴다.** 없으면 다음 세션이 "정리 누락"으로 오해한다 (작업 F 보고서 §4-1 선례)

### [G1] `roleLessList` — 원소 8개 제거

**위치:** `JwtAuthorizationFilter.java:57`

**Before (원문 — 한 글자도 고쳐 읽지 말 것)**

```java
List<String> roleLessList = Arrays.asList("/signUp","/registDepart","/registPosition", "/login","/members/{memberId}","/showAllMembersPage", "/announces", "/announces/{ancNo}, /approvals", "/resetMemberPassword/{memberId}", "/members/updateProfile/{memberId}", "/", "/wss/chatting");
```

**After**

```java
/* 인증 없이 통과시킬 경로. 판정이 String.equals 라 이 목록에 없는 URI 는 전부 토큰이 필요하다.
 * 4개만 남은 이유 (2026-08-14 전수 판정 — docs/security/tasks/05-authn-boundary.md §3-2):
 *   /signUp        프론트가 이 호출에만 토큰을 붙이지 않는다 (MemberAPICalls.js). 닫으면 구성원 등록이 정지한다
 *   /login         인증 시작점
 *   /              루트 매핑이 없어 정적 리소스 핸들러와의 상호작용이 불확정. 닫는 실익이 없다
 *   /wss/chatting  브라우저 WebSocket 생성자는 커스텀 헤더를 지정할 수 없다. 프론트 수정으로도 닫히지 않는다
 * 판정 방식을 String.equals 외의 것으로 바꾸면 지워 둔 원소가 되살아나 무인증으로 열린다.
 * 되살리거나 바꾸기 전에 §3-2 를 먼저 읽을 것. */
List<String> roleLessList = Arrays.asList("/signUp", "/login", "/", "/wss/chatting");
```

**주석은 8줄이다** (v1.1의 12줄에서 4줄 축소 — 근거는 §7 **D11**).
잘라낸 것은 분량이 아니라 **"처방 후 코드를 설명하지 않는 줄"**이다.

- 제거 대상 8개: `/registDepart` · `/registPosition` · `/members/{memberId}` · `/showAllMembersPage` ·
  `/announces` · `"/announces/{ancNo}, /approvals"` · `/resetMemberPassword/{memberId}` ·
  `/members/updateProfile/{memberId}`
- `Arrays.asList` · `List<String>` · 변수명 **무변경** → 신규 import 0건
- 판정문 `:60`은 **한 글자도 바꾸지 않는다**

---

## 5. Scope — 수정 허용 파일

```
final/src/main/java/com/insider/login/auth/filter/JwtAuthorizationFilter.java
```

**이 1파일 외 코드 변경은 전부 범위 이탈이다.** 설정 파일(`application.yml`), 프론트엔드,
다른 필터·핸들러·컨트롤러 포함.

문서는 별도:
```
docs/security/tasks/05-authn-boundary.md            (본 문서)
docs/security/reports/05-authn-boundary-report.md
docs/security/spec.md                                (§4-6 신설 + §4-3 갱신·등재)
docs/security/precedents.md                          ★ 선례 정본 (v2에서 신설)
docs/security/tools/capture-authn-boundary.ps1       (캡처 도구 재사용본)
AGENTS.md                                            (로드맵 · 선례 절 → precedents.md 포인터)
```

---

## 6. ErrorCode — 사용 0건

작업 F와 동일하게 **신규 0건이 아니라 사용 자체가 0건**이다.

- 401 응답은 작업 F가 이미 넣은 `JwtAuthorizationFilter`의 `catch` 블록이 만든다.
  이번 작업은 **그 블록에 도달하는 경로를 늘릴 뿐** 응답 생성 코드를 건드리지 않는다
- `M002`·`M003`·`M004`는 여전히 죽은 상수다. **되살리지 않는다** (작업 F D3 유지)
- 필터는 `GlobalExceptionHandler`에 위임할 수 없다 (작업 F §3-3 대조 실증분)

---

## 7. 결정 사항

### D1. 처방 방식 — **원소 선별 제거. 패턴 매처 도입 금지**

| 안 | 판단 |
|---|---|
| 패턴 매처(`AntPathMatcher`) 도입 | 🚫 **금지.** 죽은 원소가 되살아나 `/members/{id}`·`updateProfile`이 무인증 개방된다 |
| **원소 선별 제거** | ✅ **채택.** diff 1줄, 되돌리기 쉽고, 판정이 두 숫자로 끝난다 |
| 컨트롤러에서 토큰 유무 검사 | ✗ 필터가 이미 통과시킨 뒤라 `SecurityContext`가 비어 있다. 인증 경계가 둘로 갈린다 |

### D2. `/signUp` — **범위 밖. 작업 06으로 분리**

프론트 리포 수정 + ADMIN 인가 도입 + 파괴적 실측(계정 생성) 세 가지가 붙는다.
작업 A와 B가 갈라진 이유와 같다. **`AGENTS.md` 로드맵에 "작업 06 확정"으로 등재**하고,
보고서 §7에 "이번 작업이 닫지 않았다"를 명시한다.

### D3. 인가가 아니라 **인증만 추가한다**

회원·공지 조회에는 결재 같은 "관계"가 없다. 사내 그룹웨어이므로 열람 주체는 **인증된 사원 전원**이
자연스럽고, 그렇게 자르면 처방이 원소 제거로 수렴한다. 선례 **S2(관계 판정)·S4(응답 불변)를 적용할
축이 애초에 없다** (`docs/security/precedents.md`). 필드 단위 축소(주소·연락처)와
`downloadMemberInfo`의 ADMIN 인가는 §9 등재.

### D4. 판정문 `:60` — **불변**

`contains(getRequestURI())`를 그대로 둔다. 바꾸는 순간 D1이 무너진다.

### D5. `/` 원소 — **유지**

루트 매핑이 없고 `addResourceHandler("**")`와의 상호작용을 기동 없이 확정할 수 없다(§3-6 B3).
닫는 실익이 0이고 회귀 리스크만 있다. **동결 항목으로 캡처에 기록만 한다.**

### D6. `/wss/chatting` — **유지. 구조적으로 닫을 수 없다**

브라우저 `WebSocket` 생성자는 커스텀 헤더를 지정할 수 없다. `Room.js:19`가 만드는
`'Authorization:' + 'BEARER' + …` 문자열은 헤더가 아니라 **연결 후 메시지 본문**이다.
핸드셰이크(`GET /wss/chatting` Upgrade)는 필터를 통과하므로 원소를 지우면 **채팅이 죽는다.**

> 닫으려면 서브프로토콜(`Sec-WebSocket-Protocol`)·쿼리스트링 토큰·`HandshakeInterceptor` 중
> 하나를 도입해야 한다. **별도 작업이며 프론트 수정이 필수다.** §9-1 등재.

#### 선례로 승격하지 않는다 (v2 확정 — v1.1 §14-7 해소)

v1.1은 이 건에 **새 선례 번호를 부여**하는 안을 열어 두었다. **승격하지 않는다 —
번호도 미리 예약하지 않는다** (`precedents.md` §3). 근거 셋.

| # | 근거 |
|---|---|
| 1 | **형태가 선례가 아니다.** `precedents.md`의 S1~S9는 전부 *"이런 상황이면 이렇게 하라"*는 **처방 판단 기준**이다. 이 건은 *"이 방법으로는 못 닫는다"*는 **사실**이다 (`precedents.md` §3 번호 부여 규칙) |
| 2 | **실증이 없다.** 이 작업은 `/wss/chatting`에 **캡처 항목을 두지 않는다**(§10 말미). 확보하는 증거는 "원소를 유지했더니 채팅이 계속 됐다"는 음성 증거뿐이고, **지웠을 때 실제로 죽는지는 확인하지 않는다.** S1~S9는 전부 캡처·실측으로 뒷받침돼 있다 — 승격하면 그 기준선이 내려간다 |
| 3 | **n=1이다.** "브라우저에서 헤더를 붙일 수 없는 프로토콜"은 EventSource·`<img>`·form submit·`window.open`으로 넓어질 여지가 있으나 이 작업은 그중 아무것도 조사하지 않았다 |

⇒ **`spec.md` §4-3 등재 + 본 D6 근거 유지.** 선례 승격은 *실제로 WebSocket 인증을 도입하는
작업*이 자기 결론으로 올린다. 그때는 n=1이 아니라 처방이 함께 붙는다.

> 그럼에도 승격한다면 문구를 사실이 아니라 **조건부 규칙**으로 바꿔야 형태가 맞는다 —
> "화이트리스트 제거만으로 닫히는지는 프로토콜이 헤더를 실을 수 있는지에 달렸다.
> 확인 없이 원소를 지우지 말 것."

### D7. 정적 리소스 화이트리스트 — **범위 밖. 등재도 하지 않는다**

`WebSecurityConfig:39~41`의 `PathRequest.toStaticResources().atCommonLocations()`는
`/css/**`·`/js/**`·`/images/**`·`/webjars/**`·`/favicon.ico` 5패턴 한정의 **Spring 표준 설정**이다.
결함이 아니므로 §4-3에 넣지 않는다. 다만 **"경로 화이트리스트가 여기에도 있다"는 사실은 본 명세에 남긴다.**

### D8. `ErrorCode` — **사용하지 않는다**

§6 참조. 작업 F D3 유지.

### D9. 문서 배치 — `docs/security/tasks/05-authn-boundary.md`

2026-08-12 평평화 규칙. 도메인 하위 폴더를 만들지 않는다.

### D10. 기준선 재사용 — **불가. 새로 찍는다**

작업 F 기준선은 무토큰 `/showAllMembersPage`·`/announces`를 **정상 경로(불변)** 로 고정해 두었다
(캡처 `04`·`05`). 이번 작업은 바로 그 둘을 **전환**시키므로 판정 그룹이 정면으로 어긋난다.
`C:\temp\authn-boundary\`에 신규 22항목을 캡처한다. **토큰도 재발급한다.**

### D11. [G1] 주석 — **8줄. 잘라낸 기준은 분량이 아니다** (v1.1 §14-5 해소)

v1.1은 12줄이었다. 작업 F가 코드 4줄에 주석 5줄이었던 것과 비교하면 비율이 극단이지만,
**비율은 잘못된 척도다.** 주석의 필요량은 코드 라인 수가 아니라 **후속자가 오판할 여지의 크기**에
비례하고, 여기는 그 여지가 유난히 크다 — `Arrays.asList("/signUp", "/login", "/", "/wss/chatting")`는
**그 자체로 완결돼 보이고**, 잘려나간 8개의 흔적이 코드에 0으로 남는다. 게다가 남은 4개가
**서로 다른 이유**로 남았다. 그래서 **원소당 사유 4줄은 압축 불가능하다** — 그것이 이 작업의 결론이다.

잘라낸 기준: **처방 후 코드를 설명하지 않는 줄.**

| 잘라낸 것 | 근거 |
|---|---|
| "자리표시자나 쿼리스트링이 든 문자열은 어떤 URI 와도 매칭되지 않는다" | **처방 후 리스트에 그런 문자열이 0개다.** 없는 것을 설명한다. 이것은 §3-1의 내용이지 코드 주석의 내용이 아니다 |
| `AntPathMatcher` 경고의 **대상 경로명** (`/members/{memberId}` 등) | 그 문자열이 코드에서 사라져 다음 세션이 코드만 보고 검증할 수 없다. ⚠ **경로명만 뺐고 "무인증으로 열린다"는 결과 문구는 남겼다** — 이 주석의 목적은 검증이 아니라 **저지**다. §3-2를 열지 않고 "정리"하려 드는 세션은 결과를 모르면 멈출 이유가 없다 |
| 라인 번호 3개 (`MemberAPICalls.js:75`·`WebConfig:26`·`Room.js:19`) | 프론트는 **별도 리포**라 이 리포의 어떤 검증도 드리프트를 잡지 못한다. **작업 F 주석의 라인 참조 0건**이 선례다. 파일명까지만 남긴다 |
| "→ 작업 06" | **06이 끝나면 주석이 거짓이 된다.** 작업 번호 대신 조건("프론트가 토큰을 붙이지 않는다")을 적으면 조건이 사라질 때 원소도 사라진다 |

### D12. §1 목표에서 "작업 06의 성립 조건" 제외 (v1.1 §14-8 해소)

v1.1 §1의 세 번째 문단("작업 06의 성립 조건을 부분적으로 무너뜨린다")을 **목표에서 뺐다.**
근거는 §3-5에 남기고, 확인은 06이 한다.

| # | 근거 |
|---|---|
| 1 | **05의 성공 기준 9개·캡처 22항목 어디에도 이것을 판정하는 항목이 없다.** 목표에 두면 보고서에 "달성"이라 쓸 자리가 생기는데 증거가 없다 |
| 2 | 근거가 **정적 추론**이다. 05는 `POST /signUp`으로 계정을 만들어 보지 않는다 — 캡처 `20`은 본문 없이 보내 *성립하지 않음*을 볼 뿐이다 (M2) |
| 3 | §3-5의 미확정 2건 중 **`cascade=PERSIST` 건이 참이면 유효한 `departNo`를 알 필요 자체가 없어져 효과가 성립하지 않는다** |
| 4 | ★ **목표에 두면 06이 그것을 전제로 받아들여 검증을 건너뛸 위험이 있다.** 06은 어차피 파괴적 실측을 하므로 그 자리에서 직접 확인하는 편이 정확하다 |

> 부수 효과 하나 더 — 목표에 두면 "05를 했으니 06은 덜 급하다"로 읽힐 여지가 있다.
> §3-5 표는 임의 `role`(ADMIN) 계정 생성 조건이 **대부분 성립**한다고 적고 있다. 완화 서술은 위험하다.

### D13. 검증 데이터·계정 정책 (v1.1 §14-1·3 해소)

| 값 | 확정 | 근거 |
|---|---|---|
| `{A사번}` · `{D1}` | **작업 F 값 승계** | 같은 계정·같은 문서를 쓰면 S0 비회귀가 작업 F 기준선과도 대조 가능해진다 |
| `{ancNo}` | **26** | 2026-08-14 신규 생성 (M5). 기존 공지는 작성자·파일 경로가 제각각이라 고정 대상으로 부적합 |
| `16`·`17`의 대상 계정 | **A 계정 그대로 쓴다** | 둘 다 **테스트 계정**이라 프로필이 덮이거나 비밀번호가 `0000`으로 초기화돼도 무해하다. 검증용 계정을 새로 만들지 않는다 |
| `12`·`20` 파괴적 실측 | **v2에서 이미 수행. 재실행 불필요** | §3-6 — 레코드 생성 0건 확인 |

⚠ **`{ancNo}` = 26 의 데이터 위생**: 최초 등록 시 멀티파트 인코딩 문제(M3)로 한글이 깨져 저장됐다.
제목·내용은 `PUT`으로 정정했으나 **`ancWriter`는 깨진 채 남아 있다**(M4 — `PUT`이 제목·내용만 덮는다).
캡처는 바이트 대조라 **깨진 값이어도 판정에 지장이 없다.** 다만 보고서에 인용할 때 오탈자로 읽지 말 것.

---

## 8. 위험 목록

| # | 위험 | 대응 |
|---|---|---|
| **R1** | ★ §3-1 전제가 틀려 S2 그룹의 before가 200이다 (자리표시자 원소가 실제로 매칭되고 있었다) | **기준선이 곧 검증이다**(§3-6 B1). 200이 하나라도 나오면 **즉시 중단**하고 명세를 다시 쓴다. 코드는 손대지 않은 상태이므로 손실 0 |
| **R2** | S1 그룹의 **응답 본문이 통째로 바뀐다** (정상 응답 → 인증 실패 JSON) | **정상이다. 회귀가 아니다.** 작업 F의 S6(본문 해시 동일)은 이번 작업에 적용되지 않는다 — 대신 **"after 본문이 REF(캡처 19)와 동일한가"** 로 판정한다 (§10) |
| **R3** | `/registDepart`·`/registPosition`의 before 상태가 예상과 다르다 | 숫자를 미리 적지 않았다(§3-6 B2). **after가 401이면 PASS**, before는 기록만 |
| **R4** | 공지 등록 화면이 깨진다 | `AncAPICalls.js:32~38`이 `:36`에서 토큰을 붙인다(§3-3). 그래도 **화면에서 직접 공지를 등록해 본다**(성공 기준 9) |
| **R5** | 구성원 목록 화면이 깨진다 | `MemberAPICalls.js:108` → `:7~11`. 화면 검증 필수 |
| **R6** | 원소를 지우다 잔존 4개의 문자열이 훼손된다 | 특히 `/login` 훼손 시 **로그인 전체가 실패**한다. 검색 확인 3번으로 문자열 대조 |
| **R7** | R11 — 재기동으로 정상 응답의 키 순서가 흔들린다 | 작업 F에서 **실제로 발동**했다(`02` 목록, Jackson 파생 속성). 정규화 재판정을 전 그룹에 유지 |
| **R8** | 토큰 만료 (작업 F 토큰 2026-08-15 06:35 KST) | **기준선 캡처 전에 재발급.** 응답 본문에 토큰이 실리는 항목은 로그인 성공(`09`)뿐이고 shape 판정이므로 재발급이 기준선을 오염시키지 않는다 |

---

## 9. 문서 갱신

### 9-1. `spec.md`

1. **§4-6 신설** — 작업 05. §4-4·§4-5와 같은 형식
2. §4 작업 분할 표에 **행 2개** 추가
   - 작업 **05** 행 (완료)
   - 작업 **06** 행 — 🔴 `POST /signUp` 무인증 계정 생성. **아래 표가 아니라 여기에 넣는다** (⚠ 참조)
3. **§4-3 갱신**
   - `GET /showAllMembersPage`가 무인증 행 — **닫힘. §4-6으로 이동**
   - 신규 등재 **9건** (아래)

> ⚠ **`POST /signUp`은 §4-3에 넣지 않는다.** §4-3은 "작업 D — 등재만" = **닫을 계획 없음**인데,
> 이 항목은 §9-2에서 **작업 06으로 확정 승격**한다. 같은 항목이 "등재만" 표와 로드맵에 동시에
> 있으면 다음 세션이 어느 쪽을 진실로 읽을지 갈린다.
> **선례가 있다** — §4-3의 `GET /showAllMembersPage` 행이 "인증 추가는 작업 E의 정책 결정에
> 속한다"고 적혔다가, E가 `approval/**`만 다루는 바람에 나중에 ⚠ 정정 각주가 붙었다
> (`spec.md:207~209`). 같은 사고를 반복하지 않는다.

#### 등재 9건

**정적 대조 6건** — 실측분과 구분할 것:

| 항목 | 지점 | 근거 |
|---|---|---|
| **공지 `PUT`/`DELETE` 인가 0건** | `AnnounceController:145` · `:154`. `announce` 패키지 인가 검증 전수 0건 | 임의 인증 사용자가 타인 공지 수정·삭제 가능 |
| **`/wss/chatting` 인증 도입 불가 (구조적)** | `WebSocketConfig:24` · `Room.js:50` | 서브프로토콜·쿼리 토큰·`HandshakeInterceptor` 중 택1 + 프론트 수정 필요. **선례로 승격하지 않는다** (§7 D6) |
| **`WebConfig:26` `addResourceHandler("**")`** | 매핑 실패 요청을 정적 핸들러가 삼켜 `throw-exception-if-no-handler-found`를 무력화할 수 있다 | 원소 11 판정 불확정의 원인. ⚠ **문구는 기준선 캡처 후 확정한다** — 캡처 `13`·`14`·`21`이 이 동작을 실측하므로 **정적 대조가 아니라 실측 등급으로 올릴 수 있다** |
| **`PUT /members/updateProfile/{memberId}` 인가 0건** | `MemberController:275~326` | 인증된 아무 사원이나 남의 부서·직급·입사일 덮어쓰기 + `TransferredHistory` 변경 |
| **`GET /downloadMemberInfo` 인가 0건** | `MemberController:420~479` | 전 사원 사번·이름·이메일·주소·전화번호 엑셀 |
| **`ProposalApi.js:7·20·33` 토큰 플레이스홀더** | `'BEARER YOUR_TOKEN_HERE'` 하드코딩 | 프론트. **보안 결함이 아니라 작업 F가 드러낸 회귀다** — F 이전엔 200으로 조용히 실패했고 이후엔 **401로 실패**한다 |

**실측 3건** (2026-08-14 실행 세션 — v2 신규):

| 항목 | 지점 | 실측 |
|---|---|---|
| **멀티파트 파트 본문이 UTF-8로 디코딩되지 않는다** (M3) | `POST /announces`의 `@RequestPart("announceDTO") String`. 파트에 `Content-Type` charset이 없으면 ISO-8859-1로 읽힌다 | `ancNo=26` 등록 시 제목·내용·작성자가 전부 `ê²ì¦ì©…`로 저장됐다. **프론트 `AncAPICalls.js:32~38`도 같은 경로**라 화면 등록에서도 재현될 수 있다 → §10 화면 검증 2번이 판명한다 |
| **`POST /signUp`이 필수 파트 누락에 500을 반환한다** (M2) | `MemberController:83`. `consumes` 제약이 없어 핸들러 매핑까지 도달 → `MissingServletRequestPartException` → `GlobalExceptionHandler` catch-all → **`C999` 500** | **400이어야 할 자리다.** `POST /announces`는 같은 조건에서 `consumes` 제약 덕에 **415/`C007`**로 끊긴다 — 같은 결함 계열에서 응답이 갈린다. **작업 06에서 함께 본다** |
| **`PUT /announces/{ancNo}`가 제목·내용만 덮어쓴다** (M4) | `AnnounceService.updateAnc:127~149`. `ancWriter`·`ancDate`·`filePath`·`hits`는 기존 값이 그대로 재저장된다 | 수정 API로 작성자 오기를 정정할 수 없다. 기능 결함이며 보안 결함은 아니다 |

> **📌 왜 프론트·구조 항목도 §4-3에 넣는가** (v1.1 §14-6 해소)
> §4-3은 "결함 유형별 표"가 아니라 **닫지 않은 것의 대장(臺帳)**이고,
> **"프론트라서" "구조라서" 빼는 선례가 없다.** 이미 프론트 항목(저장형 XSS —
> `ApprovalDetail.js:198`)과 구조 항목 3건(`BeanConfig:29` `setAmbiguityIgnored`,
> `CommuteController` 응답 맵 키 구조, 프로필 이미지 상대 경로)이 등재돼 있다.
> 성격 구분은 이미 **다른 축**으로 하고 있다 — `spec.md` §4-3 하단 인용문이
> **"실측된 것 / 코드 대조 수준의 등재"**를 두 번 못 박는다. 위 표의 2분할이 그것이다.

### 9-2. `AGENTS.md`

> ⚠ **선례 절만 먼저 손댔다 (2026-08-14).** 정본(`precedents.md`) 신설에 딸린 일이라
> 착수 전에 끝냈다. **로드맵·"현재 진행 작업" 갱신은 05 완료 후 커밋 시점에 한다** —
> 미완 작업을 완료로 적어 두면 다음 세션이 오독한다.

- 로드맵에 `G. 인증 경계 정상화 ✅` 추가 (커밋 해시 기입) — **05 완료 후**
- **후속 후보 → `06. POST /signUp 무인증 차단`을 확정 항목으로 승격** — **05 완료 후**
- "현재 진행 작업" 갱신 — **05 완료 후**
- **선례 절 정비 ✅ 완료** (v1.1의 "WebSocket 선례 추가"를 대체 — §7 D6):
  1. 기존 불릿에 **`precedents.md` 정본 번호를 부여**한다 — 작업 E 불릿 → `S1`~`S4`,
     작업 F 불릿 → `S6`~`S9`. **순차 부여가 아니다.** 정본에는 `S5`(차단·비차단을 `log.warn`으로
     구분 기록)가 있는데 `AGENTS.md`에 누락돼 있어, 그대로 순번을 매기면 **F 항목이 한 칸씩 밀린다**
  2. **누락된 `S5` 불릿을 「작업 E에서 확정된 것」에 보충**한다
  3. 작업 F의 다섯째 불릿("기준선은 코드 수정 전에 찍는다")은 **`S9`가 아니라 `P1`이다** —
     선례가 아니라 검증 방법론이다. **「검증 방법론 (P1~P3 · R11)」 소절로 분리**한다
  4. 선례 절 머리에 **`docs/security/precedents.md`가 정본임을 명시**한다
- **새 선례 번호는 추가하지 않는다** (§7 D6 — WebSocket 건은 `spec.md` §4-3 등재로 끝낸다)

### 9-3. 보고서

`docs/security/reports/05-authn-boundary-report.md` — 작업 F 보고서 구조를 따른다.
§1 변경 파일 / §2 구현 내용 / §3 검증 결과 / §4 명세와 실물의 차이 / §5 캡처 22항목 판정 /
§6 남은 일 / §7 범위 밖·등재

### 9-4. 캡처 도구

`capture-auth-status.ps1`의 `Get-Matrix`와 판정 그룹 배열만 교체한 재사용본을
`docs/security/tools/capture-authn-boundary.ps1`로 편입한다.
**`tokens.ps1`·캡처 산출물은 리포에 넣지 않는다** — 유효 토큰과 사원 PII.

### 9-5. `docs/security/precedents.md` ★ 신설 (v2에서 이미 생성)

`readF_to_next_handover.md` §4(선례 `S1`~`S9` · 방법론 `P1`~`P3` · `R11`)를 그대로 옮긴
**리포 안의 정본**이다.

**왜 필요했나 — 이것이 v1.1 §14-7 오판의 근원이다.** 번호는 처음부터 존재했지만
**인계 문서가 리포에 커밋되지 않았고** `AGENTS.md` 선례 절은 번호 없는 불릿이었다.
그래서 리포만 뒤진 세션이 `S2`·`S4`가 가리키는 대상을 찾지 못하고
"번호 체계가 존재하지 않는다"고 오판했다. `tasks/04:253`의 `선례 S4`까지 두 문서째
유령 참조로 남아 있었다. **정본이 리포 밖에 있으면 이 사고는 반복된다.**

- 번호를 새로 매기지 않는다. **인계 문서 §4의 번호가 그대로 정본이다**
- §3에 번호 부여 규칙을 두었다 — `S`는 처방 판단 기준일 때만, 단순 사실·제약은 `spec.md` §4-3 등재.
  **명세 단계에서 다음 번호를 미리 예약하지 않는다** — 실증되지 않은 번호가 참조되기 시작한다

---

## 10. 검증

### 자동 검증 (Claude Code)

```powershell
cd final
.\gradlew.bat compileJava
.\gradlew.bat compileTestJava
.\gradlew.bat bootRun          # 80% EXECUTING 에서 멈춘 듯 보이는 것이 정상
```

⚠ **`compileJava`만으로는 부족하다.** 단계 1.5의 JPQL 리터럴 무성 실패 선례.
이번 처방은 **문자열 배열 변경**이라 컴파일러가 오타를 전혀 잡지 못한다. 수동 API 확인이 필수다.

### 검색 확인 (PowerShell 5.1 · 보고서에 결과 기록)

```powershell
cd final\src\main\java\com\insider\login

# 1) roleLessList 원소가 정확히 4개인가 — 눈으로 문자열 대조
Select-String -Path .\auth\filter\JwtAuthorizationFilter.java -Pattern "roleLessList" -Context 3,3

# 2) 제거한 8개 문자열이 이 파일에 남아 있지 않은가 (0건이어야 한다)
Select-String -Path .\auth\filter\JwtAuthorizationFilter.java `
  -Pattern "registDepart|registPosition|showAllMembersPage|announces|resetMemberPassword|updateProfile|members/\{"

# 3) 판정문 무변경 — contains(request.getRequestURI()) 그대로인가
Select-String -Path .\auth\filter\JwtAuthorizationFilter.java -Pattern "contains|AntPathMatcher|startsWith|matches"

# 4) 본문 생성 코드 무변경
Select-String -Path .\auth\filter\JwtAuthorizationFilter.java -Pattern "jsonResponseWrapper|jsonMap.put|isCommitted|SC_UNAUTHORIZED"

# 5) ErrorCode 사용 0건
Select-String -Path .\auth\filter\JwtAuthorizationFilter.java -Pattern "ErrorCode|ErrorResponse|BusinessException"

# 6) 범위 이탈 — 변경 파일은 정확히 1개여야 한다
cd ..\..\..\..\..\..
git diff --stat
```

**2번의 기대값이 0건이다.** 히트가 있으면 원소가 덜 지워졌거나 다른 곳을 건드린 것이다.
**3번은 히트 내용이 착수 전과 동일해야 한다** (작업 F 보고서 §4-2 선례 — 히트 수가 아니라 내용으로 판정).

### 수동 검증 — 캡처 22항목 (사용자 담당)

```powershell
cd C:\temp\authn-boundary
.\capture-authn-boundary.ps1 -Phase baseline *>&1 | Tee-Object -FilePath .\baseline-console.txt
# ↑ 코드 수정 전. 이것이 끝나기 전에는 착수하지 않는다
.\capture-authn-boundary.ps1 -Phase after    *>&1 | Tee-Object -FilePath .\after-console.txt
.\capture-authn-boundary.ps1 -Compare        *>&1 | Tee-Object -FilePath .\verdict-console.txt
```

#### 매트릭스

| 그룹 | id | 요청 | 토큰 | 기대 |
|---|---|---|---|---|
| **S0 정상 (먼저 본다)** | 01 | `GET /showAllMembersPage` | A | 불변 |
| | 02 | `GET /announces?page=0&size=10&sort=ancNo&direction=DESC` | A | **shape 불변** ★M1 |
| | 03 | `GET /announces/26` | A | **shape 불변** ★M1 |
| | 04 | `GET /members/{A사번}` | A | 불변 |
| | 05 | `GET /approvals?fg=given&page=0&title=&direction=DESC` | A | 불변 |
| | 06 | `GET /approvals/{D1}` | A | 불변 |
| | 07 | `GET /departments` | A | 불변 |
| | 08 | `GET /showAllPosition` | A | 불변 |
| | 09 | `POST /login` 성공 | — | shape 불변 |
| **S1 전환 (본체)** | 10 | `GET /showAllMembersPage` | **무토큰** | **→ 401 + REF** |
| | 11 | `GET /announces?page=0&…` | **무토큰** | **→ 401 + REF** |
| | 12 | `POST /announces` (본문 없음) | **무토큰** | before **415** → **401 + REF** |
| | 13 | `GET /registDepart` | **무토큰** | **→ 401 + REF** |
| | 14 | `GET /registPosition` | **무토큰** | **→ 401 + REF** |
| **S2 불변 ★가설 검증** | 15 | `GET /members/{A사번}` | **무토큰** | before=after=**401** |
| | 16 | `PUT /members/updateProfile/{A사번}` (본문 없음) | **무토큰** | before=after=**401** |
| | 17 | `PUT /resetPassword/{A사번}` | **무토큰** | before=after=**401** |
| | 18 | `GET /approvals?fg=given&…` | **무토큰** | before=after=**401** |
| | **19** | `GET /approvals/{D1}` | **무토큰** | before=after=**401** ← **REF** |
| **S3 동결** | 20 | `POST /signUp` (본문 없음) | **무토큰** | **500 동결** (before 실측 — M2) |
| | 21 | `GET /` | **무토큰** | 상태·본문 불변 |
| | 22 | `POST /login` 틀린 비밀번호 | — | **401 유지** (작업 F 결과 동결) |

> **REF = 캡처 `19`의 본문.** 작업 F 캡처 `08`과 같은 성격(무토큰 필터 차단 본문, 90B).
> S1의 after 본문이 전부 이것과 같아야 한다 — 필터의 **같은 `catch` 블록**을 통과했다는 뜻이다.

★ **M1 — `02`·`03`은 해시로 판정할 수 없다. 반드시 shape로 둔다.**

`GET /announces/{ancNo}`는 호출마다 `incrementHits`를 돌린다(`AnnounceController:72~74`).
그리고 **`hits`는 상세 응답에도 목록 응답에도 실린다**(`AnnounceDTO.hits` / `Announce.hits`).
캡처는 `02`(목록) → `03`(상세) 순으로 도는데, `03`이 `hits`를 +1 하고 `ancNo=26`은
DESC 정렬 0페이지의 첫 항목이므로 **다음 페이즈의 `02`가 다른 값을 본다.**

```
baseline : 02 가 hits=N 을 본다  →  03 이 N→N+1
after    : 02 가 hits=N+1 을 본다  →  03 이 N+1→N+2
```

⇒ **처방과 무관하게 `02`·`03` 둘 다 해시가 100% 어긋난다.** v1.1의 `$unchanged`에 둘이
들어 있었으므로 그대로 돌렸으면 **무조건 FAIL 2건**이 났다. 실측 확인: `hits` 0 → 1 → 2.

> `hits`는 응답 **값**만 바꾸고 **키 구조**는 바꾸지 않으므로 shape 판정이 성립한다.
> 상태 코드는 여전히 200 동일해야 한다.

⚠ **파괴적 실측 가드**
- `12`(`POST /announces`)·`20`(`POST /signUp`)의 before는 **v2에서 이미 실측했다**
  (§3-6 — 415 / 500, **레코드 생성 0건**). 캡처에서 재실행해도 부작용이 없음이 확인된 상태다.
  그래도 **200이 나오면 즉시 중단**하고 생성된 레코드를 확인·삭제한 뒤 보고한다 (spec §3-4 절차)
- `16`·`17`도 본문 없이 보낸다. before가 401이면 핸들러에 도달하지 않은 것이므로 부작용이 없다.
  **200/400이 나오면 도달했다는 뜻이므로 즉시 중단**한다.
  (대상은 A 계정 = 테스트 계정이라 프로필이 덮이거나 비밀번호가 초기화돼도 무해하다 — §7 D13)

#### 판정 그룹 (도구 개조 포인트)

```powershell
$unchanged  = '01','04','05','06','07','08'             # 해시 동일 (R11 정규화 재판정)
$shapeFrozen= '02','03'                                 # ★ M1 : shape 동일 + 상태 동일 (hits 가 매번 바뀐다)
$loginOkId  = '09'                                      # shape + 성공 메시지
$authnNow   = '10','11','12','13','14'                  # ★ 신규 : after 401 AND after 해시 == REF(19)
$frozen     = '15','16','17','18','19','20','21','22'   # 해시 동일 + 상태 동일
```

⚠ **`02`·`03`을 `$unchanged`에 두지 말 것.** v1.1이 그렇게 적었으나 **무조건 FAIL 2건**이 난다
(위 M1). `$shapeFrozen` 판정식은 `capture-auth-status.ps1`에 이미 있다 — 작업 F가 `16`·`17`
(Spring 기본 에러 본문의 `timestamp`)에 쓴 것과 **같은 이유·같은 코드**다. 새로 만들 필요가 없다.

★ **`authnNow`는 작업 F에 없던 그룹이다.** 작업 F의 `statusOnly`는 "해시 동일 + 상태 전환"이었으나,
이번 작업은 **본문이 통째로 바뀐다**(정상 응답 → 인증 실패 JSON). 그래서 기준선 해시가 아니라
**같은 실행의 REF 해시**와 대조한다. `before` 상태 코드는 **판정에 쓰지 않고 기록만** 한다(§3-6 B2).

**전 항목 PASS가 아니면 완료가 아니다.**

### 화면 검증 (사용자 담당 · 성공 기준 9)

1. **구성원 관리** 화면 — 목록이 정상 표시된다 (`/showAllMembersPage`)
2. **공지** — 목록·상세가 뜨고, **한글 제목으로 공지를 실제로 등록**할 수 있다 (`POST /announces`)
   > ★ **한글일 것.** 등록이 200으로 끝나도 **저장된 제목이 깨져 있으면 M3(멀티파트 UTF-8 미디코딩)이
   > 프론트 경로에서도 재현된 것**이다. 이 작업의 처방과 무관한 별건이지만, 여기서만 판명된다.
   > 깨지면 §9-1 등재 문구를 "백엔드 한정"에서 "프론트 포함"으로 올린다
3. **채팅** — 방 입장·메시지 송수신 정상 (`/wss/chatting` 원소 유지 확인 ★ D6의 실증)
4. **로그인** — 정상 로그인 진입, 틀린 비밀번호 시 작업 F와 동일한 alert
5. 결재 목록·상세·첨부 다운로드 정상 (S0 재확인)

### 의도적으로 비워 둔 값 (리뷰 항목 아님)

| 값 | 언제 채우나 |
|---|---|
| `13`·`14`·`21`의 **before 상태 코드** | 기준선 캡처 후 보고서에 기록 (§3-6 B2·B3). 미리 적으면 그것이 기대값이 되어 오판을 부른다 |
| ~~`20`의 before~~ | **v2에서 해소 — 500** (§3-6 M2) |
| `/wss/chatting` **캡처 항목 없음** | WebSocket 핸드셰이크를 `Invoke-WebRequest`로 잡기 어려워 화면 검증 3번으로 돌렸다. 캡처할 방법이 있으면 v3에서 추가. ⚠ **이 공백이 §7 D6의 승격 보류 근거 2다** |
| `13`을 **GET으로 캡처** | 필터는 HTTP 메서드를 보지 않으므로 GET으로 충분하다고 판단. 실제 사용 형태(POST)에 맞추려면 v3에서 변경 |

---

## 11. 실행 순서

0. **사용자**: 검증용 공지 생성 — **완료 (`ancNo = 26`, 2026-08-14)**
1. **사용자**: 토큰 재발급 → `C:\temp\authn-boundary\` 구성 → **기준선 캡처 22항목**
2. **사용자**: 기준선 판정 — **캡처 그룹 S2(15~19)가 전부 401인가 확인**. 아니면 여기서 중단
3. `git status` 확인 — 워킹 트리 클린
4. plan mode로 계획 제시 → 승인
5. [G1] `JwtAuthorizationFilter:57`
6. `compileJava` → `compileTestJava` → `bootRun`
7. 검색 확인 6종 실행, 결과 기록
8. 보고서 작성 (`docs/security/reports/05-authn-boundary-report.md`, UTF-8)
9. 변경 사항 보고 → **사용자가 after 캡처 + `-Compare`**
10. 화면 검증 5항목
11. 문서 갱신 (§9)
12. 커밋 2분할(코드 / 문서) + 푸시 — **사용자**

### ★ 데이터 동결 구간 — 1단계 시작 ~ 9단계 `-Compare` 종료

**이 구간 안에서 공지·회원·결재 데이터를 만들거나 고치지 않는다.**

캡처 `02`(공지 목록)는 0페이지 DESC라 **공지를 하나 추가하면 목록 내용이 통째로 밀린다.**
shape는 유지되지만(`ancList[]` 길이 10 고정) `01`(회원 목록)·`05`(결재 목록)은 해시 판정이라
한 건만 늘어도 **FAIL로 오판**한다.

| 시점 | 허용되는 데이터 변경 |
|---|---|
| 1단계 **전** | 검증용 공지 작성 (`ancNo=26`) — **완료** |
| 1 ~ 9단계 | **없음.** 캡처 자체가 만드는 `hits` 증가만 발생한다 (M1 — shape 판정으로 흡수) |
| 10단계 **후** | 화면 검증의 공지 등록. ⚠ **after 캡처와 `-Compare`가 끝난 뒤에 한다** |

> 화면 검증 2번(공지 등록)을 `-Compare` **전에** 하면 `02`의 목록이 밀려 판정이 깨진다.
> §10 화면 검증이 11단계가 아니라 **10단계**에 있는 이유가 이것이다 — 순서를 바꾸지 말 것.

---

## 12. 착수 전 체크 (Claude Code)

- [x] **명세 리뷰 완료 (v2).** §14 절은 삭제됐다 — 잔여 미해소는 §0 착수 상태 표에 있고, **착수를 막지 않는다**
- [ ] 본 문서를 끝까지 정독했다
- [ ] **`docs/security/precedents.md`를 읽었다** (선례 `S1`~`S9` · 방법론 `P1`~`P3` · `R11` 정본)
- [ ] `docs/security/tasks/04-auth-failure-status.md` + `reports/04-auth-failure-status-report.md`를 읽었다 (선례)
- [ ] §2 "범위 밖 🚫" 12항목을 확인했다
- [ ] **기준선 캡처가 완료됐고 캡처 그룹 S2(15~19)가 전부 401임을 사용자에게 확인받았다**
- [ ] **`02`·`03`이 `$shapeFrozen`에 들어갔다** (M1 — `$unchanged`에 두면 무조건 FAIL)
- [ ] 수정 파일이 **1개**임을 확인했다
- [ ] 잔존 원소가 **정확히 4개**(`/signUp` · `/login` · `/` · `/wss/chatting`)임을 확인했다
- [ ] 판정문 `:60`을 건드리지 않는다는 것을 확인했다
- [ ] `ErrorCode`를 쓰지 않는다는 것을 확인했다
- [ ] `git status`가 클린하다

### 중단하고 보고할 상황

- **기준선에서 캡처 그룹 S2(15~19) 중 하나라도 200이 나올 때** ★ 가설 반증. 명세를 다시 쓴다
- **기준선에서 `12`·`20`이 200을 반환할 때** ★ 파괴적 실측 성립. 즉시 보고
  (v2 실측은 415 / 500이었다 — 200이 나온다면 그 사이에 무언가 바뀐 것이다)
- **`12`의 before가 415가 아니거나 `20`의 before가 500이 아닐 때** — M2와 어긋난다. 보고
- `JwtAuthorizationFilter` 외 파일을 수정해야 해 보일 때
- `:57`의 실물 문자열이 §4 Before와 다를 때 (원소 추가·순서 변경 등)
- `:60` 판정문이 §3-1과 다를 때
- `compileJava`·`compileTestJava`·`bootRun` 중 하나라도 실패할 때
- 검색 확인 2번이 0건이 아닐 때
- **범위 밖 개선이 눈에 띌 때** — 고치지 말고 보고한다. 등재 후보다

---

## 13. 작업 원칙 리마인더

- **Surgical Changes.** task가 요구하는 것만. "온 김에" 개선 금지 — 특히 **판정문을 "제대로" 고치려는 유혹**
- **task.md가 진실의 원천.** 의도 변경이 필요하면 사용자에게 보고
- 이 프로젝트엔 자동화 테스트가 없다. **새로 만들지 않는다**
- 모든 명령어는 Windows / PowerShell 5.1 기준. 모든 `.md`는 UTF-8
- 단계 완료 = **커밋 + 푸시까지**

---

> v1.1에 있던 "리뷰 대기 항목" 절은 **v2에서 삭제됐다.** 해소분은 §7 D11·D12·D13·D6과 §9로
> **옮겼고**, 잔여 미해소(🔴 4 · 🟢 9~11)는 **§0 착수 상태 표**에 있다.
> 의도적으로 비워 둔 값은 **§10 말미**로 옮겼다. 이동 내역은 문서 상단 `v2 정정` 표를 볼 것.
>
> 그 절을 다시 만드는 기준: **명세 리뷰가 한 세션에 끝나지 않을 때만.**
> 한 세션에 닫히면 상단 `vN 정정` 절만으로 충분하다 (작업 F 선례).

s