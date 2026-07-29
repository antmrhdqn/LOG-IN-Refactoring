# 단계 5: 결재번호 생성기 분리 — 작업 보고서

> 작업일: 2026-07-29
> 실행: Claude Code (Sonnet 5)
> 명세: `docs/refactoring/approval/tasks/05-generator.md` (§8 D1~D4 확정본)
> 선행: 단계 1·1.5·2·3·4 완료·커밋·푸시 (`89d27b8`(코드)·`c30ea54`(문서)·`+.gitignore`)

---

## 1. 변경 파일

| 파일 | 구분 | 라인 수 |
|---|---|---|
| `approval/service/generator/ApprovalNoGenerator.java` | **신규** | 61 |
| `approval/controller/ApprovalController.java` | 수정 | +23 / -54 (그중 이번 diff 반영분 44라인 구간) |
| `approval/service/ApprovalService.java` | 수정 | +23 / -54 (구간) |
| `approval/service/file/ApprovalFileService.java` | 수정 | +9 / -9 (구간) |

### git diff --stat (신규 파일 제외, 추적 중인 3개 파일)

```
 .../approval/controller/ApprovalController.java    | 44 +++++-----------------
 .../login/approval/service/ApprovalService.java    | 24 ++++--------
 .../approval/service/file/ApprovalFileService.java |  9 +++--
 3 files changed, 23 insertions(+), 54 deletions(-)
```

`ErrorCode.java` 미수정(D3 확정). `ApprovalRepository.java`(백킹 쿼리) 무변경. `Approval.java` 무변경.
`src/test/**` 무변경.

---

## 2. 신규 `ApprovalNoGenerator` (@Component)

결재번호·결재자번호·참조자번호·첨부파일번호 채번 규칙(연도-폼번호-순번, 하위 순번 포맷)을 **이 컴포넌트만** 안다.
생성자 주입(`ApprovalRepository` 1개). 상속 없음, 상태 없음(stateless).

| 메서드 | 책임 | 흡수한 원문 |
|---|---|---|
| `String nextApprovalNo(int year, String formNo)` | (year, formNo) 시리즈 다음 결재번호 `YYYY-{formNo}{%05d}` | `ApprovalController.insertApproval`의 채번 블록(구 L226–248: `findLastApprovalNo` 조회 → `split("-")` → `replaceAll("\\D","")` → `+1` → `%05d`, 널이면 순번 1) + `ApprovalService.selectApprovalNo`(구 L744–757, 마지막 번호 조회 위임) |
| `String senderApproverNo(String approvalNo)` | `{approvalNo}_apr000` | `ApprovalService.insertApproval`(구 L134) / `updateApproval`(구 L379)의 `concat("_apr000")` |
| `String approverNo(String approvalNo, int order)` | `{approvalNo}_apr{%03d}` | `ApprovalController`의 4개 결재자 루프 인라인 조립 |
| `String referencerNo(String approvalNo, int order)` | `{approvalNo}_ref{%03d}` | `ApprovalController`의 4개 참조자 루프 인라인 조립 |
| `String fileNo(String approvalNo, int index)` | `{approvalNo}_f{%03d}` | `ApprovalFileService.store()`(구 L79–80, Stage 4 임시 배치분) |

**[K] 방어물 0건**: 락·재시도·`existsById` 선검사 등 어떤 메커니즘도 추가하지 않았다(D1-(ii) 확정 준수).
`nextApprovalNo`는 조회→파싱→증분→포맷을 원문 그대로 순서·로직 보존하며 이관했다.

---

## 3. 변경 지점 요약

### ApprovalController.java

