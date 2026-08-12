# 작업 B: 비밀 정보 노출 차단 · 비밀번호 경로 인가 (Task 명세 · **확정본 v3**)

> 작성: 2026-07-31 / **v3 — 기준선 8종 확보, 착수 준비 완료**
> (v2: 명세 리뷰 반영 + `commute` 도메인 편입)
> 선행: 보안 작업 A(쓰기 경로 권한 경계 정리) 완료·커밋·푸시
> (`91de70c` 코드 / `8731d37` 문서, origin/main 동기화)
> 근거: `securityA_to_next_handover.md` §3·§5, `spec.md` §4-1·§4-2,
> 2026-07-31 실물 대조 · 명세 리뷰 세션 · P0 기준선 · 파괴적 실측 E-1/E-2
> 실행 도구: **Claude Code** — 본 확정본을 plan mode로 검토 후 실행
>
> ⚠ **이 문서의 모든 라인 번호는 `91de70c`(작업 A 코드 커밋) 기준이다.**
> 인계 문서·`spec.md`의 좌표는 작업 A **이전**이라 일부가 밀려 있다(v2-1). 인용 전 실물을 확인할 것.

---

## v3 정정 (P0-B 기준선 캡처 완료 — 2026-07-31)

| # | 절 | 정정 |
|---|---|---|
| **v3-1** | §3-2 #7·#8 | **`commute` 기준선 확보.** `GET /commutes?target=depart&targetValue=1&date=2024-05-09` → 109,861자 / **pw 83** (부서 1 소속 83명 전원의 해시). `GET /corrections?date=2024-05-09` → 33,394자 / **pw 2** |
| **v3-2** | §3-2 #7~#9 | **인증 요구 여부 확정 — 셋 다 인증 필요.** `roleLessList`에 `/commutes`·`/corrections`가 없다(`"/"` 원소는 완전 일치라 매칭되지 않는다) |
| **v3-3** | §8 R15 | **하향.** 미캡처는 `POST /corrections`(#9) **하나뿐**이다. 이 지점은 처방이 엔티티 애너테이션이라 필드 구성이 코드로 확정돼 있어 before 없이 실행 가능하다 |
| **v3-4** | §10 S1(b) | **07·08 행 추가 — 표가 9지점 중 8지점에 대해 숫자로 완성됐다** |
| **v3-5** | §10 S5 | 토큰 만료 응답 형태 구체화 — **HTTP 200 + 본문 `{"status":401,"message":"Token Expired"}`**. `spec.md` §4-3의 "인증 실패 200" 등재분이며, `Invoke-WebRequest`가 예외를 던지지 않으므로 **캡처·검증 시 반드시 본문을 확인해야 한다** |
| **v3-6** | §11 | **P0-A·P0-B·P0-C 전부 완료.** 코드 수정 전 단계가 끝났다 |

> ⚠ **캡처·검증 시 저장 가드를 쓴다.** 만료 토큰(200 + `status:401`)이나 필수 파라미터 누락(400 + C003)의
> 응답 본문이 기준선에 섞이면 `password` 0건으로 나와 **"노출 없음"으로 오판**한다. §10 S1의 함수를 쓸 것.

---

## v2 정정 (명세 리뷰 세션 · 읽기 전용 대조 결과)

**리뷰 판정: 치명 0건.** 아래는 사실 오류와 Scope 누락의 정정분이다.

| # | 절 | 정정 |
|---|---|---|
| **v2-1** | §3-2 #3 | **`ApprovalController:140·147` → `147·153`.** `:140`은 `deleteApproval`의 닫는 `}`다. **작업 A가 `delete()`에 `memberId` 인자를 추가하며 라인이 밀렸다.** 인계 문서가 작업 A 이전 좌표를 옮겼고 v1이 검증 없이 반영했다 → 문서 전역에 "라인 번호는 `91de70c` 기준" 명시 |
| **v2-2** | §5 | **"MemberController 로그 4곳" → 3곳.** 라인은 `:316`·`:317`·`:477` 셋뿐이다. v1의 집계 오류 |
| **v2-3** | §3-2 #4 | `ChatRoomController:37~40` → **`:37`** (경미) |
| **v2-4** | §3-4 | 코드 인용에서 실물의 `try{...}catch{...}`(`:320~330`)가 생략됐다 → 실물로 교체. **처방(분기 전체 제거)에는 영향 없음** |
| **v2-5** ★★ | §1 · §3-2 · §5 | **`commute/**`에 password 노출 3경로 + 로그 3지점이 있다.** 같은 `member_info` 테이블을 매핑하는 **세 번째 엔티티/DTO 쌍**이다. 편입하지 않으면 성공 기준이 **거짓**이 된다 → **작업 B에 편입.** 코드 12파일 → **16파일**, 응답 6지점 → **9지점** |
| **v2-6** | §10 S1 | **검증 구멍 1건.** `@JsonProperty(WRITE_ONLY)`를 **다른 필드에** 잘못 붙이면 `password`는 사라지지만 정상 필드도 함께 사라지는데, S1이 이를 조건절로만 적어 강제하지 않았다 → **키 개수 before/after 서브체크 신설** |
| **v2-7** | §8 R13 | **위험 하향.** `approval.dto.MemberDTO`의 **12인자 생성자 사용처 0건**, `ShowMemberDTO`의 **15인자 생성자 사용처 0건**(리뷰 3-d 전수) |
| **v2-8** | §10 S7 | **검증 대상 계정 교체.** Z(240501629)는 `image_url`이 1바이트(`0x31`)이고 `gender`·`birthday`가 NULL이라 **"보존됐다"를 증명하지 못한다.** → **계정 123**으로 교체 + before 스냅샷 선행 |

> **리뷰가 의심했으나 안전으로 확인된 것** — `toString()` 출력을 파싱하는 코드 **0건** /
> `password`를 요청 body로 되돌리는 경로는 `MemberController:265` **1곳뿐이며 서버가 DB에서 다시 채운다** /
> `member.dto.MemberDTO` 역직렬화 지점은 `signUp` 외 **없음** /
> `ObjectMapper` 사용 5파일 중 대상 DTO를 직렬화하는 곳은 `ConvertUtil` **뿐** /
> `src/test/**`에 **member·auth 도메인 테스트 0건** /
> `MemberController.downloadMemberInfo`는 엑셀 헤더 8개에 `password`가 **없다** — 안전 /
> §5의 12파일에 **과다 포함 0건** /
> `updateOwnPassword`의 `newPassword2`만 누락 갈래는 `.equals(null)`이 false를 반환해 **예외 없이 400** — 안전

---

## 0. 착수 상태

**미확정 결정 0건 / 미확인 파일 0건 / 명세 리뷰 완료.** 착수 가능.

| 확인 항목 | 상태 | 결과 |
|---|---|---|
| `application.yml`의 비밀 3종 | ✅ | `jwt.key:44` / `datasource.username:9` / `datasource.password:10` |
| `YmlConfig` | ✅ | `prefix = "file"` — `uploadDir`/`fileDir`만. **비밀 분리와 무관** |
| `TokenUtils`의 키 사용 | ✅ | `parseBase64Binary` — **비-Base64 문자를 조용히 버린다** (R4) |
| `HANDLE_ACCESS_DENIED` (C005) | ✅ | **선언만, 사용처 0건.** 403 |
| `INVALID_INPUT_VALUE` (C001) | ✅ | 400. 기존 |
| `AccessDeniedException` 핸들러 | ✅ | **없다.** catch-all → 500 (R8) |
| `@EnableGlobalMethodSecurity` | ✅ | `WebSecurityConfig:34`에 있다. 그러나 D3-1은 코드 검증 |
| authority 형식 | ✅ | raw `"ADMIN"`/`"MEMBER"`. **`ROLE_` 접두사 없음** |
| authority 구현체 | ✅ | **람다**(`DetailsMember:34`). `SimpleGrantedAuthority` 비교 불가 (R9) |
| 두 엔드포인트 인증 필요 여부 | ✅ | **둘 다 필요.** `WebSecurityConfig`에 `authorizeHttpRequests` 없음, 게이트는 `roleLessList` 하나 |
| `ModelMapper` 설정 | ✅ | `BeanConfig:21~31` — 필드 접근 켜짐 + **`setAmbiguityIgnored(true)`** (R6) |
| `ConvertUtil` | ✅ | **Jackson `ObjectMapper`** → 로그인 응답이 [B3]으로 함께 닫힌다 |
| `member.dto.MemberDTO` 역직렬화 | ✅ | `POST /signUp:81~86` **1곳** → `@JsonIgnore` 금지 (R2) |
| **`CommuteMember` 엔티티** | ✅ | `@Getter`만, **세터 없음 + 무인자 생성자 `protected`** → 역직렬화 불가 → `@JsonIgnore` |
| **`CommuteMemberDTO`** | ✅ | `@Getter @Setter @NoArgsConstructor` + **`@ToString`이 password 포함** → 역직렬화 가능 → **`WRITE_ONLY`** |
| `src/test/**` | ✅ | member·auth **0건.** ⚠ **commute 테스트는 있다** (R14) |
| P0 기준선 | ✅ | **8종 캡처 완료** (`00~08`). §3-2 표와 전부 일치. `POST /corrections`만 미캡처 (R15) |
| 계정 123 before 스냅샷 | ✅ | CSV 확보. ⚠ `gender`·`birthday`는 세 계정 모두 NULL이라 S7이 검증하지 못한다 (§11 P0-C 주석) |
| 파괴적 실측 E-1·E-2 | ✅ | **둘 다 200 실증** (§3-5) |
| 검증 계정 | ✅ | **Z**=240501629(ADMIN, 현재 `0000`) / **B**=240501544(MEMBER, 현재 `0000`) / **123**(ADMIN, S7 대상) |
| 전 사원 수 | ✅ | **90명** (기준선 키 개수 역산) |

---

## 1. 목표

**비밀 정보가 저장소·응답·로그 세 경로로 새는 것을 한꺼번에 닫는다.**
그리고 그 비밀을 다루는 **비밀번호 변경 경로 2종에 인가를 넣는다.**

**넷을 한 작업으로 묶는 것이 요점이다.** `spec.md` §4는 도메인을 이유로 B와 C를 나눴으나
**파일 단위로 겹친다** — 둘 다 `MemberController`·`member/**`·`auth/**`·`resources/`를 연다.
`resetPassword`(`:142~158`) 바로 아래 `:316~317`에 비밀번호 로그가 있다.
**`commute/**`를 편입하는 근거도 동일하다**(v2-5) — 주제가 같고, 처방이 같고,
빼면 성공 기준이 거짓이 된다.

### 성공 기준

| 지표 | Before | After |
|---|---|---|
| `application.yml`의 비밀 문자열 | **3건** | **0건** — 전부 환경변수 참조 |
| JWT 서명 키 | 공개 리포 평문 커밋, 유출 간주 | **로테이션 완료.** 옛 키 토큰 무효 |
| 응답의 `password` 키 | **9지점** (해시 7 · null 2) | **9지점 전부 소멸** |
| 응답 JSON 키 개수 | §10 S1 표 | **`password` 개수만큼만 감소** ← v2-6 |
| 로그의 평문 비밀번호 | 활성 **2곳** | **0곳** |
| 로그의 비밀번호 해시 | 로그인 **1회당 4회** | **0회** |
| 로그의 JWT 토큰 전문 | 3곳(+주석 1) | **0곳** |
| `PUT /resetPassword/{id}` | **MEMBER가 ADMIN 계정을 초기화 가능** (E-2 실증) | **ADMIN만** — 그 외 403 / C005 |
| `PUT /updateOwnPassword` | **빈 body로 검증 우회** → `0000` (E-1 실증) | **400 / C001** |
| 신규 ErrorCode | — | **0건** (C005·C001 재사용) |
| 정상 경로(로그인·가입·프로필·비밀번호 변경·결재·출퇴근) | — | **불변** ← 최우선 비회귀 |

---

## 2. 경계 (확정)

애매하면 이 표가 이긴다.

| 항목 | 작업 | 근거 |
|---|---|---|
| `application.yml` 비밀 분리 + 키 로테이션 | **B** | `spec.md` §4-1 |
| `PUT /resetPassword/{memberId}` 인가 | **B** | 〃 |
| `PUT /updateOwnPassword` 초기화 분기 | **B** | 같은 `memberService.resetPassword()`를 부르는 **두 번째 진입점** (D9) |
| 응답 `password` **9지점** | **B** | §3-2 |
| 로그 위생 — 평문·해시·토큰 | **B** | 〃 |
| **`commute/**`의 노출 3경로 + 로그 3지점** | **B** | **v2-5.** 주제·처방 동일, 빼면 성공 기준이 거짓 |
| `logging.level.org.hibernate.type` 정리 | **B** | 무효 설정 (v1-4) |
| — 이하 작업 B 밖 — | | |
| **읽기 경로 인가** (결재 상세·파일 다운로드) | **E** | `spec.md` §4-4. **정책 결정 선행** |
| `GET /members/{id}`·`/api/rooms/members`·`/commutes`의 **인가** | **E** | 읽기 경로. 이번엔 **응답 필드만** |
| `GET /showAllMembersPage`가 **무인증**인 것 | **D** | 등재만 (R11) |
| **`CommuteController`가 원본 맵 키를 지우지 않는 구조** | **후속** | 이번엔 **직렬화만** 막는다. 응답 키는 유지 (D13) |
| `TestController` 엔드포인트 제거 | **D** | 등재 유지. 이번엔 로그만 (D12) |
| `setAmbiguityIgnored(true)`의 전역 영향 | **D** | 등재만 (R6) |
| **CORS 전역 개방** (`Access-Control-Allow-Origin: *` — E 실측에서 확인) | **D** | 등재 보강 |
| 죽은 코드 **메서드 삭제** | **후속** | D11 — 이번엔 로그 라인만 |
| 저장형 XSS / 인증 실패 200 / `insite` 무성 0건 | **D** | 등재만 |
| `[K]` 재시도 / `receivedAll` / `finalApproverDate` / `[E]` | 이월 | 완료 보고서 §4 |

> **판단 규칙**: 작업 B는 **"비밀이 새는가"** 와 **"비밀을 바꾸는 요청을 보낸 사람이 그럴 자격이 있는가"**
> 두 가지만 묻는다. 열람 권한·나머지 인가·응답 키 구조·죽은 코드 제거·프론트는 손대지 않는다.

### 범위 밖 (명시 · 🚫 하드 가드)

- 🚫 **`JwtAuthorizationFilter`** — `roleLessList:57`은 `List.contains()`로 **문자열 완전 일치**를 본다.
  `{memberId}` 자리표시자가 든 원소는 어떤 실제 URI와도 매칭되지 않고, 8번째 원소는 쉼표가 따옴표 안에 있다.
  **결과적으로 `/approvals` · `/members/{memberId}` · `/members/updateProfile/{memberId}` ·
  `/resetMemberPassword/{memberId}` 넷이 인증 필요 상태로 유지되고 있으며, 현재 동작이 옳다.**
  "고치면" 전 회원 정보 조회와 프로필 수정이 동시에 무인증으로 열린다. **읽기만 허용.**
- 🚫 **`WebSecurityConfig`** — 인증 게이트가 위 필터 하나뿐이다. 경로 정책 추가는 범위 밖.
  `setFilterProcessesUrl("/login")`도 손대지 않는다(바꾸면 `MemberController:474`의 죽은 핸들러가 되살아난다 — D11)
- 🚫 **`enums/ApprovalStatus.java`의 `description`과 `from()`의 한글 매칭 분기** — 제거하면 **기안이 전부 실패**한다
- 🚫 **DTO의 Enum 타입 전환** — Jackson 기본 역직렬화는 `name()`만 매칭한다. 봉인
- 🚫 **`@PreAuthorize` 사용** — 거부가 500이 된다 (R8)
- 🚫 **`CommuteMemberDTO`·`ShowMemberDTO`·`approval.dto.MemberDTO`의 생성자 시그니처** —
  `approval.dto.MemberDTO`는 `password`가 **index 3**이라 위치 인자 오배치가 컴파일로 안 잡힌다 (D4)
- **프론트엔드 변경** — 리포가 다르다. `callResetPassAPI` 복구는 이번 작업이 아니다
- `common/error/ErrorCode.java`(**신규 0건**), `GlobalExceptionHandler.java`, `config/BeanConfig.java`
- `common/utils/TokenUtils.java` — `:77`·`:109`는 **마스킹으로 닫힌다.** 파일을 열지 않는다
- `common/utils/ConvertUtil.java` — Jackson이므로 [B3]으로 닫힌다
- `MemberService`·`CommuteService`·`CommuteController`의 **나머지 로직**
- `src/test/**`, DB 스키마, 새 기능, `.gitignore`

---

## 3. 실측 근거

### 3-1. 로그인 **1회**에 해시 4회 · 평문 1회 · 토큰 1회 (2026-07-31 bootRun 로그)

| 순서 | 출처 | 형태 |
|---|---|---|
| 1 | `CustomAuthenticationProvider:35` | **평문** |
| 2 | `MemberService:50` ← `DetailsService.loadUserByUsername` | `Member.toString()` **해시** |
| 3 | `CustomAuthSuccessHandler:27` | `MemberDTO.toString()` **해시** |
| 4 | `TokenUtils:77` | 〃 |
| 5 | `TokenUtils:109` | 〃 |
| 6 | `CustomAuthSuccessHandler:38` | **JWT 토큰 전문** |

**해시 4건 중 3건이 `toString()` 경유다.** → D6(마스킹)의 근거.

**같은 로그가 반증한 것 둘**
- `inputted username:` 줄이 **없다** → `MemberController:474~484` 도달 불가 (v1-1)
- Hibernate SQL에 `where m1_0.member_id=?`만 있고 **바인딩 값이 없다** → `type: trace` 무효 (v1-4)

### 3-2. 응답 `password` 노출 — **9지점** (P0 기준선으로 전수 확정)

| # | 지점 | 값 | 인증 | 기준선 |
|---|---|---|---|---|
| 1 | `CustomAuthSuccessHandler:30` 로그인 응답 `userInfo` | **해시** | — | `00-login` 22키/pw 1 |
| 2 | `MemberController:176~188` `GET /members/{memberId}` | **해시** | 필요 | `01` 19키/pw 1 |
| 3 | `ApprovalController:147`·**`:153`** `/approvals/members/{id}`·`/approvals/members` | **해시** | 필요 | `02` 17키/pw 1 · `03` 1263키/**pw 90** |
| 4 | `ChatRoomController:37` `GET /api/rooms/members` | **해시** | 필요 | `04` 1710키/**pw 90** |
| 5 | `MemberController:358~370` `GET /getTokenInfo` | `null` | 필요 | `05` 19키/pw 1 |
| 6 | `MemberController:372~409` `GET /showAllMembersPage` | `null` | **불필요** (R11) | `06` 1710키/**pw 90** |
| **7** | `CommuteService:154` → `GET /commutes?target=depart` 응답 `data.members[]` | **해시** | 필요 | `07` 4944키/**pw 83** |
| **8** | `CommuteService:515` → `GET /corrections` 응답 `data.member[]` | **해시** | 필요 | `08` 1496키/**pw 2** |
| **9** | `CommuteService:264` → `POST /corrections` 응답 `notice[]` — **JPA 엔티티 직접** | **해시** | 필요 | **미캡처** (R15) |

> **7~9의 구조**: `responseMap.put("member"/"members"/"notice", …)`로 원본 컬렉션을 넣고,
> Controller가 `getName()`만 뽑아 쓰면서 **원본 키를 맵에서 제거하지 않아** 전체가 그대로 나간다.
> **9번은 DTO가 아니라 `CommuteMember` 엔티티**다 — Lombok `@Getter`의 `getPassword()`가 직렬화된다.

> **7~9의 인증 요구 여부**: `roleLessList`에 `/commutes`·`/corrections`가 없으므로 **셋 다 인증 필요**다.
> **#9만 기준선이 없다** — 캡처하면 정정 신청 데이터가 실제로 생성되므로 뜨지 않았다 (R15).

**전 사원 90명, 그중 83명이 부서 1(인사팀).** 한 번 호출에 나가는 해시:
`03` 90개(632KB) / `04` 90개(638KB) / `06` 90개 / **`07` 83개**(109KB) / `08` 2개.
**`/commutes?target=depart`는 사실상 전 직원의 해시를 한 번에 내보낸다.**

### 3-3. JWT payload 실측 (계정 Z 토큰 디코드)

```
{"positionName":"…","sub":"240501629","departNo":1,"role":"ADMIN",
 "imageUrl":"1","name":"…","memberStatus":"재직","exp":…,"departName":"…","memberId":240501629}
```

- **`password` claim이 없다** → #5가 `null`인 근거
- **`role`이 raw 문자열 `"ADMIN"`** → `MemberRole.valueOf()` 성립, authority도 접두사 없이 `"ADMIN"`

### 3-4. `PUT /updateOwnPassword`의 검증 우회 (실물 인용 — v2-4)

```java
// MemberController:319~331
if (updatePasswordRequestDTO.getNewPassword1() == null || updatePasswordRequestDTO.getCurrentPassword() == null) {
    try {
        MemberDTO memberInfo = memberService.findSpecificMember(getTokenInfo123().getMemberId());
        String encodedPassword = passwordEncoder.encode("0000");
        memberInfo.setPassword(encodedPassword);
        memberService.resetPassword(memberInfo);
        return ResponseEntity.ok("Password reset successfully");
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(…);
    }
}
// ↓ 현재 비밀번호 검증은 :334 에 있다 — 위 분기가 그것을 건너뛴다
```

**심각도를 올리는 것**: `jwt.key` 유출과 결합하면 **토큰 위조 → 비밀번호 초기화 → 정상 로그인**이 되어,
토큰 24h 만료와 무관하게 **계정이 영구히 넘어간다.** 작업 A 보고서 §8 잔여 위험 2의 실현 경로다.

### 3-5. 파괴적 실측 (2026-07-31 · before 원문) ★

| # | 요청 | 결과 |
|---|---|---|
| **E-1** | **B 토큰** + `PUT /updateOwnPassword` + body `{}` | **200** `Password reset successfully` — B의 비밀번호가 `0000`이 됐다 |
| **E-2** | **B(MEMBER) 토큰** + `PUT /resetPassword/240501629`(**ADMIN Z**) | **200** — **하급자가 관리자 계정의 비밀번호를 초기화했다** |

> E-2는 인가 부재를 넘어 **권한 방향까지 무시된다**는 것을 보여준다.
> 응답 헤더에 `Access-Control-Allow-Origin: *`도 함께 확인됐다 → CORS 등재 보강(§2).

**E-2 직후 DB 확인 결과 회원 정보 유실 없음.** 단 Z는 `image_url`이 1바이트(`0x31`)이고
`gender`·`birthday`가 NULL이라 **이 케이스는 보존을 증명하지 못한다** → S7을 계정 123으로 교체 (v2-8).

---

## 4. 처방

### 공통 원칙

1. **인가 검증은 `try` 블록 밖, 메서드 첫 줄에 둔다.** `MemberController:147~157`이
   `catch (Exception e) → 500`이라 **`BusinessException`이 삼켜진다** (R1).
   검증은 `:144~145` 사이, `try` **앞**에 들어간다.
2. **role 판정은 `getAuthority()` 문자열 비교로만 한다** (R9).
3. **직렬화 차단은 방향을 구분한다.** 요청 body로 들어올 수 있는 DTO에 `@JsonIgnore`를 쓰면
   **역직렬화까지 막힌다** (R2).
4. **응답 구조를 바꾸는 작업이다.** "응답 불변"을 의도적으로 깬다 —
   **무엇을 깼는지 세지 못하면 다음 작업이 아무거나 깨도 된다고 읽는다** (D7).

---

### [B1] 비밀 분리 — `application.yml` → 환경변수 + 키 로테이션

| 위치 | 현재 | 처방 |
|---|---|---|
| `application.yml:9` | `username: ‹평문›` | `username: ${DB_USERNAME}` |
| `application.yml:10` | `password: ‹평문›` | `password: ${DB_PASSWORD}` |
| `application.yml:44` | `key: ‹평문›` | `key: ${JWT_KEY}` |
| `application.yml:52` | `type: trace` | **제거** (무효 설정) |

- **기본값을 두지 않는다.** `${JWT_KEY:기본값}`으로 쓰면 env 없이도 기동돼 결국 약한 키로 돌아간다.
  **누락이 기동 실패로 드러나야 한다** (R5)
- **`show-sql: true`와 `org.hibernate.sql: debug`는 유지한다.** 리팩토링 이래 검증 수단이었다

```powershell
# 새 키 생성 — 값은 콘솔에만. 문서·커밋·보고서에 넣지 않는다
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)

[Environment]::SetEnvironmentVariable('JWT_KEY',     '‹생성한 키›', 'User')
[Environment]::SetEnvironmentVariable('DB_USERNAME', '‹계정›',      'User')
[Environment]::SetEnvironmentVariable('DB_PASSWORD', '‹비밀번호›',  'User')
```

> ⚠ 새 키는 **유효한 Base64 + 디코드 32바이트 이상**이어야 한다. `parseBase64Binary`가 비-Base64 문자를
> 조용히 버린다 (R4). 현재 키가 정확히 그 상태이며 그 자체가 로테이션 근거다.

---

### [B2] 인가 — 비밀번호 경로 2종

#### [B2-a] `PUT /resetPassword/{memberId}` — ADMIN 전용

| 위치 | 현재 | 처방 |
|---|---|---|
| `MemberController:142~158` | 인가 **0줄** (E-2 실증) | `:144~145` — **`try`(`:147`) 앞**에 ADMIN 검증 |

```
1. isAdmin() == false  →  BusinessException(HANDLE_ACCESS_DENIED)   // C005 / 403
2. (이하 기존 로직 그대로 — try-catch 포함)
```

**헬퍼 신설** (`MemberController` private):

```
getCurrentMemberId() : Integer.parseInt(SecurityContextHolder.getContext().getAuthentication().getName())
isAdmin()            : ...getAuthorities().stream().anyMatch(a -> "ADMIN".equals(a.getAuthority()))
```

- **"호출자 == 대상" 검증은 넣지 않는다.** 본인 초기화는 `/updateOwnPassword`가 담당한다 (D8)
- 프론트 회귀 위험 0 — 호출부가 주석 처리돼 있다

#### [B2-b] `PUT /updateOwnPassword` — 초기화 분기 제거

| 위치 | 현재 | 처방 |
|---|---|---|
| `MemberController:319~331` | 검증 우회 → `0000` + 200 (E-1 실증) | **분기 전체 제거** → `BusinessException(INVALID_INPUT_VALUE)` (C001 / 400) |

- 기존 `:334~343`의 검증 3종은 **그대로 둔다**
- 인가 불필요 — 토큰에서 자기 사번을 뽑으므로 이미 본인 한정

> ⚠ **의도된 동작 변경 1건.** 관리자 화면의 "비밀번호 초기화" 버튼이 200 → **400**이 된다.
> 기능 상실이 아니라 **잘못된 성공의 제거**다. 프론트 수정은 별도 작업이다.
> **부수 효과**: 관리자 레코드가 전체 덮어쓰기 경로를 통과하는 일도 사라진다 (R6).

---

### [B3] 응답 `password` 제거 — 9지점

**DTO마다 방향이 다르다. 하나의 관용구를 강요하지 않는다.**

| 대상 | 역직렬화 가능? | 처방 |
|---|---|---|
| `member/dto/MemberDTO.java` | **예** — `signUp:81~86` | **`@JsonProperty(access = Access.WRITE_ONLY)`** |
| `commute/dto/CommuteMemberDTO.java` | **예** — `@Setter` + `@NoArgsConstructor` | **`@JsonProperty(access = Access.WRITE_ONLY)`** |
| `approval/dto/MemberDTO.java` | 아니오 (응답 전용) | **`@JsonIgnore`** |
| `member/dto/ShowMemberDTO.java` | 아니오 (세터로만 조립) | **`@JsonIgnore`** |
| `commute/entity/CommuteMember.java` | **아니오** — 세터 없음, 무인자 생성자 `protected` | **`@JsonIgnore`** |

- **`@JsonIgnore`를 `member.dto.MemberDTO`에 붙이면 회원가입이 500으로 죽는다** (R2)
- **`CommuteMemberDTO`는 요청 사용 여부가 미확인이라 `WRITE_ONLY`가 안전하다** — 역직렬화를 막지 않는다
- **필드 제거·전용 DTO는 채택하지 않는다** (D4)
- **애너테이션은 직렬화 시점에만 작동한다.** ModelMapper는 무시하므로 객체 안에는 해시가 남는다.
  **로그 경로는 [B4]가 닫는다**
- **`CommuteController`의 응답 맵 키(`member`/`members`/`notice`)는 그대로 둔다** — 키 제거는 응답 구조
  변경이고 프론트 사용 여부가 미확인이다 (D13)

---

### [B4] 로그 위생

#### (1) `toString()` 마스킹 — **5파일**, 각 1줄

| 파일 | 방식 | 닫히는 로그 |
|---|---|---|
| `member/entity/Member.java` | 수동 `toString()` → `password='***'` | `MemberService:50`(로그인마다)·`:65`·`:71`·`:107`·`:127~134`·`:140`·`:182` |
| `member/dto/MemberDTO.java` | 〃 | `CustomAuthSuccessHandler:27` · `TokenUtils:77`·`:109` · `MemberService:168` |
| `auth/model/dto/LoginDTO.java` | 〃 | (도달 불가 경로 대비 — D11) |
| `member/dto/UpdatePasswordRequestDTO.java` | 〃 | `MemberController:317` — **평문 3개** |
| `commute/dto/CommuteMemberDTO.java` | **Lombok** `@ToString(exclude = "password")` | `CommuteService:495` 등 |

#### (2) 직접 출력 제거 — **13지점**

| 지점 | 내용 | 상태 |
|---|---|---|
| `CustomAuthenticationProvider:35` | **평문** | 활성 |
| `MemberController:316` | 해시 (`existingPassword`) | 활성 |
| `MemberController:317` | DTO 전체 | 활성 (마스킹돼도 라인 제거) |
| `MemberService:191`·`:193` | **전 사원 `findAll()` 전체, 한 요청에 2회** | 활성 |
| `CustomAuthSuccessHandler:38` | **토큰 전문** + 주석의 하드코딩 JWT | 활성 |
| `TestController:27`·`:45` | **토큰 전문** | 활성. **로그 라인만** (D12) |
| **`CommuteService:495`** | `memberDTOList.forEach(System.out::println)` — DTO 전체 | 활성 |
| **`CommuteController:111~113`** | `responseMap.forEach(…)` — `members` 키 | 활성 |
| **`CommuteController:284~286`** | 〃 — `member` 키 | 활성 |
| `MemberController:477` | 평문 | **도달 불가** (D11) |
| `MemberService:272` | LoginDTO 전체 | **도달 불가** (D11) |

#### (3) 설정

`application.yml:52`의 `type: trace` 제거 ([B1]과 같은 파일)

#### 범위를 못박는 규칙 (D5)

> **R1 (금지 대상)** — 비밀번호(평문·해시), JWT 토큰 전문, `Authorization` 헤더 값은 **어떤 로그에도
> 출력하지 않는다.** 주석에 든 예시 값도 포함한다.
>
> **R2 (금지 형태)** — 비밀 필드를 가진 객체(`Member`·`member.dto.MemberDTO`·`ShowMemberDTO`·
> `LoginDTO`·`UpdatePasswordRequestDTO`·`CommuteMemberDTO`)의 비밀 필드는 **`toString()` 마스킹으로 닫는다.**
> 단 **다건 전체 출력**(`findAll()` 결과, `forEach(println)` 등)은 개인정보 대량 노출이므로 **라인 자체를 제거한다.**
>
> **R3 (설정)** — `logging.level.org.hibernate.type: trace`를 제거한다.
> `show-sql`과 `org.hibernate.sql: debug`는 **유지한다.**
>
> **R4 (범위)** — 이번 작업이 여는 파일은 §5 목록뿐이다. 그 밖에서 발견한 로그 결함은
> **보고서에 기록만 하고 고치지 않는다.**
>
> **R5 (완료 판정)** — §10의 검색 확인 8종을 실행하고 결과를 보고서에 원문으로 붙인다.
> 그리고 **로그인 1회의 콘솔 로그 전문**을 before/after로 대조한다 (S4).

---

## 5. Scope — 수정 허용 파일 (**코드 16개**)

**설정 (1)**
- `final/src/main/resources/application.yml` — 비밀 3종 → 환경변수, `:52` 제거

**인가 (1)**
- `member/controller/MemberController.java` — 헬퍼 2개 신설 / `resetMemberPassword` 인가 /
  `updateOwnPassword` 분기 제거 / **로그 3곳**(`:316`·`:317`·`:477`) ← v2-2

**직렬화 + `toString()` (4)**
- `member/dto/MemberDTO.java` — `WRITE_ONLY` + 마스킹
- `member/dto/ShowMemberDTO.java` — `@JsonIgnore` + 마스킹
- `member/dto/UpdatePasswordRequestDTO.java` — 마스킹
- `approval/dto/MemberDTO.java` — `@JsonIgnore` + 마스킹

**엔티티 · DTO (2)**
- `member/entity/Member.java` — 마스킹
- `auth/model/dto/LoginDTO.java` — 마스킹

**로그 (4)**
- `member/service/MemberService.java` — `:191`·`:193`·`:272`
- `auth/handler/CustomAuthenticationProvider.java` — `:35`
- `auth/handler/CustomAuthSuccessHandler.java` — `:38` + 주석
- `member/controller/TestController.java` — `:27`·`:45`

**commute (4)** ← v2-5
- `commute/entity/CommuteMember.java` — `@JsonIgnore`
- `commute/dto/CommuteMemberDTO.java` — `WRITE_ONLY` + `@ToString(exclude = "password")`
- `commute/service/CommuteService.java` — `:495`
- `commute/controller/CommuteController.java` — `:111~113`·`:284~286`

**신규**: 없음 **삭제**: 없음
**17번째 코드 파일이 diff에 등장하면 범위 이탈이다.** 문서는 §9에 따로 있다.

**금지 (손대지 않음)**
- 🚫 `JwtAuthorizationFilter`, 🚫 `WebSecurityConfig`
- `common/error/ErrorCode.java`(**신규 0건**), `GlobalExceptionHandler.java`, `config/BeanConfig.java`
- `common/utils/TokenUtils.java`, `common/utils/ConvertUtil.java` — 둘 다 다른 처방으로 닫힌다
- `MemberService`·`CommuteService`·`CommuteController`의 **나머지 로직** (응답 맵 구성 포함 — D13)
- `approval/**`의 코드(DTO 1개 제외), `webSocket/**`(응답 DTO 변경으로만 닫힌다), `insite/**`
- `src/test/**`, 프론트엔드, DB 스키마, `.gitignore`

---

## 6. ErrorCode — 재사용만, 신규 0건

| 상수 | 코드 | HTTP | 이번 용도 |
|---|---|---|---|
| `HANDLE_ACCESS_DENIED` | **C005** (기존) | 403 | [B2-a] ADMIN 아님. **선언만 있고 사용처 0건이었다 — 이번에 활성화** |
| `INVALID_INPUT_VALUE` | **C001** (기존) | 400 | [B2-b] 비밀번호 파라미터 누락 |

- **`ErrorCode.java`를 수정하지 않는다.** M 계열 다음 여유 번호는 M008이지만 필요 없다 (D3)
- `GlobalExceptionHandler:21~28`이 `HttpStatus.valueOf(errorCode.getStatus())`로 매핑한다.
  **반환 타입이 `ResponseEntity<String>`이어도 무관하다** — `@RestControllerAdvice`가 응답을 대체한다 (리뷰 4-a 확인)

---

## 7. 결정 사항 (D1~D13 전 항목 확정)

### D1. git history rewrite — ✅ **하지 않는다**
키 로테이션으로 무력화하고 **판단 근거를 문서에 남긴다.**
① rewrite로도 완전히 지워지지 않는다(GitHub dangling object, 포크·PR 참조) — **"공개 리포에 한 번 올라간
비밀은 유출된 것"**이 전제여야 하고 그러면 처방은 로테이션이다 ② 배포 인스턴스가 없다
③ **이 리포는 커밋 이력 자체가 성과물**이라 문서 전반의 커밋 해시 인용이 전부 무효가 된다.

### D2. 비밀 분리 방식 — ✅ **환경변수, 기본값 없음**
`application-local.yml` + `.gitignore`는 **이번 사고와 같은 구조**다. 환경변수는 파일이 없다.
DB 계정도 함께 뺀다 — "비밀 문자열 0건"이어야 검색으로 완료 판정이 된다.

### D3. 인가 ErrorCode — ✅ **C005 재사용, 신규 0건**
이 엔드포인트에는 지금 403 경로가 없으므로 **형식 변경이 아니라 신설**이다(작업 A D11과 같은 논리).

### D3-1. 검증 방식 — ✅ **명시적 코드 검증** (`@PreAuthorize` 금지)
`@EnableGlobalMethodSecurity`가 켜져 있어 동작은 하지만 **거부 시 500이 된다**(실측).
※ 조사 답변 중 "`Position.java:11`의 `@PreAuthorize`가 동작한다"는 **오류** — 엔티티는 프록시 대상이 아니다.

### D3-2. 검증 위치 — ✅ **`try` 블록 밖, `:144~145`**
`catch (Exception e) → 500`이 `BusinessException`을 삼킨다 (R1).

### D3-3. 판정 코드 — ✅ **`getAuthority()` 문자열 비교**
`DetailsMember:34`가 람다를 넣으므로 `SimpleGrantedAuthority` 비교는 실패하고,
`ROLE_` 접두사가 없어 `hasRole`류도 맞지 않는다.

### D3-4. 검증 계층 — ✅ **Controller**
ADMIN 판정은 조회가 필요 없고, `MemberService.resetPassword(MemberDTO)`는 두 진입점이 공유한다.

### D4. 응답 `password` 제거 방식 — ✅ **DTO별로 다르게** (§4 [B3])
**일관성보다 정확성을 택했다.** 기준은 하나 — **역직렬화가 필요한가.**
필요하면 `WRITE_ONLY`, 아니면 `@JsonIgnore`. 이 기준을 명세에 적어 "비일관"으로 읽히지 않게 한다.

### D5. 로그 위생 범위 — ✅ **R1~R5** (§4 [B4])

### D6. `toString()` — ✅ **마스킹**
호출부만 고치는 안은 §3-1 로그가 반박한다. **5줄로 13곳 이상이 닫힌다.**
필드 삭제가 아니라 마스킹인 이유: 필드가 있되 로그에 안 나온다는 사실이 코드에 남는 게 낫다.

### D7. 응답 구조 변경의 기록 — ✅ **작업 A D11 선례 재사용**
① 명세 전용 절(§3-2) ② 성공 기준에 **숫자로** ③ 보고서 §3에 **before/after 원문**(값은 `‹생략›`)
④ 프론트 근거의 한계 명시 — 2024-05-31 스냅샷이며 **회귀 부재의 근거로만** 쓴다.

### D8. `resetPassword`의 검증 주체 — ✅ **ADMIN 전용**
"호출자 == 대상"은 의미가 없다 — 본인 초기화는 `/updateOwnPassword`가 담당하고,
그러면 `@PathVariable`이 존재할 이유가 사라진다. **대안(엔드포인트 삭제)을 접은 이유**: 프론트 복구 시
백엔드를 다시 만들며 인가가 또 빠질 수 있다.

### D9. `updateOwnPassword` 초기화 분기 — ✅ **제거, 누락은 400/C001**
정상 기능이 아니라 **미완성 코드**로 보인다 — `resetPassword`와 같은 코드를 복사했고 바로 아래에 정식
검증이 있다. `resetPassword`만 막고 이걸 남기면 **더 쉬운 우회로를 옆에 열어둔 채 "인가를 넣었다"고
보고**하게 된다.

### D10. 문서 배치 — ✅ **`spec.md`만 `docs/security/` 직하로**
작업 A 산출물은 **옮기지 않는다.** 커밋 메시지·완료 보고서·인계 문서가 그 경로를 인용한다 (§9-1).

### D11. 죽은 코드 — ✅ **로그 라인만 제거, 메서드는 남긴다**
대상: `MemberController:474~484` / `MemberService:243~259` / `:271~279` — 전부 호출부 0건(리뷰 6 확인).
메서드 삭제는 별개 주제다. **라인 제거는 비용 0이고 "되살아나도 안전"을 확보한다.**
보고서에는 **"활성 2건 + 도달 불가 2건"**으로 나눠 적는다 — 합쳐 4건은 과대 보고다.

### D12. `TestController` — ✅ **로그 라인만**
엔드포인트 제거는 `spec.md` §4-3에 **등재 유지.**
**부수 관찰(기록만)**: 이 컨트롤러는 비-ADMIN에게 403이 아니라 **500**을 반환한다.

### D13. `commute` 편입 — ✅ **편입하되 직렬화·로그만** (신규)

**편입 근거**(§5-1이 B와 C를 합칠 때 쓴 기준 그대로):
주제가 하나(`password`가 응답에 실린다) / 처방이 같다(직렬화 차단 + 로그 제거) /
**빼면 성공 기준 "응답 password 0지점"이 거짓이 된다.**

**단, 응답 맵 키(`member`/`members`/`notice`)는 지우지 않는다.**
`CommuteController`가 원본 컬렉션을 맵에 남기는 **구조 자체**는 후속 주제다 — 키를 제거하면
응답 구조가 바뀌고 프론트 사용 여부가 미확인이다. **이번엔 필드만 막는다.**

`CommuteMember` 엔티티에 `@JsonIgnore`를 붙이는 것은 "엔티티라서 예외"가 아니라
**노출 경로(`:264`)가 실제로 있으니 막는 것**이다.

---

## 8. 위험 목록

| # | 위험 | 대응 |
|---|---|---|
| **R1** ★ | **`try-catch`가 `BusinessException`을 삼킨다.** `MemberController:147~157`. 인가를 `try` 안에 넣으면 **403이 500이 된다.** 컴파일·정상경로에 드러나지 않는다 | D3-2 — `:144~145`. **S2-b가 잡는 유일한 항목** |
| **R2** ★ | **`@JsonIgnore`를 `member.dto.MemberDTO`에 붙이면 회원가입이 죽는다.** 양방향 차단이라 `signUp:85`의 `encode(null)`이 던진다. `CommuteMemberDTO`도 같은 형태 | `WRITE_ONLY`. **S0-b가 판정** |
| **R3** | 키 로테이션으로 **발급된 모든 토큰이 즉시 무효** | [B1]을 **마지막 단계(P5)**로. 이후 전 계정 재로그인 |
| **R4** | 새 키가 유효 Base64가 아니거나 32바이트 미만이면 **실효 엔트로피가 낮다** | §4 [B1] 생성 명령 사용. 로그인 성공으로 확인 |
| **R5** | env 미설정 시 **기동 실패**. IDE 실행 구성에도 필요 | **의도된 동작.** S6에서 확인 |
| **R6** 🟡 | `resetPassword`가 `DTO → Entity → save()`로 **엔티티 전체를 덮어쓴다.** `BeanConfig:29`의 `setAmbiguityIgnored(true)` 때문에 매핑이 틀려도 신호가 없다. **E-2 실측에서 유실은 없었으나, Z는 필드가 빈약해 증명력이 없다** | **고치지 않는다.** **S7을 계정 123으로** 수행 (v2-8). 유실 시 **기록만** |
| **R7** | `resetPassword`만 막고 `updateOwnPassword`를 남기면 **더 쉬운 우회로가 남는다** | D9. **S3이 판정** |
| **R8** | `@PreAuthorize` 사용 시 거부가 **500**이 된다 | D3-1. 사용 금지 |
| **R9** | `SimpleGrantedAuthority` 비교는 **실패한다** — 람다 구현체다 | D3-3 |
| **R10** | 응답에서 사라진 `password`를 프론트가 요청으로 되돌리는 경로 | 리뷰 3-b: `MemberController:265` **1곳뿐이며 서버가 DB에서 다시 채운다.** 그래도 **S0-b·S0-c로 실측** |
| **R11** | `GET /showAllMembersPage`가 **무인증 200**(P0 실증). 전 사원 90명 인사정보가 토큰 없이 조회된다 | **범위 밖(작업 D 등재).** 인증 추가는 작업 E |
| **R12** | 토큰 전문 로그 제거로 디버깅 수단이 준다 | 문제 아님 |
| **R13** 🟡 | `approval.dto.MemberDTO` 변경의 결재 도메인 회귀 | **하향** — 12인자 생성자 **사용처 0건**, 15인자 생성자 **0건**(v2-7). 애너테이션·`toString()`만 건드린다. **S0-f 스모크는 유지** |
| **R14** ★ | **`src/test/**`에 commute 테스트가 있다.** member·auth·approval은 0건이지만 여기는 다르다 — `compileTestJava`가 깨질 수 있다 | **착수 전 확인**(§12-10). 깨지면 **중단·보고** |
| **R15** 🟡 | **하향.** 7·8은 기준선 확보, 인증 필요 확정. **`POST /corrections`(#9)만 미캡처** — 캡처하면 정정 신청 데이터가 생성된다 | 보고서에 **"#9 before 미캡처, 사유: 부작용 회피"**로 명시. after의 `password` 부재만으로 판정한다. 필드 구성은 엔티티 코드로 확정돼 있다 |
| **R16** ★ | **에러 본문이 기준선·검증 결과에 섞인다.** 만료 토큰은 **HTTP 200 + `{"status":401}`**, 필수 파라미터 누락은 400 + C003이다. 둘 다 `password` 0건이라 **"노출 없음"으로 오판**하게 된다 | §10 S1의 **저장 가드 함수 사용 필수.** `pw=` 값이 기대와 다르면 본문부터 확인 |

---

## 9. 문서 갱신

### 9-1. `spec.md` 이동 및 정정

```
docs/security/approval/spec.md   →   docs/security/spec.md
```

- **§4를 B+C 통합으로 정정.** 작업 C 행을 없애고 B에 흡수 (근거: 인계 문서 §5-1)
- **§4-2의 노출 4지점 → 9지점**으로 정정 (v1-2·v1-3·v2-5 반영)
- **§4-3 등재 추가**: `showAllMembersPage` 무인증(R11) / `setAmbiguityIgnored` 전역 영향(R6) /
  `TestController`의 403→500(D12) / **`CommuteController`의 응답 맵 키 구조**(D13) /
  **CORS 전역 개방 실측 확인**
- 상단에 작업 A 산출물 위치를 명시 (`docs/security/approval/` 유지 — D10)

### 9-2. `AGENTS.md` — 3지점

**(1) 현재 진행 작업**

```markdown
**작업 B: 비밀 정보 노출 차단 · 비밀번호 경로 인가** — `docs/security/tasks/02-secret-exposure.md`
(작업 A 쓰기 경로 권한 경계 정리 완료·커밋·푸시 — `91de70c`(코드)·`8731d37`(문서), origin/main 동기화)
```

**(2) 로드맵** — B와 C를 통합 1항목으로

```
A. 쓰기 경로 권한 경계 정리 ✅
B. 비밀 정보 노출 차단 · 비밀번호 경로 인가 (현재)  ※ 구 B + 구 C 통합, commute 도메인 포함
```

**(3) 필수 읽기 순서**의 `spec.md` 경로 갱신

### 9-3. `CLAUDE.md` — 1지점

현재 규칙 `docs/{작업 스트림}/{도메인}/reports/`에 이번 작업은 도메인이 없다.

> 추가할 문장: **"도메인이 특정되지 않는 작업은 `{도메인}`을 생략한다"**
> (예: `docs/security/reports/02-secret-exposure-report.md`)

### 9-4. 보고서

`docs/security/reports/02-secret-exposure-report.md` — `01-write-authz-report.md` 형식을 따른다.

> ⚠ **보고서는 origin에 푸시된다.** 토큰 전문·비밀번호·해시 실값을 남기지 않는다.
> 응답 인용 시 `"password": "‹생략›"`, 키는 `‹생략›`.

---

## 10. 검증

### 자동 검증

```powershell
cd final
.\gradlew.bat compileJava
.\gradlew.bat compileTestJava      # ⚠ R14 — commute 테스트가 있다
.\gradlew.bat bootRun
```

> ⚠ 세 개가 통과했다는 것은 **아무것도 증명하지 않는다.** 직렬화·인가·로그 변경은 컴파일에 드러나지 않는다.
> ⚠ **env 3개를 설정한 셸에서 실행한다.** 미설정 시 기동 실패가 정상이다 (R5 / S6).

### 검색 확인 (PowerShell 5.1)

> ⚠ `Select-String`에는 `-Recurse`/`-Include`가 없다.

```powershell
cd final
$src = Get-ChildItem -Path .\src\main\java -Filter *.java -Recurse

# 1. application.yml 에 비밀 문자열 0건 (${...} 참조만 남아야 한다)
Get-ChildItem -Path .\src\main\resources -Filter application.yml |
    Select-String -Pattern "key:|username:|password:|type: trace" -Encoding UTF8

# 2. C005 사용 (1건이어야 한다)
$src | Select-String -Pattern "HANDLE_ACCESS_DENIED" -Encoding UTF8

# 3. ErrorCode 무변경 (M008 이 0건)
Get-ChildItem -Path .\src\main\java\com\insider\login\common\error -Filter ErrorCode.java -Recurse |
    Select-String -Pattern "M008" -Encoding UTF8

# 4. password 를 찍는 로그 잔존 (눈으로 판정)
$src | Select-String -Pattern 'println\(.*[Pp]assword' -Encoding UTF8

# 5. 토큰 전문 로그 잔존 (0건)
$src | Select-String -Pattern 'println\("token' -Encoding UTF8

# 6. 직렬화 차단 애너테이션 (5건 — DTO 4 + 엔티티 1)
$src | Select-String -Pattern "JsonIgnore|WRITE_ONLY" -Encoding UTF8

# 7. @PreAuthorize 신규 도입 0건 (기존 2건 외에 늘지 않았는지 — R8)
$src | Select-String -Pattern "@PreAuthorize" -Encoding UTF8

# 8. 범위 이탈 (코드는 16파일)
git status
git diff --stat
```

### 수동 검증 — 시나리오 (사용자 담당 · **전부 API 직접 호출**)

> **화면으로 검증할 수 없다.** 결재 처리 기능이 프론트에서 정지 상태이고, 관리자 초기화 버튼도 엉뚱한
> API를 탄다. Postman 등으로 수행한다.
> 계정: **Z**=240501629(ADMIN) / **B**=240501544(MEMBER) / **123**(ADMIN, S7 대상).
> ⚠ **Z·B는 E-1·E-2로 비밀번호가 `0000`이다.** 로그인 실패 시 이것부터 의심할 것.
> **판정은 응답 코드로 한다** — 프론트는 403을 삼킨다.

---

**S0. 정상 경로 비회귀** ★★ **가장 중요. 먼저 한다**

| # | 시나리오 | 기대 | 이 항목이 잡는 것 |
|---|---|---|---|
| **S0-a** | 로그인(새 키 적용 후) | **200** + 토큰 발급 | [B1] 로테이션 |
| **S0-b** ★★ | **회원가입** (`POST /signUp`, `password` 포함) | **200** + 그 비밀번호로 로그인 성공 | **R2 판정.** `@JsonIgnore`를 붙였으면 **여기서만 죽는다** |
| **S0-c** | 프로필 수정 (`PUT /members/updateProfile/{id}`) | 200, **비밀번호 불변** | R10 |
| **S0-d** | 본인 비밀번호 변경 (세 필드 정상) | 200 + 새 비밀번호로 로그인 | [B2-b] 정상 갈래 |
| **S0-e** | **ADMIN(Z)이 `PUT /resetPassword/123`** | **200**, 123이 `0000`으로 로그인 | [B2-a] 정상 갈래 |
| **S0-f** | 결재 스모크 — 기안 1건 + 상세 + 목록 5종 | 200, 구조 불변 | R13 |
| **S0-g** | **출퇴근 스모크** — `GET /commutes?target=depart` · `GET /corrections` · `POST /corrections` | **200**, `password` 외 구조 불변 | **v2-5 회귀** |
| **S0-h** | `GET /members/{id}` · `/approvals/members` · `/api/rooms/members` | 200, `password` 키만 없음 | [B3] |

---

**S1. 응답 `password` 소멸 + 키 개수** ★ — **P0 기준선 대조**

**(a) `password` 키 소멸 — 9지점 전부**

**(b) 키 개수 서브체크** ← v2-6. **`KeyCount`가 정확히 `PwCount`만큼만 줄어야 한다.**

| 파일 | before Keys | before Pw | **after Keys (기대)** |
|---|---|---|---|
| `00-login` | 22 | 1 | **21** |
| `01-members-id` | 19 | 1 | **18** |
| `02-approval-member` | 17 | 1 | **16** |
| `03-approval-members` | 1263 | 90 | **1173** |
| `04-rooms-members` | 1710 | 90 | **1620** |
| `05-getTokenInfo` | 19 | 1 | **18** |
| `06-showAllMembers` | 1710 | 90 | **1620** |
| **`07-commutes-depart`** | **4944** | **83** | **4861** |
| **`08-corrections`** | **1496** | **2** | **1494** |
| `09` `POST /corrections` | — | — | **미캡처** — `password` 부재만 확인 (R15) |

**after 캡처 (저장 가드 필수 — R16)**

```powershell
$base = 'http://localhost:8080'
$out  = 'C:\temp\secret\after'
New-Item -ItemType Directory -Force -Path $out | Out-Null

$loginZ = Invoke-WebRequest -Uri "$base/login" -Method Post -UseBasicParsing `
            -ContentType 'application/json' -Body '{"memberId":240501629,"password":"‹현재 비밀번호›"}'
$hZ = @{ Authorization = "Bearer $(($loginZ.Content | ConvertFrom-Json).token)" }

function Save-Capture($url, $file, $headers) {
    try   { $c = (Invoke-WebRequest $url -Headers $headers -UseBasicParsing).Content }
    catch { Write-Warning "$file : HTTP $($_.Exception.Response.StatusCode.value__)"; return }
    if ($c -match '"status"\s*:\s*(400|401)') { Write-Warning "$file : 에러 본문. 저장 안 함`n$c"; return }
    $c | Out-File "$out\$file" -Encoding UTF8
    $k  = ([regex]::Matches($c, '"[A-Za-z_][A-Za-z0-9_]*"\s*:')).Count
    $pw = ([regex]::Matches($c, '"password"\s*:')).Count
    Write-Host "$file  keys=$k  pw=$pw"
}

Save-Capture "$base/login-response-는-위에서-이미-받음" $null $hZ   # 00 은 $loginZ.Content 를 직접 저장
$loginZ.Content | Out-File "$out\00-login.json" -Encoding UTF8
Save-Capture "$base/members/240501629"           "01-members-id.json"       $hZ
Save-Capture "$base/approvals/members/240501629" "02-approval-member.json"  $hZ
Save-Capture "$base/approvals/members"           "03-approval-members.json" $hZ
Save-Capture "$base/api/rooms/members"           "04-rooms-members.json"    $hZ
Save-Capture "$base/getTokenInfo"                "05-getTokenInfo.json"     $hZ
Save-Capture "$base/showAllMembersPage"          "06-showAllMembers.json"   @{}
Save-Capture "$base/commutes?target=depart&targetValue=1&date=2024-05-09" "07-commutes-depart.json" $hZ
Save-Capture "$base/corrections?date=2024-05-09" "08-corrections.json"      $hZ
```

> **판정: `pw` 전부 0 그리고 `keys`가 위 표의 after 값과 일치.**
> 조건절이 아니라 **숫자로 판정한다.** 기대와 다르면 **다른 필드에 애너테이션을 잘못 붙인 것이다.**
> `pw=0`인데 `keys`도 크게 줄었으면 에러 본문일 수 있다 — 본문을 직접 확인할 것 (R16).

---

**S2. [B2-a] `resetPassword` 인가** ★ — **세 방향 필수**

| # | 요청 | 기대 | 이 항목이 잡는 것 |
|---|---|---|---|
| **S2-a** | **ADMIN(Z) 토큰** | **200** | 정상. 403이면 판정 코드가 틀렸다 (R9) |
| **S2-b** ★ | **MEMBER(B) 토큰**, 대상 = Z | **403 + `"C005"`** | **R1 판정. 500이 나오면 검증을 `try` 안에 넣은 것이다** |
| S2-c | 토큰 없음 | 401 계열 (`{"status":401}`) | 기존 동작 |

→ S2-b 직후 **대상 계정으로 기존 비밀번호 로그인이 되는지** 확인 (E-2의 역전 확인)

---

**S3. [B2-b] `updateOwnPassword` 우회 차단** ★

| 요청 | before (실측) | after |
|---|---|---|
| body `{}` | **200** `Password reset successfully` (E-1) | **400 + `"C001"`** |
| `currentPassword`만 | 200 (초기화) | **400 + `"C001"`** |
| **`newPassword2`만 누락** | 400 (기존 검증) | **400** — 변화 없음이 정상 |
| 세 필드 정상 | 200 | **200** (S0-d) |

→ 400을 받은 뒤 **기존 비밀번호로 로그인이 되는지** 확인

---

**S4. 로그 위생** ★ — **로그인 1회 콘솔 전문 before/after 대조**

| 항목 | before | after |
|---|---|---|
| 평문 비밀번호 | **1회** | **0회** |
| 비밀번호 해시 | **4회** | **0회** |
| JWT 토큰 전문 | **1회** | **0회** |
| `password='***'` | 0회 | 마스킹 형태로 등장 |

추가: `GET /showAllMembersPage` 1회에서 `MemberService:191·193` 대량 출력 소멸,
`GET /corrections` 1회에서 `CommuteService:495`·`CommuteController:284~286` 소멸.

> ⚠ 로그에 해시가 남아 있으면 **그 줄을 보고서에 붙이지 말 것.** "몇 회"만 기록한다.

**S5. 키 로테이션 실효**
로테이션 **전** 토큰으로 아무 API 호출 →
**HTTP 200 + 본문 `{"status":401,"message":"Token SignatureException"}`** (또는 `Token Expired`).

> ⚠ **HTTP 코드가 200이다.** `JwtAuthorizationFilter`가 `response.setStatus()`를 호출하지 않고
> 본문만 쓴다(`spec.md` §4-3 "인증 실패 200" 등재분). **본문으로 판정해야 한다** — 상태 코드만 보면
> "인증이 통과했다"로 오판한다 (R16). 재로그인한 새 토큰으로는 정상 동작해야 한다.

**S6. env 누락 시 기동 실패**
`JWT_KEY`를 지운 새 셸에서 `bootRun` → **기동 실패**. 성공하면 어딘가에 기본값이 남아 있다 (R5).

**S7. [R6] `resetPassword` 후 회원 정보 보존** ★ — **대상: 계정 123** (v2-8)

S0-e 직후 **P0-C의 before 스냅샷**과 대조한다.

```sql
SELECT member_id, name, depart_no, position_level, employed_date, address,
       phone_no, email, HEX(image_url), CHAR_LENGTH(image_url),
       gender, birthday, member_status, member_role
FROM member_info WHERE member_id = 123;
```

**기대: `password` 외 13개 컬럼 전부 불변.** 특히 `HEX(image_url)` 35바이트 유지.
**유실이 있으면 고치지 않고 기록**한다 — 이번 작업이 만든 것이 아니다.

**S8. 응답 형식 동등성**
P0 기준선과 대조. **`password` 키 소멸 9지점 외에 차이가 나오면 회귀다.**
- 결재: 상세 / 목록 5종 / 회수 / 처리 / 삭제
- 회원: 조회 / 전 사원 / 가입 / 프로필 수정
- **출퇴근: `GET /commutes` / `GET /corrections` / `POST /corrections`**

---

## 11. 실행 순서

```
P0-A. 응답 기준선 캡처 (00~06, 6종)          ✅ 완료
P0-B. commute 기준선 (07·08)                ✅ 완료 — #9 는 미캡처(R15)
P0-C. 계정 123 before 스냅샷                ✅ 완료 (CSV)
      ↓  ── 여기까지가 되돌릴 수 없는 단계. 전부 끝났다 ──
P1. 문서 이동 + spec.md 정정 (§9-1)
      ↓
P2. [B4] toString 마스킹 5 + 직접 로그 제거 13지점
      · 응답 불변. 가장 안전한 단계
      ↓
P3. [B3] 직렬화 애너테이션 (DTO 4 + 엔티티 1)
      · 여기서 응답 JSON이 바뀐다
      ↓
P4. [B2] 인가 — 헬퍼 2개 + resetPassword ADMIN + updateOwnPassword 분기 제거
      ↓
P5. [B1] 비밀 분리 + 키 로테이션
      ↓
P6. compileJava + compileTestJava + bootRun + 검색 확인 8종
      ↓
P7. 수동 검증 S0~S8                          ← 사용자
      ↓
P8. 보고서 + AGENTS.md·CLAUDE.md 갱신 + 커밋·푸시
```

> **P0을 맨 앞에 두는 이유**: D7의 before 원문이고 **되돌릴 수 없다.**
> 작업 A의 `C:\temp\authz\baseline\`은 응답 구조가 바뀌는 이번 작업엔 쓸 수 없다.
>
> **P0-C 선택 사항**: `gender`·`birthday`가 세 계정 모두 NULL이라 S7이 두 컬럼을 검증하지 못한다.
> 필요하면 스냅샷 **전에** 값을 넣어둔다 —
> `UPDATE member_info SET gender='M', birthday='1995-03-14' WHERE member_id=123;`
>
> **P2 → P3 순서**: 로그 위생은 응답을 바꾸지 않는다. 먼저 끝내면 P3 이후 문제가 생겼을 때
> 원인을 구분할 필요가 없다.
>
> **P5를 마지막에 두는 이유**: [B1]은 코드가 아니라 **실행 환경**을 바꾼다.
> 구현 중 매번 env를 요구하면 마찰이 크고, 설정 실수가 앞 단계의 빌드·기동 확인을 막는다 (R3).

**커밋 분리**: **코드 / 문서** 2커밋. 커밋·푸시는 **사용자가 한다.**

---

## 12. 착수 전 체크 (Claude Code)

1. §7 D1~D13은 **확정됐다.** 더 나은 안이 떠올라도 바꾸지 말고 **보고**해라.
2. **인가 검증을 `try` 블록 안에 넣지 않았는가?** (R1 — 403이 500이 된다)
3. **`member.dto.MemberDTO`·`CommuteMemberDTO`에 `@JsonIgnore`를 붙이지 않았는가?** (R2)
4. **`@PreAuthorize`를 새로 도입하지 않았는가?** (R8)
5. **`SimpleGrantedAuthority` 비교를 쓰지 않았는가?** (R9)
6. **`application.yml`에 기본값(`${X:기본값}`)을 넣지 않았는가?** (R5)
7. **`CommuteController`의 응답 맵 키를 지우지 않았는가?** (D13 — 필드만 막는다)
8. 🚫 `JwtAuthorizationFilter` / `WebSecurityConfig` / `ErrorCode.java` /
   `GlobalExceptionHandler` / `BeanConfig`를 **열지 않았는가?**
9. diff의 코드 파일이 **16개인가?** (§5)
10. ⚠ **`compileTestJava`가 통과하는가?** `src/test/**`에 **commute 테스트가 있다** (R14).
    깨지면 **중단하고 보고**해라 — 테스트를 고치는 것은 이번 범위가 아니다.
11. **비밀 실값(키·비밀번호·해시·토큰 전문)을 보고서·커밋·콘솔 출력에 남기지 않았는가?**
12. ⚠ **기준선은 8종(`00~08`)이 `C:\temp\secret\baseline\`에 있다.** `POST /corrections`(#9)만 미캡처다.
    이 경로는 after의 `password` 부재로만 판정한다 (R15).
13. ⚠ **검증 시 에러 본문을 성공으로 오판하지 마라.** 만료 토큰은 **HTTP 200 + `{"status":401}`**,
    파라미터 누락은 400이다. 둘 다 `password` 0건이라 통과처럼 보인다 (R16).

### 중단하고 보고할 상황

- 이 문서에 없는 요구사항이 필요하다고 판단될 때
- 호출부·영향 범위가 명세보다 넓을 때 (특히 `toString()`을 파싱하는 코드 발견 시)
- `password`를 요청 body로 되돌려 보내는 경로를 발견했을 때 (R10)
- `compileTestJava`가 commute 테스트에서 깨질 때 (R14)
- 기존 동작을 바꿔야만 컴파일이 통과할 때
- 이 문서와 실물이 어긋날 때 — **실물이 정답이고, 문서를 고친다**

추측으로 메우지 마라.

---

## 13. 작업 원칙 리마인더

- **Surgical**: 이 문서가 요구하는 것만. "온 김에" 금지. 눈에 보여도 범위 밖이면 **기록만**
- **파일이 진실의 원천**: 대화에만 있는 결정은 유실 전제
- **가정 금지**: 참조 문서도, **명세도** 실물과 다를 수 있다.
  v1 정정 6건 + v2 정정 8건이 그 사례다. **특히 라인 번호는 앞선 작업이 밀어 놓는다**(v2-1)
- **명세는 실행 전에 리뷰한다**: 읽기 전용 세션과 실행 세션을 분리한다.
  이번 리뷰가 **`commute` 노출 3경로**를 잡았다 — 없었으면 성공 기준이 거짓인 채로 보고됐을 것이다
- **프론트 관찰의 한계**: 회귀 부재의 근거로만 쓴다
- **무성 실패 주의**: `compileJava`·`bootRun` 통과는 아무것도 증명하지 않는다.
  **직렬화·인가·로그 변경은 컴파일에 전혀 드러나지 않는다**
- **조건절이 아니라 숫자로 판정한다**: S1의 키 개수 서브체크가 그 예다 (v2-6)
- **양방향 검증**: 차단만 보고 정상 경로를 놓치면 기능이 죽는다. **S0을 S1~S3보다 먼저 한다**
- **자격증명 취급**: 보고서는 origin에 푸시된다. 실값을 남기지 않는다
- **작업 완료 = 커밋 + 푸시.**
