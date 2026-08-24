# ============================================================================
#  LOG-IN 보안 작업 05 — 인증 경계 정상화(roleLessList 원소 전수 판정) : 기준선 / 검증 캡처
#  PowerShell 5.1 전용
#
#  작업 04 의 capture-auth-status.ps1 개조본. 판정 축이 하나 늘었다.
#    작업 E : "해시 동일(불변)" / "404 전환"
#    작업 04: "해시 동일(불변)" / "해시 동일 + 200→401" / "완전 동결"
#    작업 05: 위에 더해 → "본문이 통째로 바뀐다. after 가 REF 와 같은가"  ← $authnNow
#
#  ★ 작업 04 와 결정적으로 다른 점
#    04 는 본문을 건드리지 않는 처방이라 "해시 동일 + 상태 전환"으로 끝났다(선례 S6).
#    05 는 무인증 통과 경로를 닫는 처방이라 그 경로의 본문이 정상 응답 → 인증 실패 JSON 으로
#    통째로 바뀐다. 그래서 기준선 해시가 아니라 **같은 실행(after) 안의 REF 해시**와 대조한다.
#    REF = 캡처 19 (무토큰 결재 상세). 필터의 같은 catch 블록을 통과했다는 증명이 판정식이다.
#
#  사용법
#    1) tokens.ps1 을 먼저 만든다 (아래 "준비" 참조). 리포 밖에 둔다.
#    2) 코드 수정 전 :  .\capture-authn-boundary.ps1 -Phase baseline
#       결정성 확인   :  .\capture-authn-boundary.ps1 -Phase baseline2
#       (⚠ 재기동 후 baseline2 를 한 번 더 돌릴 것 — R11)
#    3) 처방 후      :  .\capture-authn-boundary.ps1 -Phase after
#    4) 판정         :  .\capture-authn-boundary.ps1 -Compare
#
#  ⚠ 데이터 동결 구간 : 1단계 시작 ~ 4단계 종료. 공지·회원·결재 데이터를 건드리지 말 것.
#     화면 검증의 공지 등록 테스트는 -Compare 가 끝난 뒤에 한다 (명세 §11).
#
#  ⚠ 저장물에 사원 이름·부서·직급 PII 와 유효 토큰이 들어간다. 리포 안으로 옮기지 말 것.
# ============================================================================

