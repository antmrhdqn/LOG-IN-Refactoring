# 작업 06 — `POST /signUp` 무인증 계정 생성 차단

> 작성: 2026-08-27 (v1) · 정정: 2026-08-27 (**v1.1** — 착수 전 명세 리뷰 반영)
> 직전: 작업 05 인증 경계 정상화 (`tasks/05-authn-boundary.md` · 코드 `23bd8d1` / 문서 `4eaab80`)
> 선례 정본: `docs/security/precedents.md` (`S1`~`S10` · `P1`~`P3` · `R11`)
>
> ⚠ **날짜는 행위 시점을 적는다.** 커밋 시점으로 일괄 치환하지 않는다 (`reports/05` §7-4).

## v1.1 정정 요약 (착수 전 리뷰 · 2026-08-27)

| # | 정정 |
|---|---|
| 1 | **캡처 `12`를 after 전용으로.** before에 찍으면 계정이 하나 더 생긴다 — `/signUp`이 원소인 동안 필터는 헤더를 읽기 전에 `return`하므로 `11`과 **같은 코드 경로**다 |
| 2 | **화면 검증 1이 세 번째 계정을 만든다.** 파괴 **2회 → 3회**, 사번 3개, 대역 삭제, 화면 검증 뒤 원복 단계 신설 (D4 · D5 · §11) |
| 3 | **캡처 도구가 multipart를 보내지 못한다.** PS 5.1 `Invoke-WebRequest`에 `-Form`이 없다 — 도구 개조가 선행 조건 (§10-3 · §12) |
| 4 | **`11`은 실재하는 `departNo`·`positionLevel`로 보낸다.** v1은 매트릭스와 §10-4 주석이 서로 다른 것을 지시했다 |
| 5 | **`cascade = PERSIST` 발동 여부가 `save()`→`merge()` 분기에 달려 있다.** v1 §3-9의 ⚠ 박스는 결론을 단정했다 — **관측 항목으로 교체** (§3-9 · §10-4 ③) |
| 6 | 프론트 라인 번호 4곳 정정 (§3-6) |
| 7 | **`U5` 격하** — `@ManyToOne` EAGER + `open-in-view` 기본 true라 지연로딩 예외 구간이 아니다 |
| 8 | **예외가 2종이다** — 비-multipart는 `MultipartException`, 파트 누락은 `MissingServletRequestPartException`. 핸들러 **2개**가 필요하다 (D6 강화) |
| 9 | 실측 이미지를 **수 KB로 고정** (§12) |
| 10 | §10-2 검사 2번에 기대 건수 명시 |
| 11 | **`13`은 after 비파괴 항목이 전부 끝난 뒤 단독으로** — 아니면 `01` 해시가 오염된다 (§11 9·10단계) |
| 12 | `show-sql` 관측용 **bootRun 콘솔 로그 파일 보존** (§12) |

> **성공 기준 7의 "15항목"은 그대로다.** `12`를 after 전용으로 돌려도 매트릭스 총 항목은 15로
> 불변이고, 줄어드는 것은 **before 캡처 회차**(13 → 12)뿐이다. 두 숫자를 같이 움직이면
> 판정 대상이 실제로 줄어든 것처럼 기록된다.

---

## 0. 착수 상태

| 항목 | 값 |
|---|---|
| HEAD | 이 명세·도구를 담은 커밋. 부모가 `4eaab80` (05 문서 커밋) · `origin/main` 동기화 |
| 워킹 트리 | 클린 (`git status --porcelain` 0줄) |
| 05 처방 | `JwtAuthorizationFilter` `roleLessList` 원소 12 → 4 |
| 잔존 원소 4개 | `/signUp` · `/login` · `/` · `/wss/chatting` |
| 라인 번호 재확인 | ✅ 2026-08-27. §3-1 표가 그 결과다 |

### 이 작업이 상속하는 결정

- **05는 계정을 만들어 보지 않았다.** "05가 `departNo`·`positionLevel` 정보원을 없앴다"를
  **전제로 받지 않는다** (`tasks/05` D12). 06이 파괴적 실측 자리에서 직접 본다
- ⚠ **그 반대 방향의 전제도 채택하지 않는다.** "`cascade`가 발동하지 않으므로 05의 효과는
  성립했다" 역시 관측 전에는 **같은 종류의 미검증 전제**다 (§3-9)
- 05가 `/signUp`을 남긴 유일한 이유는 **프론트가 이 호출에만 토큰을 붙이지 않기 때문**이다
  (`MemberAPICalls.js:75~77`). 그래서 06은 백엔드 전용이 아니다 (`tasks/05` D2)

---

## 1. 목표

**`POST /signUp`이 토큰 없이, 그리고 인증만 된 일반 사원이 임의 `role` 계정을 만드는 것을 닫는다.**

성립하면 작업 A·B·E·F·05가 쌓은 인가 경계 전체가 **계정 하나로 우회**된다.

### 성공 기준

1. `JwtAuthorizationFilter`의 `roleLessList` 원소가 정확히 **3개**(`/login` · `/` · `/wss/chatting`)
2. 판정 방식이 `String.equals`(`contains`) 그대로 — 패턴 매처 도입 0건
3. `MemberController.signUp` 본문 **첫 문장**에 ADMIN 인가 가드가 있고, 그 앞에 다른 문장이 없다
4. 무토큰 `POST /signUp` → **401** (본문이 필터 표준 응답과 동일)
5. 인증된 non-ADMIN(`role=MEMBER`) `POST /signUp` → **403 / `C005`**, `member_info` **0행 증가**
6. ADMIN `POST /signUp` → **정상 등록** — 도구 경로(`13`)와 화면 경로(화면 검증 1) 둘 다
7. 캡처 매트릭스 **15항목** 전 항목 PASS
8. 신규 `ErrorCode` **0건**, 기존 사용 **1건**(`HANDLE_ACCESS_DENIED`)
9. 수정 파일 — 백엔드 **2개**, 프론트 **1개**. 그 외 0건 (도구는 별개 · §5)
10. 파괴적 실측 **3회**로 생긴 데이터가 **전부 원복**됐음을 4개 테이블 카운트로 확인
    (화면 검증 1이 만든 계정 포함)
11. 화면 검증 — 구성원 등록 화면에서 ADMIN이 실제로 등록에 성공한다

### 성공 기준이 아닌 것

- `POST /signUp`의 **500 → 400 정정** (D6 — 분리)
- `MemberController`의 다른 인가 결함 (`PUT /members/updateProfile` · `GET /downloadMemberInfo`)
- 프론트 module-level `const headers` 구조 개선 (3파일 — 05 등재 별건)
- `getOriginalFilename()` 미검증 (§9-1 등재)
- **`U1`·`U2a`·`U2b`·`U4`의 원인 규명.** 관측하고 기록할 뿐 고치지 않는다

---

## 2. 경계 (확정)

### 범위 안 — 3파일

| # | 리포 | 파일 | 처방 |
|---|---|---|---|
| **[G1]** | 백엔드 | `auth/filter/JwtAuthorizationFilter.java` | `roleLessList`에서 `/signUp` 제거 (4 → 3) + 주석 갱신 |
| **[G2]** | 백엔드 | `member/controller/MemberController.java` | `signUp` 첫 문장에 ADMIN 인가 가드 |
| **[G3]** | 프론트 | `apis/MemberAPICalls.js` | `:75~77`에 `Authorization` 헤더 추가 (**호출 시점 조립**) |

> ⚠ **[G3]는 조건부다.** 기준선 화면 관측 결과에 따라 확정한다 — §3-6 · D3

### 범위 밖 (명시 · 🚫 하드 가드)