| 지점 (변경 전 라인) | 변경 |
|---|---|
| 생성자 | `ApprovalNoGenerator` 주입 추가 |
| `insertApproval` 구 L211–252 (`YearFormNo` 조립 → `selectApprovalNo` → split/replaceAll/+1/%05d → `setApprovalNo`) | `approvalNoGenerator.nextApprovalNo(Year, formNo)` 호출로 축약. "임시저장→formNo=ims" 분기(D2)는 그대로 Controller에 유지. 중간 `log.info`/`System.out.println` 디버그 출력은 계산 로직 자체가 사라지며 함께 제거됨(값 자체는 더 이상 존재하지 않음) |
| `insertApproval` 구 L275–284 결재자 루프 (L279 `approverNo` 조립) | `approvalNoGenerator.approverNo(approvalNo, i + 1)` |
| `insertApproval` 구 L286–294 참조자 루프 (L290 `refNo` 조립) | `approvalNoGenerator.referencerNo(approvalNo, i + 1)` |
| `updateApprovalTemp` 구 L155–163 결재자 루프 (L159 `approverNo` 조립) | `approvalNoGenerator.approverNo(approvalDTO.getApprovalNo(), i + 1)` |
| `updateApprovalTemp` 구 L166–174 참조자 루프 (L170 `refNo` 조립) | `approvalNoGenerator.referencerNo(approvalDTO.getApprovalNo(), i + 1)` |

루프 구조·다른 필드 설정(`setApprovalNo`/`setApproverStatus`/`setApproverOrder`/`setRefOrder`)은 전부 원문 그대로.
루프 자체의 서비스 이관은 Stage 6 소관(범위 외).

### ApprovalService.java

| 지점 (변경 전 라인) | 변경 |
|---|---|
| 생성자 | `ApprovalNoGenerator` 주입 추가 |
| `insertApproval` 구 L134 (D4 콜사이트 1/2) | `approvalDTO.getApprovalNo().concat("_apr000")` → `approvalNoGenerator.senderApproverNo(approvalDTO.getApprovalNo())` |
| `updateApproval` 구 L379 (D4 콜사이트 2/2) | 동일 치환. **삭제-후-재생성([C]) 구조·순서는 원문 그대로**(문자열 조립부만 교체, 재정렬 없음) |
| `selectApprovalNo` (구 L744–757) | **메서드 제거** — 유일 호출처가 Generator로 흡수됨. `findLastApprovalNo`(Repository)는 존치 |

### ApprovalFileService.java

| 지점 (변경 전 라인) | 변경 |
|---|---|
| 생성자 | `ApprovalNoGenerator` 주입 추가 |
| `store()` 구 L79–80 | `// TODO: Stage 5 ApprovalNoGenerator 로 이관` 주석 제거, `String fileNo = approvalNo + "_f" + String.format("%03d", (i + 1));` → `String fileNo = approvalNoGenerator.fileNo(approvalNo, i + 1);` |

---

## 4. [K] 잔여 · Stage 6 이월 항목

- **[K] 결재번호 동시성 (read-modify-write 경쟁)**: 이번 단계에서 해결하지 않음(D1=(ii) 확정). "마지막 조회 → +1 →
  포맷 → 저장"이 여전히 원자적이지 않다. 두 기안이 동시에 같은 `lastApprovalNo`를 읽으면 같은 다음 번호를 만들 수 있다.
  Stage 5의 [K] 기여는 채번 로직을 `ApprovalNoGenerator` 한 지점으로 모은 것뿐이다.
- **[K-후속 — Stage 6 인계]** `approvalNo`는 PK이므로 진짜 중복 행은 DB가 막지만, `approvalRepository.save(Approval)`가
  assigned String `@Id` + `@Version` 부재 조건에서 `merge()`로 흐를 경우 충돌이 예외가 아니라 **기존 행 UPDATE**로
  표면화될 수 있다(뒤 기안자가 앞 기안자 결재를 조용히 덮어쓰는 경로). Stage 6가 [F](save→persist/dirty checking)
  전환 시 이 merge 여부를 실측하고, 재시도-on-예외가 실효화되는지 확인해야 한다.