[CmdletBinding()]
param(
    [ValidateSet('baseline','baseline2','after')]
    [string]$Phase = 'baseline',

    [switch]$Compare,

    [string]$Root = 'C:\temp\authn-boundary',

    # 검증용 공지 번호. 자격증명이 아니라 명세 D13 이 고정한 값이라 여기에 둔다.
    [int]$AncNo = 26
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# 준비 : C:\temp\authn-boundary\tokens.ps1 을 아래 내용으로 만든다
#        (토큰·비밀번호는 자격증명이다. 이 스크립트에 직접 적지 말 것)
#
#   $BaseUrl = 'http://localhost:8080'
#
#   # ⚠ A 는 작업 F 와 **같은 계정**이어야 한다. 계정이 바뀌면 D1 과의 관계가 끊겨
#   #    06(정상 경로)이 404 가 되고, 19(REF)는 무토큰이라 401 그대로여서 알아채지 못한다.
#   $Token = @{
#       A = '<240501544 정상 토큰 — 새로 발급>'
#   }
#
#   $Doc = @{ D1 = '2026-non00003' }      # 작업 F 값 승계. A 가 기안했거나 결재선에 있어야 한다
#
#   $Member = @{ A = '240501544' }        # A 토큰의 주인 사번. 04·15·16·17 이 쓴다
#
#   $Login = @{
#       okId    = '240501544'             # A. 재직 상태여야 한다
#       okPw    = '<변경된 비밀번호>'
#       badPw   = 'wrong-password-0000'
#   }
#
#   ※ 작업 04 의 EXPIRED · FORGED · $File · $Dormant 는 이번 매트릭스에서 쓰지 않는다.
#     남겨 두어도 무해하다.
# ---------------------------------------------------------------------------

$tokenFile = Join-Path $Root 'tokens.ps1'
if (-not (Test-Path $tokenFile)) {
    throw "tokens.ps1 이 없다: $tokenFile  (스크립트 상단 주석 참조)"
}
. $tokenFile

$OutDir = Join-Path $Root $Phase


# ===========================================================================
#  공통 함수  (작업 04 원본과 동일 — 손대지 않았다)
# ===========================================================================

function Write-Utf8NoBom {
    param([string]$Path, [string]$Text)
    [System.IO.File]::WriteAllText($Path, $Text, (New-Object System.Text.UTF8Encoding($false)))
}

function Get-Sha256 {
    param([byte[]]$Bytes)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { ($sha.ComputeHash($Bytes) | ForEach-Object { $_.ToString('x2') }) -join '' }
    finally { $sha.Dispose() }
}

# 응답 JSON 의 "모양"을 숫자 지문으로 만든다.
# 값이 아니라 키 개수 / 배열 길이만 뽑으므로, 값이 매번 바뀌는 항목(02·03 의 hits,
# 09 의 토큰)을 이것으로 판정한다.
function Get-JsonShape {
    param($Node, [string]$Path = '$')
    $out = @()
    if ($null -eq $Node) { return @("$Path=null") }

    if ($Node -is [System.Object[]]) {
        $out += "$Path[]=$($Node.Count)"
        if ($Node.Count -gt 0) { $out += Get-JsonShape -Node $Node[0] -Path "$Path[0]" }
        return $out
    }
    if ($Node -is [System.Management.Automation.PSCustomObject]) {
        $names = @($Node.PSObject.Properties.Name | Sort-Object)
        $out += "$Path{}=$($names.Count):$($names -join ',')"
        foreach ($n in $names) {
            $out += Get-JsonShape -Node $Node.$n -Path "$Path.$n"
        }
        return $out
    }
    return @("$Path=$($Node.GetType().Name)")
}

# JSON 의 키 순서를 재귀적으로 정렬해 정규화한다 (R11).
# ⚠ 인증 실패 본문은 json-simple 의 JSONObject(HashMap 기반)라 직렬화 순서가 보장되지 않는다.
#    작업 F 에서 실제로 발동했다 — 예상은 인증 실패 본문이었으나 정상 응답 쪽이 흔들렸다.
function Get-CanonicalJson {
    param($Node)
    if ($null -eq $Node) { return 'null' }
    if ($Node -is [System.Object[]]) {
        return '[' + (($Node | ForEach-Object { Get-CanonicalJson -Node $_ }) -join ',') + ']'
    }
    if ($Node -is [System.Management.Automation.PSCustomObject]) {
        $parts = @()
        foreach ($n in @($Node.PSObject.Properties.Name | Sort-Object)) {
            $parts += ('"' + $n + '":' + (Get-CanonicalJson -Node $Node.$n))
        }
        return '{' + ($parts -join ',') + '}'
    }
    if ($Node -is [string]) { return '"' + $Node + '"' }
    return "$Node"
}

function Test-SameIgnoringKeyOrder {
    param([string]$PathA, [string]$PathB)
    try {
        $ja = (Get-Content $PathA -Raw -Encoding UTF8) | ConvertFrom-Json
        $jb = (Get-Content $PathB -Raw -Encoding UTF8) | ConvertFrom-Json
        return ((Get-CanonicalJson -Node $ja) -ceq (Get-CanonicalJson -Node $jb))
    } catch { return $false }
}

# 비-2xx 도 본문을 읽어야 한다.
# 이번 작업은 기준선에서 이미 401·415·500 이 섞여 나오고,
# PS 5.1 의 Invoke-WebRequest 는 그때 예외를 던진다.
function Invoke-Capture {
    param(
        [string]$Id,
        [string]$Url,
        [string]$Method = 'GET',
        [string]$Body,                 # POST /login 용 (JSON 문자열)
        [string]$TokenKey,             # $Token 해시테이블의 키. "Bearer <token>" 으로 조립된다
        [string]$RawHeader,
        [switch]$Binary
    )

    $headers = @{}
    if ($TokenKey)   { $headers['Authorization'] = "Bearer $($Token[$TokenKey])" }
    if ($RawHeader)  { $headers['Authorization'] = $RawHeader }

    $status  = $null
    $bytes   = $null
    $hdr     = @{}

    $args = @{
        Uri             = $Url
        Method          = $Method
        Headers         = $headers
        UseBasicParsing = $true
        ErrorAction     = 'Stop'
    }
    if ($PSBoundParameters.ContainsKey('Body')) {
        # ⚠ 문자열로 넘기면 PS 5.1 이 ISO-8859-1 로 인코딩한다. 바이트로 직접 넘긴다.
        $args['Body']        = [System.Text.Encoding]::UTF8.GetBytes($Body)
        $args['ContentType'] = 'application/json'
    }
    # ⚠ 12·16·17·20 은 Body 를 넘기지 않는다. 본문 없는 POST/PUT 이 이번 작업의 판정 조건이다
    #    (12 는 consumes=multipart 제약에 415, 20 은 @RequestPart 누락으로 500 — 명세 M2).

    try {
        $resp = Invoke-WebRequest @args
        $status = [int]$resp.StatusCode
        $bytes = $resp.RawContentStream.ToArray()
        foreach ($k in 'Content-Type','Content-Length','Content-Disposition') {
            if ($resp.Headers.ContainsKey($k)) { $hdr[$k] = $resp.Headers[$k] }
        }
    }
    catch [System.Net.WebException] {
        $r = $_.Exception.Response
        if (-not $r) { throw }

        $status = [int]$r.StatusCode
        $ms = New-Object System.IO.MemoryStream
        try {
            $r.GetResponseStream().CopyTo($ms)
            $bytes = $ms.ToArray()
        } finally { $ms.Dispose() }

        foreach ($k in 'Content-Type','Content-Length','Content-Disposition') {
            $v = $r.Headers[$k]
            if ($v) { $hdr[$k] = $v }
        }
    }

    if ($null -eq $bytes) { $bytes = New-Object byte[] 0 }

    $head = if ($bytes.Length -gt 0) {
                [System.Text.Encoding]::UTF8.GetString($bytes, 0, [Math]::Min(512, $bytes.Length))
            } else { '' }
    $text = if ($Binary) { $head } else { [System.Text.Encoding]::UTF8.GetString($bytes) }

    if ($Binary) {
        [System.IO.File]::WriteAllBytes((Join-Path $OutDir "$Id.bin"), $bytes)
    } else {
        Write-Utf8NoBom -Path (Join-Path $OutDir "$Id.json") -Text $text
    }

    if (-not $Binary) {
        $shape = @()
        try   { $shape = Get-JsonShape -Node ($text | ConvertFrom-Json) }
        catch { $shape = @('PARSE_FAILED') }
        Write-Utf8NoBom -Path (Join-Path $OutDir "$Id.shape.txt") -Text ($shape -join "`r`n")
    }

    [pscustomobject]@{
        id       = $Id
        status   = $status
        bytes    = $bytes.Length
        sha256   = Get-Sha256 -Bytes $bytes
        ctype    = $hdr['Content-Type']
        clen     = $hdr['Content-Length']
        cdisp    = $hdr['Content-Disposition']
        url      = $Url
        method   = $Method
        magic    = if ($bytes.Length -ge 4) {
                       (($bytes[0..3]) | ForEach-Object { $_.ToString('x2') }) -join ''
                   } else { '' }
        authFail = ($head -match '"status"\s*:\s*401')
        loginOk  = ($head -match '로그인 성공')
    }
}


# ===========================================================================
#  캡처 매트릭스  (명세 §10)
#    S0 정상        (01~09)  ← 차단·전환 검증보다 먼저 본다 (P2)
#    S1 전환        (10~14)  ← 이번 작업이 바꾸는 것
#    S2 불변·가설검증(15~19)  ← ★ before 가 전부 401 이어야 §3-1 전제가 실증된다
#    S3 동결        (20~22)  ← 바뀌면 범위 이탈 신호
#
#  ⚠ 이 문서의 S0~S3 은 **캡처 그룹** 이름이다.
#     precedents.md 의 S1~S9 는 **선례** 번호다. 서로 다른 체계다.
# ===========================================================================

function Get-Matrix {

    $detail  = { param($no) "$BaseUrl/approvals/$no" }
    $list    = { param($fg) "$BaseUrl/approvals?fg=$fg&page=0&title=&direction=DESC" }
    $ancList = "$BaseUrl/announces?page=0&size=10&sort=ancNo&direction=DESC"
    $ancOne  = "$BaseUrl/announces/$AncNo"
    $loginUrl = "$BaseUrl/login"
    $loginBody = {
        param($id, $pw)
        "{""memberId"":$id,""password"":""$pw""}"
    }
    $mid = $Member.A

    $m = @(
        # --- S0 정상 경로 (처방 후 불변이어야 한다) ---
        @{ id='01'; url="$BaseUrl/showAllMembersPage"; tok='A'; note='구성원 목록 · 토큰' }
        @{ id='02'; url=$ancList; tok='A'; note='공지 목록 · 토큰   ← ★M1 shape 판정 (hits)' }
        @{ id='03'; url=$ancOne;  tok='A'; note="공지 상세 · 토큰 · ancNo=$AncNo  ← ★M1 shape 판정 (hits)" }
        @{ id='04'; url="$BaseUrl/members/$mid"; tok='A'; note='회원 상세 · 토큰' }
        @{ id='05'; url=(& $list 'given');       tok='A'; note='결재 목록 · 토큰' }
        @{ id='06'; url=(& $detail $Doc.D1);     tok='A'; note='결재 상세 · 토큰 · D1' }
        @{ id='07'; url="$BaseUrl/departments";  tok='A'; note='부서 목록 · 토큰' }
        @{ id='08'; url="$BaseUrl/showAllPosition"; tok='A'; note='직급 목록 · 토큰' }
        @{ id='09'; url=$loginUrl; method='POST'; body=(& $loginBody $Login.okId $Login.okPw)
           note='로그인 성공  ← 해시 비교 불가. shape + 메시지로 판정' }

        # --- S1 전환 : 무인증 통과 → 401 (이번 작업의 본체) ---
        @{ id='10'; url="$BaseUrl/showAllMembersPage"; note='원소 /showAllMembersPage → 401' }
        @{ id='11'; url=$ancList;                       note='원소 /announces (GET)      → 401' }
        @{ id='12'; url="$BaseUrl/announces"; method='POST'
           note='원소 /announces (POST · 본문없음) before=415 → 401' }
        @{ id='13'; url="$BaseUrl/registDepart";   note='원소 /registDepart (핸들러 없음)   → 401' }
        @{ id='14'; url="$BaseUrl/registPosition"; note='원소 /registPosition (핸들러 없음) → 401' }

        # --- S2 불변 ★가설 검증 : 자리표시자·콤마 원소가 죽어 있었는가 ---
        #     before 가 전부 401 이어야 §3-1(String.equals) 전제가 실증된다.
        #     하나라도 200 이면 즉시 중단하고 명세를 다시 쓴다.
        @{ id='15'; url="$BaseUrl/members/$mid"; note='자리표시자 원소 · 무토큰 → 401 (before 도 401이어야)' }
        @{ id='16'; url="$BaseUrl/members/updateProfile/$mid"; method='PUT'
           note='자리표시자 원소 · 무토큰 · 본문없음 → 401 (before 도 401이어야)' }
        @{ id='17'; url="$BaseUrl/resetPassword/$mid"; method='PUT'
           note='경로명 오기 원소 · 무토큰 · 본문없음 → 401 (before 도 401이어야)' }
        @{ id='18'; url=(& $list 'given'); note='콤마 원소 · 무토큰 → 401 (before 도 401이어야)' }
        @{ id='19'; url=(& $detail $Doc.D1); note='★ REF — 무토큰 필터 차단 본문. S1 의 after 가 이것과 같아야 한다' }

        # --- S3 동결 (잔존 원소. 바뀌면 범위 이탈이다) ---
        @{ id='20'; url="$BaseUrl/signUp"; method='POST'
           note='잔존 원소 /signUp · 본문없음 → 500 동결 (작업 06 범위)' }
        @{ id='21'; url="$BaseUrl/"; note='잔존 원소 / → 현재 동작 동결  ← B3 (판정 불확정)' }
        @{ id='22'; url=$loginUrl; method='POST'; body=(& $loginBody $Login.okId $Login.badPw)
           note='틀린 비밀번호 → 401 유지 (작업 F 결과 동결)' }
    )

    return $m
}


# ===========================================================================
#  판정
# ===========================================================================

function Invoke-Comparison {

    $bPath = Join-Path $Root 'baseline\_index.csv'
    $aPath = Join-Path $Root 'after\_index.csv'

    if (-not (Test-Path $bPath)) { throw "기준선이 없다: $bPath" }
    if (-not (Test-Path $aPath)) { throw "검증 캡처가 없다: $aPath" }

    $b = @{}; Import-Csv $bPath | ForEach-Object { $b[$_.id] = $_ }
    $a = @{}; Import-Csv $aPath | ForEach-Object { $a[$_.id] = $_ }

    $unchanged   = '01','04','05','06','07','08'              # 해시 동일
    # ★M1 : 02·03 은 해시로 판정할 수 없다.
    #   GET /announces/{ancNo} 가 호출마다 incrementHits 를 돌리고(AnnounceController:72~74),
    #   hits 는 상세·목록 양쪽 본문에 실린다. ancNo=26 이 DESC 0페이지 첫 항목이라
    #   03 이 올린 값을 다음 페이즈의 02 가 본다 → 처방과 무관하게 100% 어긋난다.
    #   키 구조는 안 바뀌므로 shape 판정이 성립한다.
    $shapeFrozen = '02','03'
    $loginOkId   = '09'                                       # shape + 성공 메시지
    $authnNow    = '10','11','12','13','14'                   # ★ 신규 : after 401 AND after==REF
    $frozen      = '15','16','17','18','19','20','21','22'    # 해시 동일 + 상태 동일

    $refId = '19'
    if (-not $a.ContainsKey($refId)) { throw "REF($refId) 가 after 캡처에 없다. 판정 불가." }
    $refHash = $a[$refId].sha256
    $refPath = Join-Path $Root "after\$refId.json"

    Write-Host ""
    Write-Host "REF = 캡처 $refId (무토큰 결재 상세) · after sha256 = $($refHash.Substring(0,16))…" -ForegroundColor Cyan

    # --- ★ 가설 검증 : S2 그룹의 before 가 전부 401 인가 ---
    $s2 = '15','16','17','18','19'
    $bad = @($s2 | Where-Object { $b.ContainsKey($_) -and [int]$b[$_].status -ne 401 })
    if ($bad.Count -gt 0) {
        Write-Host ""
        Write-Host "★ 가설 반증. 자리표시자·콤마 원소가 실제로 매칭되고 있었다." -ForegroundColor Red
        foreach ($i in $bad) { Write-Host "    $i  before status = $($b[$i].status)  (401 이어야 한다)" -ForegroundColor Red }
        Write-Host "  명세 §3-1 전제가 무너진다. 중단하고 보고할 것." -ForegroundColor Red
    }

    $rows = @()

    foreach ($id in ($b.Keys | Sort-Object)) {
        if (-not $a.ContainsKey($id)) {
            $rows += [pscustomobject]@{ id=$id; expect='—'; before="$($b[$id].status)"; after='없음'
                                        hashSame=$false; canonSame=$false; verdict='FAIL — after 캡처 누락' }
            continue
        }

        $expect =
            if     ($unchanged   -contains $id) { '불변' }
            elseif ($shapeFrozen -contains $id) { '불변 (shape · hits)' }
            elseif ($id -eq $loginOkId)         { '불변 (shape)' }
            elseif ($authnNow    -contains $id) { '→ 401 · 본문=REF' }
            elseif ($frozen      -contains $id) { '완전 동결' }
            else                                { '?' }

        $sameHash = ($b[$id].sha256 -eq $a[$id].sha256)

        $sameCanon = $false
        if (-not $sameHash) {
            $pb = Join-Path $Root "baseline\$id.json"
            $pa = Join-Path $Root "after\$id.json"
            if ((Test-Path $pb) -and (Test-Path $pa)) {
                $sameCanon = Test-SameIgnoringKeyOrder -PathA $pb -PathB $pa
            }
        }
        $sameBody = ($sameHash -or $sameCanon)

        $bs = [int]$b[$id].status
        $as = [int]$a[$id].status

        $verdict =
            if ($unchanged -contains $id) {
                if     ($sameHash)  { 'PASS' }
                elseif ($sameCanon) { 'PASS — 키 순서만 다름 (R11)' }
                else                { 'FAIL — 비회귀 깨짐' }
            }
            elseif (($shapeFrozen -contains $id) -or ($id -eq $loginOkId)) {
                $sb = Join-Path $Root "baseline\$id.shape.txt"
                $sa = Join-Path $Root "after\$id.shape.txt"
                $sameShape = ((Test-Path $sb) -and (Test-Path $sa) -and
                              ((Get-Content $sb -Raw) -ceq (Get-Content $sa -Raw)))
                if     (-not $sameShape) { 'FAIL — 응답 구조가 바뀌었다' }
                elseif ($as -ne $bs)     { "FAIL — status $bs → $as" }
                elseif (($id -eq $loginOkId) -and ($a[$id].loginOk -ne 'True')) { 'FAIL — 성공 메시지 없음' }
                else                     { 'PASS' }
            }
            elseif ($authnNow -contains $id) {
                # ★ 이 그룹만 판정식이 다르다.
                #   before 상태는 판정에 쓰지 않고 기록만 한다 (13·14 는 실측 전이다 — 명세 §3-6 B2).
                #   after 가 401 이고, 본문이 REF(19) 와 같아야 한다
                #   = 필터의 같은 catch 블록을 통과했다는 뜻이다.
                $sameRef = ($a[$id].sha256 -eq $refHash)
                if (-not $sameRef) {
                    $pa = Join-Path $Root "after\$id.json"
                    if ((Test-Path $pa) -and (Test-Path $refPath)) {
                        $sameRef = Test-SameIgnoringKeyOrder -PathA $pa -PathB $refPath
                    }
                }
                if     ($as -ne 401)  { "FAIL — status=$as (401 이어야 한다)" }
                elseif (-not $sameRef){ 'FAIL — 401 이지만 본문이 REF 와 다르다 (다른 경로로 차단됐다)' }
                elseif ($bs -eq 401)  { 'WARN — before 도 이미 401 이다. 전환이 아니다' }
                else                  { "PASS — $bs → 401" }
            }
            elseif ($frozen -contains $id) {
                if     (-not $sameBody) { 'FAIL — 본문이 바뀌었다 (범위 이탈)' }
                elseif ($as -ne $bs)    { "FAIL — status $bs → $as (범위 이탈)" }
                else                    { 'PASS' }
            }
            else { '?' }

        $rows += [pscustomobject]@{
            id        = $id
            expect    = $expect
            before    = "$bs / $($b[$id].bytes)B"
            after     = "$as / $($a[$id].bytes)B"
            hashSame  = $sameHash
            canonSame = $sameCanon
            verdict   = $verdict
        }
    }

    $rows | Format-Table -AutoSize

    $fail = @($rows | Where-Object { $_.verdict -like 'FAIL*' })
    $warn = @($rows | Where-Object { $_.verdict -like 'WARN*' })

    if ($warn.Count -gt 0) {
        Write-Host ""
        Write-Host "WARN $($warn.Count) 건 — 판정 실패는 아니지만 명세와 어긋난다:" -ForegroundColor Yellow
        $warn | ForEach-Object { Write-Host "  $($_.id)  $($_.verdict)" -ForegroundColor Yellow }
    }

    if ($fail.Count -gt 0) {
        Write-Host ""
        Write-Host "FAIL $($fail.Count) 건. 아래로 국소화한다:" -ForegroundColor Red
        foreach ($f in $fail) {
            Write-Host "  Compare-Object (Get-Content $Root\baseline\$($f.id).shape.txt) (Get-Content $Root\after\$($f.id).shape.txt)"
        }
    } else {
        Write-Host ""
        Write-Host "전 항목 PASS" -ForegroundColor Green
    }

    $rows | Export-Csv (Join-Path $Root 'verdict.csv') -NoTypeInformation -Encoding UTF8
}


# ===========================================================================
#  실행
# ===========================================================================

if ($Compare) { Invoke-Comparison; return }

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }

Write-Host "캡처 시작 : $Phase  →  $OutDir   (ancNo=$AncNo)" -ForegroundColor Cyan

$index = @()
foreach ($c in (Get-Matrix)) {

    $p = @{ Id = $c.id; Url = $c.url; Binary = [bool]$c.bin }
    if ($c.method) { $p['Method']    = $c.method }
    if ($c.tok)    { $p['TokenKey']  = $c.tok }
    if ($c.raw)    { $p['RawHeader'] = $c.raw }
    if ($null -ne $c.body) { $p['Body'] = $c.body }

    $r = Invoke-Capture @p
    $r | Add-Member -NotePropertyName note  -NotePropertyValue $c.note
    $r | Add-Member -NotePropertyName token -NotePropertyValue $c.tok
    $index += $r

    $mark = if ($r.authFail) { '  [본문 401]' } else { '' }
    Write-Host ("  {0}  status={1,-3}  {2,7}B  {3}{4}" -f $r.id, $r.status, $r.bytes, $c.note, $mark)
}

$index | Export-Csv (Join-Path $OutDir '_index.csv') -NoTypeInformation -Encoding UTF8

# --- S0 건전성 : 정상 경로가 인증 실패로 나오면 토큰이 죽은 것이다 ---
$s0   = '01','02','03','04','05','06','07','08','09'
$dead = @($index | Where-Object { $s0 -contains $_.id -and ($_.authFail -or [int]$_.status -eq 401) })
if ($dead.Count -gt 0) {
    Write-Host ""
    Write-Host "중단. 정상 경로 $($dead.Count) 건이 인증 실패다 — 토큰을 재발급하고 다시 캡처할 것:" -ForegroundColor Red
    $dead | ForEach-Object { Write-Host "  $($_.id)  $($_.note)" }
    return
}