| # | 금지 |
|---|---|
| 1 | 🚫 **판정 방식 변경 금지.** `roleLessList.contains(request.getRequestURI())` 그대로. 바꾸면 05가 지운 8개 원소가 되살아난다 (`tasks/05` D4) |
| 2 | 🚫 **잔존 3개 원소의 문자열·순서 변경 금지** |
| 3 | 🚫 **`@PreAuthorize` 금지.** `AccessDeniedException` 전용 핸들러가 `GlobalExceptionHandler`에 **0건**이라 catch-all(`Exception`)에 걸려 **500**이 나간다 |
| 4 | 🚫 **`getCurrentMemberId()`(`MemberController:170`) 사용 금지.** 익명 토큰의 `getName()`이 `"anonymousUser"`라 `Integer.parseInt` → `NumberFormatException` → **500**. `isAdmin()`만 쓴다 (§3-3) |
| 5 | 🚫 **`MemberDTO.role`에 `@JsonIgnore`를 붙이거나 서버가 `role`을 덮어쓰지 말 것.** `role`은 등록 화면의 정당한 입력이다(`RegisterMember.js:613~625` 권한 드롭다운). 06은 **호출자 자격만** 본다 |
| 6 | 🚫 **`MemberController`의 다른 인가 결함을 "온 김에" 고치지 말 것** (제약 #13) |
| 7 | 🚫 **`GlobalExceptionHandler` 수정 금지.** 핸들러 추가가 엔드포인트 **5곳**의 응답을 동시에 바꾼다 (§3-5) |
| 8 | 🚫 **파일 복사 블록(`:113~119`) 수정 금지.** 경로 순회 여지는 등재만 한다 (§9-1) |
| 9 | 🚫 **실측에서 경로 순회를 시도하지 말 것.** 성공하면 리포 밖에 파일이 생겨 정리 절차가 무너진다 (D8) |
| 10 | 🚫 **엔티티 `cascade`·`fetch`·`@GeneratedValue`를 손대지 말 것.** §3-9는 **관측 항목**이지 처방 대상이 아니다 |
| 11 | 🚫 자동화 테스트 신설 금지 (`spec.md` 범위 밖) |

---

## 3. 실측 근거

표기: `[정]` 정적 대조 · `[프]` 프론트 · `[기]` 기준선에서 확정 · `[교]` 교차검증(2026-08-27)

### 3-1. 라인 번호 — 인용 전 재확인 완료 `[교]`

**`auth/filter/JwtAuthorizationFilter.java`**

| 항목 | 라인 |
|---|---|
| 주석 블록 | 57~64 |
| `roleLessList` 선언 | **65** |
| 판정문 `contains((request.getRequestURI()))` | **68** (블록 68~71) |
| `Authorization` 헤더 읽기 | **73** ← 판정문보다 **뒤**다 (§10-3 `12` 근거) |

**`member/controller/MemberController.java`**

| 항목 | 라인 |
|---|---|
| `@PostMapping("/signUp")` | **83** |
| 시그니처 (`@RequestPart` 2개) | 84~85 |
| **본문 첫 문장** `System.out.println("signUp method 도착");` | **86** ← 처방 삽입 지점 |
| `findExistingMemberId` `do-while` | 96~101 |
| 파일 복사 `try` | 113~119 (`createDirectories` 114 · `copy` 115) |
| `memberService.saveMember(memberDTO)` | **129** |
| `transferredHistoryService.saveHistory(savedMember)` | **134** |
| 메서드 종료 | 143 |
| `private int getCurrentMemberId()` | 170 (🚫 §2 범위 밖 4) |
| `private boolean isAdmin()` | **175** (본문 176~178) |
| `generateNewMemberId` | 181~191 |

**`member/service/MemberService.java`**

| 항목 | 라인 |
|---|---|
| `saveMember` | `@Transactional` 62 / 선언 63 / `memberRepository.save` **70** / 종료 73 |
| `deleteMemberById` | `@Transactional` 217 / 선언 218 / 종료 220 |

**`member/entity/Member.java`**

| 항목 | 라인 |
|---|---|
| `@Id` + `private int memberId` (assigned · `@GeneratedValue` 없음) | 18~20 |
| `@ManyToOne(cascade = CascadeType.PERSIST)` `department` | **38~40** |
| `@ManyToOne(cascade = CascadeType.PERSIST)` `position` | **41~43** |

> ⚠ **처방 삽입으로 `MemberController:86` 아래가 밀린다.** 보고서에 라인 드리프트 표를
> 남긴다 — 05가 `+8`을 남긴 것과 같은 형식 (`reports/05` §1).

### 3-2. 임의 `role` 계정 생성 조건 — 대부분 성립 `[정]`

| 조건 | 상태 |
|---|---|
| `MemberDTO.role` 존재 · 역직렬화 차단 없음 | `MemberDTO:28`. `@JsonIgnore` 없음 (`WRITE_ONLY`는 `getPassword()`에만) |
| 서버가 `role`을 덮어쓰는가 | **0건.** signUp이 세팅하는 것은 `password` · `memberId` · `imageUrl`뿐 |
| `MemberRole`에 ADMIN | `MEMBER` / `ALL` / **`ADMIN`** (`MemberRole:5~7`) |
| 필수 여부 | `Member.role` `nullable=false` + DDL `member_role varchar(10) NOT NULL` ⇒ 호출자가 넣은 값이 곧 저장값 |
| 다른 회원 생성 경로 | **없음.** `saveMember` 호출 전수 1건 (`MemberController:129`) |
| signUp에 인가 검증 | **0건.** `MemberController`의 `isAdmin()` 히트는 `:149`(`resetPassword`, 작업 B) 하나뿐 |

> `spec.md` §4 표는 이 항목을 *"대부분 성립"*으로 적었다. **"대부분"을 "전부"로 확정하거나
> 반증하는 자리는 기준선 파괴적 실측(§10-3 `11`)뿐이다** — 처방 후에는 무토큰이 401에서 끊겨
> 관측 자체가 불가능해진다.

### 3-3. 익명 인증 토큰 — `isAdmin()`은 NPE가 아니라 `false` `[교]`

`WebSecurityConfig`에 `http.anonymous(...)` 호출이 **0건**이고 `SecurityFilterChain` 정의는
`WebSecurityConfig:47` 단 1곳이다. ⇒ Spring Security 기본값(`AnonymousAuthenticationFilter`)이 적용된다.

`JwtAuthorizationFilter`는 `addFilterBefore(..., BasicAuthenticationFilter.class)`(`WebSecurityConfig:51`)로
등록돼 `AnonymousAuthenticationFilter`보다 **앞**이다. `roleLessList` 분기(`:68~71`)는
`SecurityContextHolder`에 아무것도 넣지 않고 통과시키므로, 뒤이어 익명 토큰이 채워진다.

| 컨트롤러에서 | 무토큰 시 | 결과 |
|---|---|---|
| `isAdmin()` (`:175`) | authorities = `[ROLE_ANONYMOUS]` | `false` → **403** |
| `getCurrentMemberId()` (`:170`) | `getName()` = `"anonymousUser"` | `NumberFormatException` → **500** |

**⇒ 이것이 [G2]만 채택할 수 없는 근거다.** [G2]만이면 무토큰 요청이 **401이 아니라 403**을 받는다.
작업 F·05가 세운 **선례 `S9`(인증 실패는 401 단일)** 를 06이 정면으로 깨는 것이고,
프론트는 `=== 401`만 보므로 그 경우만 조용히 실패한다.

> ⚠ **이 경로는 처방 후 도달 불가능해진다.** [G1]이 `/signUp`을 빼면 무토큰은 필터에서 끊긴다.
> ⇒ **익명 토큰 거동은 06의 실측 항목이 아니다.** 대신 §10-3 `10`의 **역방향 판정문**이
> [G1] 누락을 잡는다.

### 3-4. `isAdmin()`은 `ADMIN`과 `ALL`을 통과시킨다 `[정]`

```
DetailsMember.getAuthorities()
  → memberDTO.getRoleList().forEach(role -> authorities.add(() -> role))
     ※ GrantedAuthority.getAuthority() 반환값 = 그 문자열 그대로 (ROLE_ 접두사 없음)
```

| 토큰 `role` | authorities | `isAdmin()` |
|---|---|---|
| `ADMIN` | `["ADMIN"]` | ✅ |
| `ALL` | `["MEMBER","ADMIN"]` | ✅ |
| `MEMBER` | `["MEMBER"]` | ❌ |

**명세에 명시해 둔다** — 안 적으면 다음 세션이 `ADMIN`만 통과하는 줄 안다.

### 3-5. `@RequestPart` 전수 5지점 — D6의 근거 `[교]`

| # | 파일:라인 | 경로 | HTTP | `consumes` | 필수 파트 |
|---|---|---|---|---|---|
| 1 | `MemberController:84~85` | `/signUp` | POST | **없음** | `memberDTO` · `memberProfilePicture` |
| 2 | `MemberController:277~278` | `/members/updateProfile/{memberId}` | PUT | **없음** | `memberDTO` |
| 3 | `AnnounceController:111~112` | `/announces` | POST | `multipart/form-data` | `announceDTO` |
| 4 | `ApprovalController:97~98` | `/approvals/{approvalNo}` | PUT | **없음** | `approvalDTO` |
| 5 | `ApprovalController:113~114` | `/approvals` | POST | **없음** | `approvalDTO` |

**⚠ 예외가 2종이다** — 정정하려면 핸들러가 **2개** 필요하다.

| 상황 | 예외 |
|---|---|
| **비-multipart** 요청 (캡처 `10`) | **`MultipartException`** — 요청이 multipart로 래핑되지 않아 `@RequestPart` 해석 단계에서 발생 |
| **multipart인데 파트 누락** | **`MissingServletRequestPartException`** |

둘 다 catch-all(`GlobalExceptionHandler:91`)에 걸려 **500 + `ErrorResponse`(`C999`)** 로 나간다.
⇒ 핸들러 추가는 "무처리 → 처리"가 아니라 **"500 → 400" 전환**이며 본문 `ErrorCode`도 바뀐다.

- `consumes` 제약은 **3번 하나뿐**. 나머지 4개는 비-multipart 요청도 핸들러 매핑까지 도달한다
- **4·5는 작업 A·E가 처방한 결재 도메인**이다. 06이 건드리면 두 작업의 검증 기준선이 함께 흔들린다

### 3-6. 프론트 — 회귀는 정적 grep으로 판정하지 않는다 `[프]`

**라인 번호 (2026-08-27 실물 대조)**

```
:7~11    const headers = { 'Content-Type', Accept, Authorization: 'Bearer ' + ... }   ← :10 이 Authorization
:13~16   const header  = { 'Content-Type', Authorization: 'BEARER ' + ... }           ← 별개 객체. 혼동 주의
:65~82   callRegisterMemberAPI
  :74      try {
  :75~77     const result = await axios.post(`${API_BASE_URL}/signUp`,
             formData
           );                                            ← ★ [G3] 교체 대상은 이 3줄
  :79~81   } catch (error) { console.error(...) }        ← rethrow 없음
마운트 3콜: :86 /departmentDetails · :97 /showAllPosition · :108 /showAllMembersPage
호출부: RegisterMember.js:352 registerMember → :369 formData 조립 → :380 호출 → :382 alert → :384 navigate
```

**미확정 2건 — 기준선 화면 관측에서 확정한다** `[기]`

| # | 관측 항목 |
|---|---|
| **V1** | `:7~11`의 module-level `const headers`가 언제 `Bearer null`이 되는가. 05 보고서 §7-2가 실측했으나, 같은 `{ headers }`를 쓰는 05 화면 검증 1번은 **정상 통과**했다 — **발현 조건이 확정돼 있지 않다.** `S10`을 "항상 `Bearer null`"로 읽지 않는다. ⚠ 관측 시 `headers`(`:7`)와 `header`(`:13`)를 구분할 것 |
| **V2** | **구성원 등록 화면이 지금 뜨는가.** 마운트 3콜이 전부 module-level `{ headers }`다. **05 화면 검증 5항목에 이 화면은 없었다.** 드롭다운이 비면 `:75~77`을 고쳐도 등록이 불가능하다 |

**V2 결과가 [G3] 갈래를 정한다**

| V2 관측 | [G3] |
|---|---|
| 정상 (드롭다운 채워짐) | **`:75~77`만.** 헤더를 **호출 시점 조립** |
| 빈 화면 | **05가 남긴 별건이 06을 막고 있다.** → **사용자에게 보고 후 결정** |

> ⚠ **`callRegisterMemberAPI`가 에러를 삼킨다** (`:79~81`). catch가 rethrow하지 않아
> `RegisterMember.js:380`의 try/catch는 **발동하지 않는다.**
> ⇒ **401/403이어도 "등록하는데 성공했습니다" alert + `navigate`.**
> **화면 표시로 06 처방을 판정할 수 없다. 판정원은 네트워크 탭 상태 코드다.**
> 이 결함 자체는 **고치지 않고 등재**한다 (§9-1).

### 3-7. DB 사실 — `U3` 확정 `[정]`

`SHOW CREATE TABLE member_info` (2026-08-27 사용자 실행)

```sql
`member_id`      int          NOT NULL,   PRIMARY KEY   ← assigned. AUTO_INCREMENT 없음
`depart_no`      int          NOT NULL,   FK → department_info(depart_no)
`position_level` varchar(30)  NOT NULL,   FK → position_info(position_level)
`member_role`    varchar(10)  NOT NULL
`name` `employed_date` `address` `phone_no` `member_status` `email`   NOT NULL
`password`       varchar(100) DEFAULT NULL      ← ⚠ 엔티티는 nullable=false
`image_url`      longblob                        ← ⚠ 엔티티는 String nullable=false
`birthday` `gender`  DEFAULT NULL
```

`member_role varchar(10)`은 `@Enumerated(STRING)`이 enum **이름**(`ALL`=3자)을 저장하므로 문제없다.

**`member_info`를 참조하는 FK 전수 7건**

| 테이블 | 컬럼 | 제약 |
|---|---|---|
| `approval` | `member_id` | `approval_ibfk_1` |
| `approver` | `member_id` | `approver_ibfk_2` |
| `commute` | `member_id` | `FKiop9k0l80vo0cu5e8rpo1eiti` |
| `entered_room` | `MEMBER_ID` | `entered_room_ibfk_1` |
| `entered_room` | `RECEIVER_MEMBER` | `entered_room_ibfk_2` |
| `notice` | `MEMBER_ID` | `FK_MEMBER_ID2` |
| `proposal` | `member_id` | `proposal_ibfk_1` |

🔴 **`transferred_history`는 이 7건에 없다.** 그런데 `signUp`은 `saveHistory`(`:134`)로
**실측 1건당 반드시 1행**을 만든다. ⇒ `DELETE FROM member_info`가 **FK로 막히지 않고 통과하며,
`transferred_history` 행이 고아로 조용히 남는다.** 삭제 SQL이 명시적으로 먼저 지워야 한다.

```
transferred_history
  member_id  int / new_depart_no  int / new_position_name  varchar
  transferred_date  date / transferred_no  int AI PK
현재 17행 (2026-08-27)
```

### 3-8. 트랜잭션 경계 — 부분 실패가 가능하다 `[교]`

- `MemberController`에 `@Transactional` **0건** ⇒ `signUp`은 트랜잭션 밖에서 실행된다
- `MemberService.saveMember`(`@Transactional` `:62`)는 **반환 시점에 커밋**된다
- `TransferredHistoryService.saveHistory`(`@Transactional` `:28`)는 호출자 트랜잭션이 없으므로
  **새 트랜잭션을 연다** — `saveMember` 커밋 **이후**의 완전히 별개 트랜잭션

⇒ **`saveHistory` 자체가 실패해도 계정 행은 롤백되지 않는다.** 계정만 남고 인사발령 내역이
없는 상태가 만들어질 수 있다.

> ⚠ **지연 로딩 예외 구간은 아니다** (v1.1 정정). `Member`의 두 `@ManyToOne`은 `fetch` 미지정
> ⇒ **EAGER 기본값**이고, `spring.jpa.open-in-view` 미설정 ⇒ Boot 기본 **true**다.
> `saveHistory`가 읽는 `getDepartment()`·`getPosition()`은 이미 로드돼 있다.

**⇒ 검증 규칙 (§10-4에 반영).** 상태 코드로 부작용을 판정하지 않는다. 모든 실측 항목은
응답 코드와 무관하게 **`member_info` · `transferred_history` · `department_info` · `position_info`
4개 테이블 카운트로** 판정한다.

또한 파일 복사(`:114~115`)는 **DB보다 먼저** 일어나고 트랜잭션과 무관하다. 이후 어느 단계가
실패해도 업로드 파일은 남는다.

### 3-9. 미확정 — 기준선 파괴적 실측이 답한다 `[기]`

| # | 미확정 | 어디서 답해지는가 |
|---|---|---|
| **U1** | `modelMapper`가 `MemberDTO.departmentDTO → Member.department`를 실제로 매핑하는가 (`BeanConfig:20~31` STANDARD + `fieldMatchingEnabled` + `ambiguityIgnored`, 필드명이 다르다). 매핑이 안 되면 `department`가 null → `depart_no NOT NULL` → 500 | `11` 응답 + 4테이블 카운트 |
| **U2a·U2b** | **`cascade = PERSIST`(`Member:38~43`)가 발동하는가.** 발동 여부는 `memberRepository.save`(`MemberService:70`)가 `persist`로 가는가 `merge`로 가는가에 달려 있다 — `merge`는 `CascadeType.MERGE`만 전파한다 | **`11`·`13`의 `show-sql` 로그** (§10-4 ③) |
| **U4** | 파일 파트를 정상으로 채우면 **실제로 계정이 생성되는가.** 05는 파트를 비운 실측만 했다 — 500은 "차단"이 아니라 "거기까지 도달했다"는 뜻이다 | `11` |

> ⚠ **명세는 `U2a`/`U2b`의 결론을 쓰지 않는다.** `Member.memberId`가 assigned primitive `int`라
> `save()`의 `isNew()` 분기가 `persist`/`merge` 중 어디로 가는지에 결과가 달려 있고,
> **그 분기는 관측 대상이지 명세가 선점할 것이 아니다.**
>
> `AGENTS.md:50` · `tasks/05` D12 · `spec.md` §4 표 06 행 각주는 전부 **조건문**이다
> (*"cascade 건이 참이면 그 효과가 성립하지 않는다"*). **조건문 자체는 틀리지 않았다.**
> 바뀌는 것은 조건의 진리값뿐이고, 그건 아직 모른다. ⇒ **정정 문구는 보고서가 확정한다.**
> D11(*"번호는 완료 시점에 보고서가 확정한다"*)과 같은 규율이다.
>
> 참고 — `Department.departNo`는 `@GeneratedValue(IDENTITY)`다. 설령 cascade가 발동해도
> **입력한 `departNo` 값으로 행이 생기지 않는다.** ⇒ 관측은 **입력값 조회가 아니라
> 카운트·MAX 대비**로 한다 (§10-4 ④). `Position.positionLevel`은 `@Id String` assigned다.
>
> ⚠ **`U5`(LazyInitializationException)는 v1.1에서 폐기했다** — §3-8 참조.

---

## 4. 처방

### 공통 원칙

- **원소 1개만 지운다.** 판정문 · `catch` 블록 · `jsonResponseWrapper` · import를 건드리지 않는다
- **인가는 메서드 본문 첫 문장.** 그 앞에 어떤 문장도 두지 않는다
- 왜 3개만 남았는지 **주석을 갱신한다.** 없으면 다음 세션이 "정리 누락"으로 오해한다

### [G1] `JwtAuthorizationFilter` — `/signUp` 원소 제거

`:65` 선언을 원소 **4 → 3**으로 재작성하고, `:57~64` 주석에서 `/signUp` 행을 제거하며
**작업 06에서 닫혔다**는 사실과 그 날짜를 남긴다.

```java
List<String> roleLessList = Arrays.asList("/login", "/", "/wss/chatting");
```

`:68` 판정문은 **불변**.

### [G2] `MemberController.signUp` — ADMIN 인가 가드

`:86`(현 첫 문장) **앞**에 삽입한다.

```java
if (!isAdmin()) {
    throw new BusinessException(ErrorCode.HANDLE_ACCESS_DENIED);
}
```

**첫 문장이어야 하는 이유**: 파일 복사(`:113~119`)가 `saveMember`(`:129`)보다 **앞**이다.
한 줄이라도 뒤에 두면 차단해도 파일이 디스크에 남는다.

`BusinessException` → `GlobalExceptionHandler.handleBusinessException` →
`new ResponseEntity<>(ErrorResponse.of(errorCode), HttpStatus.valueOf(403))` ⇒ **403 / `C005`**.

> ⚠ `isAdmin()`은 이미 존재한다(`:175`). **새 메서드를 만들지 않는다.**
> `BusinessException`·`ErrorCode`는 **이미 import돼 있다**(`MemberController:6~7`) ⇒ **신규 import 0건.**

### [G3] `MemberAPICalls.js:75~77` — 헤더 추가 (조건부)

**호출 시점에 조립한다.** `:7~11`의 module-level `const headers`를 참조하지 않는다 (`S10`).

```js
const result = await axios.post(`${API_BASE_URL}/signUp`, formData, {
    headers: {
        Authorization: `Bearer ${window.localStorage.getItem('accessToken')}`,
    },
});
```

`Content-Type`은 지정하지 않는다 — `FormData`를 넘기면 axios가 boundary와 함께 자동 설정한다.
명시하면 boundary가 빠져 서버 파싱이 깨진다.

> §3-6 V2가 "빈 화면"이면 이 처방만으로는 등록이 성립하지 않는다. **사용자에게 보고하고
> 갈래를 확정한 뒤 진행한다.**

---

## 5. Scope — 수정 허용 파일

```
백엔드 (LOG-IN-Refactoring)
  final/src/main/java/com/insider/login/auth/filter/JwtAuthorizationFilter.java   [G1]
  final/src/main/java/com/insider/login/member/controller/MemberController.java   [G2]

프론트 (LOG-IN-F-Refactoring)
  src/apis/MemberAPICalls.js                                                      [G3]

도구 (검증용 · 처방 아님 · 성공 기준 9의 "파일 3개"에 포함되지 않는다)
  docs/security/tools/capture-signup-authz.ps1   ← 05 도구의 06 개조본 (§10-3)
```

**그 외 0건.** 문서는 §9.

---

## 6. ErrorCode — 신규 0건 · 사용 1건

| 항목 | 값 |
|---|---|
| 신규 정의 | **0건** |
| 사용 | **1건** — `HANDLE_ACCESS_DENIED(403, "C005", "접근 권한이 없습니다.")` |

05는 사용 0건이었다. 06은 **사용 1건**이 되므로 보고서에 그 차이를 기록한다.

---

## 7. 결정 사항

### D1. 인가 축 — **role. ADMIN 전용** (Q1)

**S2("인가 판정은 관계로만")와 충돌하지 않는다.** S2의 사정권은 *이미 존재하는 문서와 요청자
사이의 관계*다. 회원 **생성**은 판정 시점에 대상 엔티티가 아직 없어 **관계라는 축 자체가
성립하지 않는다.** ⇒ S2 위반이 아니라 **S2의 사정권 밖**이다.

근거 3개: ① 관계 축 부재 ② 작업 B가 `resetPassword`(`:149`)에 `isAdmin()`을 쓴 선례 —
같은 파일·같은 도메인 ③ `isAdmin()`이 `ADMIN`·`ALL`을 통과시킨다는 바이트코드 확정 (§3-4)

> `RegisterMember.js`의 권한 드롭다운은 **방증이지 근거가 아니다** — 그 화면에 누가 도달하는지
> 확인하지 않았다. "`role`을 서버가 덮어쓰면 안 되는 이유"로만 쓴다.

### D2. 처방 위치 — **필터 + 컨트롤러 둘 다** (Q2)

| 층 | 처방 | 결과 |
|---|---|---|
| 필터 [G1] | 원소 제거 | 무토큰 → **401** |
| 컨트롤러 [G2] | 첫 문장 가드 | 인증됨 · non-ADMIN → **403 / `C005`** |

- [G1]만: 위협 모델이 "무인증"에서 "인증된 아무나"로 옮겨갈 뿐이다
- [G2]만: 무토큰이 **401이 아니라 403**을 받는다 → **선례 `S9` 위반** (§3-3)

**상태 코드가 둘로 갈리므로 캡처 매트릭스에서 별도 항목·별도 REF로 찍는다** (`S1`).

### D3. 프론트 범위 — **`:75~77`만. 단 기준선 화면 관측 후 확정** (Q3)

module-level `const headers` 구조 수정(3파일)은 **05가 등재만 한 별건**이다. 06이 이미 3층
(필터·인가·프론트)을 안고 있어 여기서 넓히면 회귀 판정과 처방 판정이 섞인다(`S6`와 같은 축).

**§3-6 V2가 "빈 화면"이면 이 결정을 재검토한다.**

### D4. 파괴적 실측 — **한다. 총 3회** (Q4 · v1.1 정정)

**처방 후에 하면 원래 질문에 답할 수 없다.** 처방 후에는 ADMIN 토큰으로만 만들 수 있고,
그건 "무인증 계정 생성이 성립했는가"에 대한 답이 아니다.

**진짜 근거는 대조군이다.**

```
before 없음:  after 401 · 계정 0건
              ↑ 원래도 못 만들었을 가능성이 배제되지 않는다

before 있음:  before 계정 1건 → after 계정 0건
              ↑ 이제야 "06이 무언가를 닫았다"가 성립한다
```

**3회의 내역과 각각이 관측하는 층**

| 회차 | 항목 | 시점 | 관측하는 것 |
|---|---|---|---|
| 1 | `11` 무토큰 · 유효 2파트 | **기준선** (`P1`) | 위협 실증 · `U1`·`U2a/b`·`U4` |
| 2 | `13` ADMIN 토큰 · 도구 경로 | after | **백엔드** 정상 경로 비회귀 (`P2`) |
| 3 | 화면 검증 1 · 브라우저 경로 | after (동결 구간 밖) | **[G3] 포함 전 경로 · 프론트 런타임 헤더** |

**2와 3을 합치지 않는 이유** — `13`은 헤더를 도구가 조립하므로 **프론트 런타임 헤더 값을
원리적으로 관측하지 못한다.** 그게 `S10`이 확정한 결함 유형이고, [G3]가 아직 조건부인
지금 그 관측을 포기하면 05가 겪은 것을 다시 못 본다. 판정원도 다르다(도구 판정 vs 네트워크 탭).

**3이 해시 판정을 오염시키지 않는다** — 데이터 동결 구간은 §11 2단계 ~ 11단계이고
화면 검증은 12단계로 그 **밖**이다.

### D5. 실측 사번 — **prefix 단위로 고른다. 3개**

`generateNewMemberId`(`:181~191`)는 `memberId / 1000`(앞자리)을 **보존하고 뒤 3자리만 난수화**한다.
⇒ **기존에 없는 prefix면 충돌 자체가 발생하지 않아 서버가 사번을 바꾸지 않는다.**

이게 중요한 이유: `signUp`의 반환값은 `"회원 가입 성공!"` **문자열뿐이고 생성된 사번이 없다.**
서버가 사번을 바꾸면 무엇이 만들어졌는지 모른 채 지워야 한다.

| 용도 | 사번 |
|---|---|
| 기준선 `11` (무토큰) | **`209901001`** |
| after `13` (ADMIN · 도구) | **`209901002`** |
| 화면 검증 1 (ADMIN · 브라우저) | **`209901003`** ← 화면에 **손으로 입력**한다 |

> ⚠ 화면 등록은 입력값이 그대로 나간다(`RegisterMember.js:369`). `209901003`을 입력하지 않으면
> **대역 삭제(§10-4 ⑥)에 걸리지 않는다.**

착수 시 확인 — **0이어야 한다.**

```sql
SELECT COUNT(*) FROM member_info WHERE member_id DIV 1000 = 209901;
```

⚠ **`999001`·`999002` 금지** — 05 화면 검증에서 채팅 상대 목록에 실재로 떴다.

또한 이 prefix 선택이 **§10-4 ③ SQL 개수 판정을 성립시킨다** — 충돌이 없어
`do-while`(`:96~101`)이 1회만 돌고 `existsByMemberId`가 정확히 한 번 나간다.

### D6. `POST /signUp` 500 → 400 정정 — **분리** (Q5)

400 정정은 보안이 아니라 응답 규약이고, `GlobalExceptionHandler`에 **핸들러 2개**(`MultipartException`
· `MissingServletRequestPartException`)를 다는 일이 **엔드포인트 5곳의 응답 코드와 `ErrorCode`를
동시에 바꾼다**(§3-5). 그중 2개가 작업 A·E가 처방한 결재 도메인이다.

`spec.md` §4-3 등재는 **유지하되 문구를 정정한다** (§9-1).

### D7. 검증 계정 — 2종

| 계정 | role | 용도 |
|---|---|---|
| **`240501544`** (A) | `MEMBER` | 403 판정 · 정상 경로 토큰. 작업 F부터 승계 — **바꾸면 `D1` 관계가 끊긴다** |
| **`240501629`** (B) | `ADMIN` | ADMIN 정상 등록 (`13` · 화면 검증 1) |

두 토큰 모두 재발급 필요. `tokens.ps1`과 캡처 산출물은 **리포에 넣지 않는다**.

### D8. 실측에서 경로 순회를 시도하지 않는다

`fileName = memberId + "_" + file.getOriginalFilename()`(`:105`)이 검증 없이 경로에 붙는다.
정적 추론이며 확증하지 않았다. **성공하면 리포 밖에 파일이 생겨 정리 절차가 무너진다.**
실측 파일명은 평범한 이름 하나로, **크기는 수 KB로** 고정한다 (§12). 결함은 §9-1 등재.

### D9. 커밋 3분할 · 프론트 선행 (Q6)

| 순서 | 리포 | 내용 | 중간 상태 |
|---|---|---|---|
| 1 | 프론트 | `[G3]` | 백엔드가 아직 무인증 허용 ⇒ **동작 무변화. 무해** |
| 2 | 백엔드 | `[G1]`+`[G2]` 코드 | 이 시점부터 경계가 닫힌다 |
| 3 | 백엔드 | 문서 + 도구 | 05의 코드/문서 2분할 유지 |

역순(백엔드 먼저)이면 그 사이 **구성원 등록 화면이 정지**한다.

### D10. 문서 배치 — `docs/security/tasks/06-signup-authz.md`

보고서는 `docs/security/reports/06-signup-authz-report.md`.
슬러그가 `authn`이 아닌 이유: 처방이 인증+인가 둘인데 `authn`만 쓰면 05와 구분이 흐려지고,
06의 본체는 "누가 계정을 만들 수 있는가"라 인가 쪽이 무게중심이다.

### D11. 선례 번호를 예약하지 않는다

`precedents.md` §3 규칙 — 번호는 **작업 완료 시점에 보고서가 확정**한다.
**명세 단계에서 `S11`을 예약하지 않는다.**

---

## 8. 위험 목록

| # | 위험 | 대응 |
|---|---|---|
| **R1** | 🔴 **파괴적 실측 3회의 데이터가 남는다** | §10-4 대역 삭제. 4테이블 카운트 원복이 성공 기준 10 |
| **R2** | 🔴 **[G1] 누락** — [G2]만 반영되면 무토큰이 403을 받는다 | §10-3 `10`의 역방향 판정문이 잡는다 |
| **R3** | 🔴 **파괴적 실측이 정상 캡처를 오염시킨다.** 계정 생성이 `GET /showAllMembersPage` 본문을 바꾼다 | §11 순서 고정 — before는 `11` 후 삭제·`01` 재캡처, after는 **`13`을 마지막 단독**으로 |
| **R4** | 🔴 **캡처 도구가 multipart를 보내지 못한다** (PS 5.1에 `-Form` 없음) | §10-3 도구 개조 + §12 사전 시험. **4단계에서 처음 발견하면 파괴적 실측 한가운데서 도구를 고치게 된다** |
| **R5** | 🟡 **잔존 3개 원소 문자열 훼손**, 특히 `/login` | `compileJava`가 잡지 못한다. 검색 확인 + 화면 검증 3 |
| **R6** | 🟡 **구성원 등록 화면 회귀** | §3-6 V2를 기준선에서 먼저 본다 |
| **R7** | 🟡 **화면이 실패를 보여주지 않는다** | 판정원은 네트워크 탭. alert을 근거로 쓰지 않는다 |
| **R8** | 🟡 **`R11` 재발** — 재기동 후 Jackson 키 순서 변동 | 정규화 재판정. 작업 F·05 **2회 연속 발동**했다 |
| **R9** | 🟡 **`show-sql` 로그 유실** | bootRun 콘솔을 파일로 남긴다 (§12). 관측이 파괴적 실측에 걸려 있어 다시 못 찍는다 |
| **R10** | 🟡 프론트 라인 드리프트 (별도 리포) | 인용 전 재확인 (제약 #12) |

### 작업 중 멈추고 보고할 상황

- `roleLessList` 원소가 4개가 아니거나 문자열이 §0과 다르다
- `MemberController`에 이미 `signUp` 인가가 있다 / `isAdmin()` 시그니처·위치가 §3-1과 다르다
- 기준선 캡처 `10`(무토큰·파트 없음)의 before가 **500이 아니다**
- 기준선 `11`이 **계정을 만들지 못한다** (U4 거짓 → §3-2 전제 재검토. 명세 재작성)
- §10-4 ④에서 **`department_info`·`position_info` 카운트가 늘었다**
  (cascade 발동 — ③ 판정과 모순이므로 ⑦ 실행 **전에** 보고)
- §3-6 V2가 **빈 화면**
- 수정 파일이 3개를 넘는다 (도구 제외)

---

## 9. 문서 갱신

### 9-1. `spec.md`

| 절 | 갱신 |
|---|---|
| §4 표 `06` 행 | **완료**로 변경 + 커밋 해시 |
| **§4-7 신설** | 작업 06 기록 |
| §4-3 `[기] POST /signUp 500` 행 | **문구 정정** (아래) |
| §4-3 | **신규 등재 5건** (아래) |
| — | **할 일 등재** — 아래 ★ |

**§4-3 정정 문구**

> **[기]** 필수 파트 관련 예외가 **500 `C999`**로 나간다(400이어야 할 자리). 예외는 **2종**이다 —
> 비-multipart 요청은 `MultipartException`, 파트 누락은 `MissingServletRequestPartException`.
> 도달 지점 **5개** — `POST /signUp` · `PUT /members/updateProfile/{memberId}` ·
> `POST /announces` · `POST /approvals` · `PUT /approvals/{approvalNo}`.
> 조건은 **인증된 사용자 · role 무관**(파트 파싱이 핸들러 진입 전이라 인가에 도달하지 않는다).
> 전역 핸들러 **2개**가 5곳을 동시에 바꾸므로 **별건으로 분리**한다 (`tasks/06` D6).

**신규 등재 5건**

| # | 항목 | 근거 |
|---|---|---|
| 1 | **`getOriginalFilename()` 미검증 경로 조립** | `MemberController:105`. `public/img`는 브라우저가 그대로 서빙한다. 06이 무인증 쓰기는 닫지만 **파일명 검증은 닫지 않는다.** 정적 추론 — 실측하지 않았다 (D8) |
| 2 | **프론트가 등록 실패를 삼킨다** | `MemberAPICalls.js:79~81` catch가 rethrow하지 않아 `RegisterMember.js:380`이 항상 성공 alert + navigate |
| 3 | **엔티티 ↔ DDL 불일치 — `password`** | `Member:23~24` `nullable=false` vs DDL `DEFAULT NULL`. `generate-ddl: false`라 검증되지 않는다 |
| 4 | **`image_url`이 `longblob`인데 엔티티는 `String`** | `Member:44~45`. 실제 저장값은 파일명 문자열 |
| 5 | **`transferred_history`에 `member_id` FK가 없다** | FK 전수 7건에 미포함. 회원 삭제가 고아 행을 남긴다 |

**★ 할 일 등재 (문구가 아니라 작업 항목)**

> 06의 `show-sql` 관측 결과로 **`AGENTS.md:50` · `tasks/05` D12 · `spec.md` §4 표 06 행 각주**의
> 조건문(*"cascade 건이 참이면 …"*)을 해소한다. **정정 문구는 06 보고서가 확정한다** —
> 명세가 어느 갈래도 선점하지 않는다 (§3-9).

### 9-2. `AGENTS.md`

- 로드맵 `G` 행의 `<문서 커밋>`을 **`4eaab80`**으로 채우고, `:67~68` 각주 제거
- 로드맵에 `H`(작업 06) 행 추가 · 현재 진행 작업 갱신
- `:50`의 cascade 경고는 **보고서 확정 후** 해소

### 9-3. 보고서

`docs/security/reports/06-signup-authz-report.md` — 05 보고서 구조를 따른다.
**라인 드리프트 표**와 **`show-sql` 관측 결과**를 반드시 넣는다.

### 9-4. 도구

`docs/security/tools/capture-signup-authz.ps1` — 05 도구의 06 개조본. 커밋한다
(산출물·토큰은 제외).

### 9-5. `precedents.md`

**보고서가 새 선례를 확정한 경우에만** 추가한다 (D11).

---

## 10. 검증

### 10-1. 자동 검증 (Claude Code)

```powershell
cd final
.\gradlew.bat compileJava
.\gradlew.bat compileTestJava
.\gradlew.bat bootRun    # 80% EXECUTING 에서 멈춘 것처럼 보이는 게 정상. Ctrl+C
```

⚠ `compileJava`만으로는 문자열 오타를 잡지 못한다 (단계 1.5). **수동 API 확인 필수.**

### 10-2. 검색 확인 (PowerShell 5.1 · 보고서에 결과 기록)

```powershell
$F = ".\final\src\main\java\com\insider\login\auth\filter\JwtAuthorizationFilter.java"
$M = ".\final\src\main\java\com\insider\login\member\controller\MemberController.java"

# 1) roleLessList 원소가 정확히 3개인가 — 눈으로 문자열 대조
Select-String -Path $F -Pattern "roleLessList"

# 2) signUp 문자열  ★ 기대: 코드 0건 · 주석 N건
#    [G1]이 주석을 갱신하므로 총 건수가 아니라 "코드/주석" 내역으로 판정한다.
#    PS 5.1 Select-String 은 주석을 걸러내지 못한다 — 히트를 눈으로 분류해 보고서에 적는다.
Select-String -Path $F -Pattern "signUp"

# 3) 판정문 무변경
Select-String -Path $F -Pattern "contains"

# 4) 인가 가드가 signUp 본문 첫 문장인가 — 라인 번호 대조
Select-String -Path $M -Pattern "signUp|isAdmin|HANDLE_ACCESS_DENIED"

# 5) getCurrentMemberId 사용 — 기대: signUp 안에서 0건
Select-String -Path $M -Pattern "getCurrentMemberId"

# 6) @PreAuthorize — 기대 0건
Select-String -Path $M -Pattern "PreAuthorize"

# 7) 범위 이탈 — 백엔드 변경 파일은 정확히 2개여야 한다
git diff --stat
```

### 10-3. 캡처 매트릭스 15항목 (사용자 담당)

**도구**: `docs/security/tools/capture-signup-authz.ps1` — 05 개조본.

> 🔴 **05 도구를 그대로 쓸 수 없다.** `Invoke-Capture`의 `ContentType`이 `application/json`
> 고정이고, **PS 5.1 `Invoke-WebRequest`에는 `-Form`이 없다**(6.1 도입).
> `11`·`12`·`13`은 JSON blob + 바이너리 2파트가 필수다.
> ⇒ `System.Net.Http.HttpClient` + `MultipartFormDataContent`로 **전송 함수를 1개 추가**한다.
> **새 함수는 `Invoke-Capture`와 동일한 산출물을 내야 한다** — 같은 파일명 규칙, 상태 코드·
> 본문·해시 기록 형식. 형식이 다르면 `-Compare`가 세 항목을 읽지 못해 매트릭스가 갈라진다.

⚠ **기준선은 코드 수정 전에 찍는다** (`P1`). **05 기준선을 재사용하지 않는다** — 새 폴더:
`C:\temp\signup-authz\` (⚠ 리포 밖 유지)

**S0 — 정상 경로 비회귀 (`P2`. 먼저 본다) · 6항목**

| # | 요청 | 판정 |
|---|---|---|
| `01` | `GET /showAllMembersPage` (토큰 A) | 해시 동일 |
| `02` | `GET /departmentDetails` (토큰 A) | 해시 동일 |
| `03` | `GET /showAllPosition` (토큰 A) | 해시 동일 |
| `04` | `GET /approvals?fg=...&page=0` (토큰 A) | **shape** — `R11` 대상 |
| `05` | `POST /login` (A) | **shape** — 토큰 재발급 |
| `06` | `GET /members/240501544` (토큰 A) | 해시 동일 |

**S1 — 전환 (본체) · 3항목**

| # | 요청 | before | after | 판정 그룹 |
|---|---|---|---|---|
| `10` | `POST /signUp` **무토큰 · 파트 없음** | **500 / `C999`** (05 M2 재현) | **401** | `$authnNow` |
| `11` | `POST /signUp` **무토큰 · 유효 2파트 · 실재 `departNo`/`positionLevel`** 🔴 before가 파괴적 | **?** ← `U1`·`U2a/b`·`U4` | **401** (비파괴 — 필터가 파트 파싱 전에 끊는다) | `$authnNow` |
| `12` | `POST /signUp` **토큰 A(MEMBER) · 유효 2파트** | — (**after 전용**) | **403 / `C005`** · 계정 0건 | `$authzNow` |

> ★ **`10`의 역방향 판정문 (R2를 잡는다)**
> after가 **403이면 [G1] 누락**(원소를 안 지웠다), **500이면 익명 토큰 전제가 틀린 것**이다.
> 401만이 PASS다.
>
> ★ **`12`·`13`이 after 전용인 이유** — `/signUp`이 `roleLessList` 원소인 동안 필터는
> `Authorization` 헤더를 **읽기 전에** `return`한다(판정문 `:68` vs 헤더 읽기 `:73`).
> 컨트롤러도 `SecurityContext`를 읽지 않는다. ⇒ `11`·`12`·`13`의 before는 **같은 코드 경로**이고,
> 세 번 찍으면 **계정만 세 개 생긴다.** before 대응은 `11`이 대신한다 — 대조군 손실 0.

**S0-2 — ADMIN 정상 등록 · 1항목 (after 전용)**

| # | 요청 | 판정 |
|---|---|---|
| `13` | `POST /signUp` **토큰 B(ADMIN) · 유효 2파트 · 사번 `209901002`** 🔴 파괴적 | **200 · 계정 1건 생성** |

**S2 — 동결 · 5항목**

| # | 요청 | 기대 | 비고 |
|---|---|---|---|
| `20` | `GET /` | 동결 | 원소 유지 (`tasks/05` D5) |
| `21` | `POST /login` 잘못된 비밀번호 | **401** 동결 | 작업 F 처방 |
| `22` | `POST /announces` 무토큰 · 본문 없음 | **401** 동결 | 05가 415 → 401로 전환시킨 것 |
| `23` | `GET /showAllMembersPage` **무토큰** | **401** 동결 | 05 처방 · **★ REF-401** |
| `24` | `PUT /resetPassword/240501544` (토큰 A = MEMBER) | **403 / `C005`** 동결 | 작업 B 처방 · **★ REF-403** |

**판정 그룹**

```powershell
$hashSame    = '01','02','03','06'
$shapeFrozen = '04','05'
$authnNow    = '10','11'          # after 401 AND after 해시 == REF-401('23')
$authzNow    = '12'               # after 403 AND after 해시 == REF-403('24')
$frozen      = '20','21','22','23','24'
$afterOnly   = '12','13'
```

- **before 캡처 회차 = 12항목** (`01`~`06`, `10`, `20`~`24`) + 파괴적 `11` 별도
- **after 캡처 = 15항목** (단 `13`은 마지막 단독 — §11 9·10단계)
- `24`가 REF-403 역할과 작업 B 회귀 검증을 겸한다
- ⚠ **`R11`** — 해시 불일치 시 키 순서 정규화로 자동 재판정. 작업 F·05 **2회 연속 발동**했다

### 10-4. 파괴적 실측 — 4테이블 판정 · SQL 관측 · 삭제 (사용자 담당)

**상태 코드로 부작용을 판정하지 않는다** (§3-8).

```sql
-- ① 실측 전 기준값
SELECT COUNT(*) FROM member_info;                          -- N
SELECT COUNT(*) FROM transferred_history;                  -- 17
SELECT COUNT(*), MAX(depart_no)      FROM department_info; -- D , Dmax
SELECT COUNT(*), MAX(position_level) FROM position_info;   -- P , Pmax

-- ② 대역 확인 — 0이어야 한다
SELECT COUNT(*) FROM member_info WHERE member_id DIV 1000 = 209901;
--    실측에 쓸 실재값도 여기서 뽑는다 (미리 적지 않는다)
SELECT depart_no, depart_name        FROM department_info ORDER BY depart_no;
SELECT position_level, position_name FROM position_info   ORDER BY position_level;
```

**③ `show-sql` 관측 — `U2a`·`U2b`를 답한다 (추가 파괴 0회)**

`application.yml:19` `show-sql: true`가 이미 켜져 있다. `11`·`13` 각각에 대해
**bootRun 콘솔에서 `member_info` 대상 문장을 센다.**

| 경로 | INSERT 앞 `member_info` SELECT |
|---|---|
| `persist` | **정확히 1개** — `existsByMemberId`(`MemberRepository:12`) 파생 쿼리 |
| **`merge`** | **2개 이상** — 위 + 스냅샷 로드 |

**⇒ 2개 이상이면 `merge` 확정. `merge`는 `CascadeType.MERGE`만 전파하므로 `PERSIST`는 발동하지 않는다.**

> ⚠ **구분점은 구문이 아니라 컬럼 목록이다.** 파생 `exists` 쿼리는 **id 단일 컬럼**만 뽑고,
> 스냅샷 쿼리는 **전 컬럼**(`name`·`password`·`depart_no`·`position_level`…)을 뽑는다.
> 행 수 제한 절은 방언에 따라 `limit ?`(MySQL) 또는 `fetch first ? rows only`로 렌더되므로
> **그 구문으로 가르지 말 것.**
>
> ⚠ **조인을 기대하지 말 것.** 스냅샷 쿼리는 엔티티 로드가 아니다. `@ManyToOne`이 EAGER여도
> `department_info`/`position_info` **LEFT JOIN이 붙지 않는다.** "조인이 없으니 merge가 아니다"는 오판이다.
>
> ⚠ **개수 판정은 D5의 prefix 선택 덕분에 성립한다** — 충돌이 없어 `do-while`(`:96~101`)이
> 1회만 돌고 `existsByMemberId`가 정확히 한 번 나간다. 충돌하는 사번을 썼으면 판정이 무너진다.

```sql
-- ④ 실측 직후 (응답 코드와 무관하게 실행)
SELECT member_id, name, member_role, depart_no, position_level
FROM member_info WHERE member_id DIV 1000 = 209901;
SELECT * FROM transferred_history WHERE member_id DIV 1000 = 209901;
--    U2a / U2b : 입력값 조회가 아니라 ①과의 카운트·MAX 대비로 본다
SELECT COUNT(*), MAX(depart_no)      FROM department_info;
SELECT COUNT(*), MAX(position_level) FROM position_info;

-- ⑤ FK 7건 0행 확인 (추정하지 않는다 · P3)
SELECT COUNT(*) FROM approval     WHERE member_id DIV 1000 = 209901;
SELECT COUNT(*) FROM approver     WHERE member_id DIV 1000 = 209901;
SELECT COUNT(*) FROM commute      WHERE member_id DIV 1000 = 209901;
SELECT COUNT(*) FROM entered_room WHERE MEMBER_ID DIV 1000 = 209901
                                    OR RECEIVER_MEMBER DIV 1000 = 209901;
SELECT COUNT(*) FROM notice       WHERE MEMBER_ID DIV 1000 = 209901;
SELECT COUNT(*) FROM proposal     WHERE member_id DIV 1000 = 209901;

-- ⑥ 삭제 ★ transferred_history 먼저 (FK가 없어 DB가 막아주지 않는다)
DELETE FROM transferred_history WHERE member_id DIV 1000 = 209901;
DELETE FROM member_info         WHERE member_id DIV 1000 = 209901;

-- ⑦ 조건부 — ④의 department_info / position_info 카운트가 ①보다 늘었을 때만
--    (cascade 발동 = ③ 판정과 모순이므로, 실행 전에 사용자에게 보고한다 — §8 중단 사유)
DELETE FROM department_info WHERE depart_no      = <④에서 확인한 신규 값>;
DELETE FROM position_info   WHERE position_level = '<④에서 확인한 신규 값>';

-- ⑧ 원복 확인 — ①의 4개 카운트로 전부 돌아와야 한다 (성공 기준 10)
```

```powershell
# ⑨ 프론트 리포 잔여 파일 — 실측 1회당 1개 생긴다 (총 3개)
Get-ChildItem C:\env\GitHub\INSIDER\LOG-IN-F-Refactoring\public\img -Filter "209901*"
Remove-Item    C:\env\GitHub\INSIDER\LOG-IN-F-Refactoring\public\img\209901*
```

> ⚠ `transferred_no`가 AUTO_INCREMENT라 삭제해도 번호에 구멍이 남는다.
> **무해하며 되돌리지 않는다** (카운터 리셋은 범위 밖).
> ⚠ **DB 작업은 사용자 담당.** 작업 A의 `DELETE` 실측(`spec.md` §3-4)이 선례다.

### 10-5. 화면 검증 (사용자 담당 · 성공 기준 11)

| # | 항목 | 시점 |
|---|---|---|
| **V2** | **구성원 등록 화면이 뜨는가** — 부서·직급 드롭다운이 채워지는가 | ★ **기준선** (처방 전). D3 갈래를 정한다 |
| 1 | ADMIN(B) 로그인 → 구성원 등록 성공 · **사번 `209901003` 손입력** 🔴 파괴적 | after (12단계) |
| 2 | MEMBER(A) 로그인 → 등록 시도 → **네트워크 탭 403** | after |
| 3 | 로그인 정상 + 틀린 비밀번호 alert (`R5` 해소 — `/login` 원소 온전성) | after |
| 4 | 결재 목록·상세·첨부 다운로드 (작업 E 비회귀) | after |

> ⚠ **1·2의 판정원은 네트워크 탭이다.** 프론트가 실패를 삼켜 화면은 항상 성공 alert을
> 띄운다 (§3-6 ⚠). **alert을 근거로 쓰지 않는다.**
> ⚠ **1은 `Authorization` 요청 헤더의 실제 값도 함께 기록한다** — `Bearer null`인지
> 유효 토큰인지. 이것이 `13`이 관측할 수 없는 층이며 `S10`의 자리다.
> ⚠ **캡처 도구가 잡지 못하는 것** — WebSocket 핸드셰이크 · **프론트 런타임 헤더 값**.

---

## 11. 실행 순서

```
 1. 착수 전 체크 (§12)  ← ★ multipart 도구 시험이 여기에 있다
 2. 기준선 캡처 — 비파괴 12항목 (01~06, 10, 20~24)          ← 코드 수정 전 (P1)
    ⚠ 이것이 끝나기 전에는 착수하지 않는다
 3. 기준선 화면 관측 V2                                      ← D3 갈래 확정
 4. 기준선 파괴적 실측 11 (무토큰 · 209901001)  🔴 1회째
    → §10-4 ③ show-sql 관측 → ④ ⑤ → ⑥ 삭제 → ⑧ 원복 확인
 5. 01 재캡처 — 2단계 값과 동일한지 확인 (R3)
    ─────────────── 여기까지가 before. 이후 코드 수정 ───────────────
 6. [G3] 프론트 수정 → 커밋 (무해한 중간 상태)
 7. [G1] + [G2] 백엔드 수정 → compileJava · compileTestJava · bootRun
 8. 검색 확인 7종 (§10-2)
 9. after 캡처 — 14항목 (01~06, 10, 11, 12, 20~24)
    ⚠ 13은 여기 넣지 않는다 — 계정이 생기면 01 해시가 오염돼 FAIL 오판이 된다
10. after 캡처 13 단독 (ADMIN · 209901002)  🔴 2회째 → §10-4 ③④⑤
11. -Compare 판정
    ─────────────── 데이터 동결 구간 종료 ───────────────
12. 화면 검증 4항목 (§10-5). 항목 1이 🔴 3회째 (209901003)
13. §10-4 ⑥ 대역 삭제 → ⑧ 4테이블 원복 확인 → ⑨ 파일 정리
    ⚠ 209901002 · 209901003 이 여기서 함께 지워진다 (성공 기준 10)
14. 백엔드 코드 커밋 → 문서·도구 갱신 (§9) → 문서 커밋
15. 두 리포 push
```

### ★ 데이터 동결 구간 — 2단계 시작 ~ 11단계 `-Compare` 종료

이 구간에서 **`member_info` · `announce` · `approval`을 바꾸는 행위를 하지 않는다.**
계정 생성이 `GET /showAllMembersPage` 본문을 바꿔 `01` 해시 판정이 깨진다 (`R3`).

- 4단계 파괴적 실측은 **삭제까지 마치고 5단계에서 원복을 확인**해야 구간이 유지된다
- 10단계 `13`은 `01`이 이미 찍힌 **뒤**이므로 구간 안이지만 무해하다
- 12단계 화면 검증은 `-Compare`가 끝난 **뒤**다 — 05가 같은 이유로 순서를 지켰다

---

## 12. 착수 전 체크

**Claude Code**

- [ ] `git status` 클린 · `git log --oneline -2` 의 두 번째가 `4eaab80` · `origin/main` 동기화
- [ ] `roleLessList` 원소가 **4개**이고 문자열이 §0과 일치
- [ ] `MemberController`에 `signUp` 인가 **0건**, `isAdmin()`이 `:175`에 존재
- [ ] `JwtAuthorizationFilter` · `MemberController` · `Member` 라인 번호가 §3-1과 일치
- [ ] 환경변수 3종 (`JWT_KEY` · `DB_USERNAME` · `DB_PASSWORD`)

**사용자**

- [ ] 🔴 **multipart 전송 함수 시험 1회 통과** — 그리고 그 산출물이 **`-Compare`에 읽히는지까지**
      확인 (§10-3). 4단계에서 처음 발견하면 파괴적 실측 한가운데서 도구를 고치게 된다
- [ ] 🔴 **실측 이미지 고정** — 평범한 파일명, **수 KB**.
      `application.yml:13~15`가 `max-file-size: 10MB`를 허용하지만, 무토큰 `11`은 서버가 본문을
      읽기 전에 401을 커밋하므로 Tomcat `maxSwallowSize`(기본 2MB)를 넘으면 커넥션이 끊겨
      깨끗한 401 대신 오류를 받는다 (*정적 근거 · 미실측*)
- [ ] 🔴 **bootRun 콘솔을 파일로 남긴다** — `show-sql` 관측이 파괴적 실측에 걸려 있어 다시 못 찍는다
      ```powershell
      cd final
      .\gradlew.bat bootRun | Tee-Object -FilePath C:\temp\signup-authz\bootrun-before.log
      ```
- [ ] 토큰 A·B 재발급 완료 (D7)
- [ ] 사번 대역 확인 — `SELECT COUNT(*) ... DIV 1000 = 209901` → **0**
- [ ] 실재 `departNo`·`positionLevel` 값 확보 (§10-4 ②)
- [ ] 기준선 캡처 폴더가 **새 폴더**인가 (`C:\temp\signup-authz\`)
- [ ] `precedents.md` 정독 — 특히 `S1` · `S2` · `S6` · `S9` · **`S10`**

---

## 13. 작업 원칙 리마인더

- **Surgical Changes.** task가 요구하는 것만. "온 김에" 개선 금지
- **명세에 없는 요구사항을 창작하지 않는다**
- **단계 경계를 존중한다.** §2 "범위 밖" 11건은 하드 가드다
- **범위 밖 개선이 눈에 띄면 고치지 말고 보고한다.** 보고서 §"범위 밖 발견"에 등재
- **의문점·예상 못 한 상황은 추측하지 말고 즉시 사용자에게 보고**
- **§8 "멈추고 보고할 상황"에 해당하면 즉시 중단**
- 모든 명령은 **Windows / PowerShell 5.1**. `&&` 없음, `Select-String`에 `-Recurse` 없음
- 모든 `.md`는 **UTF-8**. `.ps1`은 한글 포함 시 **BOM 필요**
- **단계 완료 = 커밋 + 푸시까지**