- **[K-부수 관찰 — 범위 밖, 기록만]** `findLastApprovalNo`의 백킹 쿼리가 `LIKE %:yearFormNo%`(선행 와일드카드)라
  인덱스 미사용 풀스캔이며, 폼번호가 접두어 관계(예: `A01` vs `A011`)면 시리즈 간 교차 매칭 여지가 있다. 이번 단계에서
  쿼리 의미를 바꾸지 않았으므로(규칙 보존) 그대로 남아 있다.

---

## 5. 빌드 · 부팅 결과

```
> cd final; .\gradlew.bat compileJava
BUILD SUCCESSFUL in 17s
1 actionable task: 1 executed
```

```
> cd final; .\gradlew.bat bootRun
... com.insider.login.Application : Started Application in 10.022 seconds (process running for 10.639)
```

`ApprovalNoGenerator` 빈이 `ApprovalController`·`ApprovalService`·`ApprovalFileService` 세 곳 모두에
생성자 주입으로 정상 등록됨(미등록 시 기동 실패하므로 부팅 성공이 곧 확인). 기동 후 종료.

### grep 확인 (Success Criteria 2·3·4·7)

`ApprovalController.java` / `ApprovalService.java` / `ApprovalFileService.java` 대상,
패턴 `"_apr"` / `"_ref"` / `"_f"` / `"_apr000"` 조립, `"%05d"` 조립, `selectApprovalNo` 호출:

```
→ 3개 파일 모두 0건.
```

`@Version` / `isNew(` 패턴도 approval 패키지 전체 0건 — write 경로(save 방식·tx·`@Version`) 불가침 확인.

---

## 6. 📋 수동 검증 체크리스트 (사용자 담당 — 05-generator.md §7 원문)

`compileJava`·`bootRun`만으론 채번 무성 실패(형식 어긋남·동시성)를 못 잡는다(단계 1.5 교훈). 아래를 Postman/cURL로:

- [ ] 1. **일반 기안(첨부 2개, 결재자 2·참조자 1)** → 200. 응답·DB에서 `approvalNo=YYYY-{form}00001`류,
      `_apr000`(기안자)·`_apr001`·`_apr002`, `_ref001`, `_f001`·`_f002`가 **이전과 동일 형식**인지 대조.
- [ ] 2. **연속 기안(같은 폼 2건)** → 순번이 `…00001` → `…00002`로 정확히 +1 증가.
- [ ] 3. **임시저장 기안** → `formNo=ims` 반영된 `YYYY-ims{5}` 형식.
- [ ] 4. **재임시저장(PUT /approvals/{approvalNo})** → approvalNo 불변(신규 생성 안 함), 결재자/참조자 번호 재구성 형식 동일.
- [ ] 5. **첫 건(해당 연도+폼 최초 기안)** → 순번 1로 시작.
- [ ] 6. **[K] 비회귀 확인**: 번호 형식·순번 증분이 Stage 4와 동일하고, 방어물(락/재시도)이 추가되지 않았음을
      코드로 확인(§5 grep 결과로 이미 확인됨). (선택) 같은 폼 동시 POST 2건 — 개선 기대하지 않음, 현행과 동일한
      경쟁 동작 확인용.
- [ ] 7. 무첨부 기안 / 상세·목록 조회 / 회수 / 결재처리 / 삭제 → 회귀 없음.

---

## 7. 범위 외 — 손대지 않은 항목 (원문 유지 확인)

- 결재자/참조자 DTO 조립 루프 자체의 서비스 이관(얇은 컨트롤러) → Stage 6
- `updateApproval`의 삭제-후-재생성 구조([C]), `save()` merge/persist 전환·`@Version`([F]) → Stage 6
- `findLastApprovalNo`의 LIKE/정렬 의미([K-부수 관찰]) → 변경 없음
- `ErrorCode.java`(D3), 기안자 Approver 자동등록 도메인 분리([E], 스키마 변경 수반) → 포맷 문자열만 이동, 관계 불변
- 회수 인증([A-확장])·이모지 로그([J]) → Stage 7 / `src/test/**` → 무변경