# --- 06 이 404 면 D1 과 A 의 관계가 끊긴 것이다 (계정이 바뀌었다) ---
$d1 = @($index | Where-Object { $_.id -eq '06' -and [int]$_.status -eq 404 })
if ($d1.Count -gt 0) {
    Write-Host ""
    Write-Host "중단. 06(결재 상세)이 404 다 — A 계정과 D1 의 관계가 끊겼다." -ForegroundColor Red
    Write-Host "  작업 F 와 같은 계정으로 토큰을 발급했는지 확인할 것 (명세 D13)." -ForegroundColor Red
    return
}

$ok09 = @($index | Where-Object { $_.id -eq '09' -and $_.loginOk })
if ($ok09.Count -eq 0) {
    Write-Host ""
    Write-Host "중단. 09 로그인 성공 응답이 아니다 — 자격증명을 확인할 것." -ForegroundColor Red
    return
}

# --- ★ 기준선 즉시 판정 : S2 그룹이 전부 401 인가 (명세 §3-6 B1) ---
if ($Phase -like 'baseline*') {
    $s2   = '15','16','17','18','19'
    $notU = @($index | Where-Object { $s2 -contains $_.id -and [int]$_.status -ne 401 })
    Write-Host ""
    if ($notU.Count -eq 0) {
        Write-Host "★ 가설 확정 : S2 그룹 5항목 전부 401. 자리표시자·콤마 원소는 죽어 있었다." -ForegroundColor Green
    } else {
        Write-Host "★ 가설 반증 : 아래 항목이 401 이 아니다. 중단하고 보고할 것." -ForegroundColor Red
        $notU | ForEach-Object { Write-Host "    $($_.id)  status=$($_.status)  $($_.note)" -ForegroundColor Red }
    }

    # 파괴적 실측 가드 (명세 §10)
    $danger = @($index | Where-Object { ('12','16','17','20' -contains $_.id) -and [int]$_.status -eq 200 })
    if ($danger.Count -gt 0) {
        Write-Host ""
        Write-Host "⚠ 중단. 아래가 200 이다 — 레코드가 생성·변경됐을 수 있다. 확인·삭제 후 보고할 것:" -ForegroundColor Red
        $danger | ForEach-Object { Write-Host "    $($_.id)  $($_.note)" -ForegroundColor Red }
    }

    Write-Host ""
    Write-Host "실측 기록용 — before 상태 코드:" -ForegroundColor Yellow
    $index | Where-Object { '12','13','14','20','21' -contains $_.id } |
        ForEach-Object { Write-Host ("    {0}  {1}  {2}" -f $_.id, $_.status, $_.note) -ForegroundColor Yellow }
}

Write-Host ""
Write-Host "캡처 완료. $($index.Count) 항목" -ForegroundColor Green

if ($Phase -eq 'baseline') {
    Write-Host "다음 : .\capture-authn-boundary.ps1 -Phase baseline2   (결정성 자가검증)" -ForegroundColor Yellow
}
elseif ($Phase -eq 'baseline2') {
    Write-Host "결정성 자가검증 :" -ForegroundColor Yellow
    $b1 = @{}; Import-Csv (Join-Path $Root 'baseline\_index.csv')  | ForEach-Object { $b1[$_.id] = $_.sha256 }
    $b2 = @{}; Import-Csv (Join-Path $Root 'baseline2\_index.csv') | ForEach-Object { $b2[$_.id] = $_.sha256 }

    $drift = @($b1.Keys | Where-Object { $b1[$_] -ne $b2[$_] } | Sort-Object)
    if ($drift.Count -eq 0) {
        Write-Host "  전 항목 해시 동일." -ForegroundColor Green
        Write-Host "  ⚠ 단, 02·03 이 흔들리지 않았다면 hits 가정(M1)을 재확인할 것." -ForegroundColor Yellow
    } else {
        Write-Host "  해시가 흔들리는 항목: $($drift -join ', ')" -ForegroundColor Red
        $known = '02','03','09'
        $extra = @($drift | Where-Object { $known -notcontains $_ })
        Write-Host "  02·03(hits) · 09(토큰 재발급)은 흔들리는 것이 정상이고, -Compare 가 shape 로 판정한다." -ForegroundColor Yellow
        if ($extra.Count -gt 0) {
            Write-Host "  ⚠ 설명되지 않는 항목: $($extra -join ', ') — 동적 필드가 있다. 보고할 것." -ForegroundColor Red
            Write-Host "     특히 21(/)이 여기 있으면 Spring 기본 에러의 timestamp 다 → shapeFrozen 으로 옮긴다." -ForegroundColor Red
        } else {
            Write-Host "  그 외 흔들림 없음 — 판정 가능하다." -ForegroundColor Green
        }
    }

    Write-Host ""
    Write-Host "  ⚠ R11 : 이 검증은 '같은 프로세스 안에서 결정적인가'만 본다." -ForegroundColor Yellow
    Write-Host "    애플리케이션을 재기동한 뒤 -Phase baseline2 를 한 번 더 돌려둘 것." -ForegroundColor Yellow
    Write-Host "    (-Compare 는 해시 불일치 시 키 순서 정규화로 자동 재판정한다)" -ForegroundColor Yellow
}
