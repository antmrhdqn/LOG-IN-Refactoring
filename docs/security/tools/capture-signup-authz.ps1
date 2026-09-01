# ============================================================================
#  LOG-IN 보안 작업 06 — POST /signUp 무인증 계정 생성 차단 : 기준선 / 검증 캡처
#  PowerShell 5.1 전용
#
#  작업 05 의 capture-authn-boundary.ps1 개조본. 판정 축이 하나 늘고, 전송 수단이 하나 늘었다.
#    작업 05: "해시 동일" / "완전 동결" / "→401, 본문=REF"          ← $authnNow
#    작업 06: 위에 더해 → "→403, 본문=REF-403"                      ← $authzNow  ★신규
#
#  ★ 05 도구를 그대로 쓸 수 없는 이유 (명세 §10-3)
#    11·12·13 은 multipart/form-data 2파트(JSON blob + 이미지)가 필수인데,
#    PS 5.1 의 Invoke-WebRequest 에는 -Form 이 없다(PowerShell 6.1 도입).
#    → Invoke-CaptureMultipart 를 추가했다. **산출물 형식은 Invoke-Capture 와 동일하다**
#      (같은 파일명 규칙 · 같은 pscustomobject 필드) — 다르면 -Compare 가 못 읽는다.
#
#  ★ 파괴적 실측은 별도 스위치다. 일반 실행에 섞지 않았다 (명세 D4 · §11)
#    -Probe11 : 기준선 · 무토큰 signUp (계정 1건 생성) ← 코드 수정 전에만
#    -Probe13 : after   · ADMIN signUp (계정 1건 생성) ← after 캡처가 전부 끝난 뒤 단독
#    이유: 계정이 생기면 01(구성원 목록) 해시가 바뀌어 FAIL 오판이 된다 (명세 R3)
#
#  사용법 (명세 §11 단계 번호와 짝)
#     1단계  .\capture-signup-authz.ps1 -SelfTest                  ★ multipart 전송 시험 (부작용 0)
#     2단계  .\capture-signup-authz.ps1 -Phase baseline          (비파괴 12항목)
#            .\capture-signup-authz.ps1 -Phase baseline2         (결정성 자가검증)
#            ⚠ 재기동 후 baseline2 를 한 번 더 (R11)
#     4단계  .\capture-signup-authz.ps1 -Phase baseline -Probe11 🔴 파괴 1회째
#            → 이어서 명세 §10-4 ③ show-sql 관측 → ④⑤ → ⑥ 삭제 → ⑧ 원복 확인
#     5단계  .\capture-signup-authz.ps1 -Phase baseline -Only 01 (01 재캡처 · 원복 확인)
#     9단계  .\capture-signup-authz.ps1 -Phase after             (14항목)
#    10단계  .\capture-signup-authz.ps1 -Phase after -Probe13    🔴 파괴 2회째
#    11단계  .\capture-signup-authz.ps1 -Compare
#
#  ⚠ 데이터 동결 구간 : 2단계 시작 ~ 11단계 종료. 회원·공지·결재 데이터를 건드리지 말 것.
#     화면 검증(12단계)은 -Compare 가 끝난 뒤에 한다 (명세 §11).
#
#  ⚠ 저장물에 사원 이름·부서·직급 PII 와 유효 토큰이 들어간다. 리포 안으로 옮기지 말 것.
# ============================================================================

[CmdletBinding()]
param(
    [ValidateSet('baseline','baseline2','after')]
    [string]$Phase = 'baseline',

    [switch]$Compare,

    # multipart 전송 조립을 부작용 없이 시험한다 (명세 §12). 아래 -Probe 보다 먼저 1회 돌릴 것
    [switch]$SelfTest,

    # 🔴 파괴적 실측 — 계정이 실제로 생성된다. 명세 §10-4 삭제 절차를 반드시 이어서 실행할 것
    [switch]$Probe11,
    [switch]$Probe13,

    # 특정 항목만 다시 찍는다 (5단계 01 재캡처용). 기존 _index.csv 에 덮어쓴다
    [string[]]$Only,

    [string]$Root = 'C:\temp\signup-authz'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

# ---------------------------------------------------------------------------
# 준비 : C:\temp\signup-authz\tokens.ps1 을 아래 내용으로 만든다
#        (토큰·비밀번호는 자격증명이다. 이 스크립트에 직접 적지 말 것)
#
#   $BaseUrl = 'http://localhost:8080'
#
#   # ⚠ A 는 작업 F·05 와 **같은 계정**이어야 한다 (명세 D7). 바꾸면 06(회원 상세)이 깨진다.
#   $Token = @{
#       A = '<240501544 · role=MEMBER · 새로 발급>'
#       B = '<240501629 · role=ADMIN  · 새로 발급>'
#   }
#
#   $Member = @{ A = '240501544'; B = '240501629' }
#
#   $Login = @{
#       okId  = '240501544'
#       okPw  = '<A 의 비밀번호>'
#       badPw = 'wrong-password-0000'
#   }
#
#   # 파괴적 실측 입력값 (명세 D5 · §10-4 ②)
#   #   ⚠ Prefix 는 기존에 없는 대역이어야 한다. 겹치면 generateNewMemberId 가 사번을 바꿔
#   #     무엇이 만들어졌는지 모른 채 지우게 되고, §10-4 ③ SQL 개수 판정도 무너진다.
#   #   ⚠ DepartNo / PositionLevel 은 **실재하는 값**이다 (명세 v1.1 정정 4).
#   $Probe = @{
#       Prefix        = 209901
#       Id11          = 209901001        # 무토큰 실측
#       Id13          = 209901002        # ADMIN 실측
#       DepartNo      = <실재 depart_no>
#       DepartName    = '<실재 depart_name>'
#       PositionLevel = '<실재 position_level>'
#       PositionName  = '<실재 position_name>'
#       ImagePath     = 'C:\temp\signup-authz\probe.png'   # ⚠ 수 KB. 명세 §12
#   }
# ---------------------------------------------------------------------------

$tokenFile = Join-Path $Root 'tokens.ps1'
if (-not (Test-Path $tokenFile)) {
    throw "tokens.ps1 이 없다: $tokenFile  (스크립트 상단 주석 참조)"
}
. $tokenFile

$OutDir = Join-Path $Root $Phase


# ===========================================================================
#  공통 함수  (작업 05 원본과 동일 — 손대지 않았다)
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
# ⚠ 작업 F·05 에서 2회 연속 발동했다. 정규화 재판정이 없으면 FAIL 오판이 된다.
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

# 캡처 결과를 파일로 떨구고 index 행을 만든다.
# ★ Invoke-Capture 와 Invoke-CaptureMultipart 가 **둘 다 이 함수를 통해** 산출물을 낸다.
#   형식이 갈리면 -Compare 가 세 항목(11·12·13)을 못 읽는다 (명세 §10-3 ⚠).
function Write-CaptureResult {
    param(
        [string]$Id, [int]$Status, [byte[]]$Bytes, [hashtable]$Hdr,
        [string]$Url, [string]$Method, [switch]$Binary
    )
    if ($null -eq $Bytes) { $Bytes = New-Object byte[] 0 }

    $head = if ($Bytes.Length -gt 0) {
                [System.Text.Encoding]::UTF8.GetString($Bytes, 0, [Math]::Min(512, $Bytes.Length))
            } else { '' }
    $text = if ($Binary) { $head } else { [System.Text.Encoding]::UTF8.GetString($Bytes) }

    if ($Binary) {
        [System.IO.File]::WriteAllBytes((Join-Path $OutDir "$Id.bin"), $Bytes)
    } else {
        Write-Utf8NoBom -Path (Join-Path $OutDir "$Id.json") -Text $text
        $shape = @()
        try   { $shape = Get-JsonShape -Node ($text | ConvertFrom-Json) }
        catch { $shape = @('PARSE_FAILED') }   # signUp 200 응답은 평문 문자열이라 여기 걸린다. 정상이다
        Write-Utf8NoBom -Path (Join-Path $OutDir "$Id.shape.txt") -Text ($shape -join "`r`n")
    }

    [pscustomobject]@{
        id       = $Id
        status   = $Status
        bytes    = $Bytes.Length
        sha256   = Get-Sha256 -Bytes $Bytes
        ctype    = $Hdr['Content-Type']
        clen     = $Hdr['Content-Length']
        cdisp    = $Hdr['Content-Disposition']
        url      = $Url
        method   = $Method
        magic    = if ($Bytes.Length -ge 4) {
                       (($Bytes[0..3]) | ForEach-Object { $_.ToString('x2') }) -join ''
                   } else { '' }
        authFail = ($head -match '"status"\s*:\s*401')
        denied   = ($head -match '"code"\s*:\s*"C005"')
        loginOk  = ($head -match '로그인 성공')
        signupOk = ($head -match '회원 가입 성공')
    }
}

# 비-2xx 도 본문을 읽어야 한다. PS 5.1 의 Invoke-WebRequest 는 그때 예외를 던진다.
function Invoke-Capture {
    param(
        [string]$Id,
        [string]$Url,
        [string]$Method = 'GET',
        [string]$Body,
        [string]$TokenKey,
        [string]$RawHeader,
        [switch]$Binary
    )

    $headers = @{}
    if ($TokenKey)  { $headers['Authorization'] = "Bearer $($Token[$TokenKey])" }
    if ($RawHeader) { $headers['Authorization'] = $RawHeader }

    $status = $null; $bytes = $null; $hdr = @{}

    $req = @{
        Uri = $Url; Method = $Method; Headers = $headers
        UseBasicParsing = $true; ErrorAction = 'Stop'
    }
    if ($PSBoundParameters.ContainsKey('Body')) {
        # ⚠ 문자열로 넘기면 PS 5.1 이 ISO-8859-1 로 인코딩한다. 바이트로 직접 넘긴다.
        $req['Body']        = [System.Text.Encoding]::UTF8.GetBytes($Body)
        $req['ContentType'] = 'application/json'
    }

    try {
        $resp = Invoke-WebRequest @req
        $status = [int]$resp.StatusCode
        $bytes  = $resp.RawContentStream.ToArray()
        foreach ($k in 'Content-Type','Content-Length','Content-Disposition') {
            if ($resp.Headers.ContainsKey($k)) { $hdr[$k] = $resp.Headers[$k] }
        }
    }
    catch [System.Net.WebException] {
        $r = $_.Exception.Response
        if (-not $r) { throw }
        $status = [int]$r.StatusCode
        $ms = New-Object System.IO.MemoryStream
        try { $r.GetResponseStream().CopyTo($ms); $bytes = $ms.ToArray() } finally { $ms.Dispose() }
        foreach ($k in 'Content-Type','Content-Length','Content-Disposition') {
            $v = $r.Headers[$k]; if ($v) { $hdr[$k] = $v }
        }
    }

    Write-CaptureResult -Id $Id -Status $status -Bytes $bytes -Hdr $hdr `
                        -Url $Url -Method $Method -Binary:$Binary
}


# ===========================================================================
#  ★ multipart 전송 (신규)
#
#  프론트가 보내는 것을 그대로 재현한다 — RegisterMember.js:369~370
#    memberDTO             : Blob(JSON, type=application/json), 파일명 "blob"
#    memberProfilePicture  : 이미지 파일
#  파트 이름이 하나라도 다르면 MissingServletRequestPartException 이 나고,
#  그건 처방 판정이 아니라 요청 오류다. 판정이 조용히 오염된다.
# ===========================================================================

function Invoke-CaptureMultipart {
    param(
        [string]$Id,
        [string]$Url,
        [string]$TokenKey,          # 비우면 무토큰
        [int]$MemberId,
        [string]$Role = 'ADMIN',    # ★ 임의 role 주입이 이 작업의 위협이다 (명세 §3-2)
        [string]$ImagePath
    )

    if (-not (Test-Path $ImagePath)) { throw "실측 이미지가 없다: $ImagePath  (명세 §12)" }
    $img = Get-Item $ImagePath
    if ($img.Length -gt 1MB) {
        throw "실측 이미지가 너무 크다($([int]($img.Length/1KB))KB). 수 KB 로 줄일 것 — 명세 §12 (maxSwallowSize)"
    }

    $dto = [ordered]@{
        name          = 'probe'
        address       = 'probe-address'
        email         = "probe$MemberId@example.com"
        birthday      = '1990-01-01'
        gender        = '남'
        memberId      = $MemberId
        memberStatus  = '재직'
        employedDate  = (Get-Date -Format 'yyyy-MM-dd')
        password      = '0000'
        role          = $Role
        phoneNo       = '01000000000'
        departmentDTO = [ordered]@{ departNo = $Probe.DepartNo; departName = $Probe.DepartName }
        positionDTO   = [ordered]@{ positionName = $Probe.PositionName; positionLevel = $Probe.PositionLevel }
    }
    $json = $dto | ConvertTo-Json -Depth 5 -Compress

    $client  = New-Object System.Net.Http.HttpClient
    $content = New-Object System.Net.Http.MultipartFormDataContent
    $status = $null; $bytes = $null; $hdr = @{}

    try {
        $dtoPart = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, 'application/json')
        $content.Add($dtoPart, 'memberDTO', 'blob')

        $imgBytes = [System.IO.File]::ReadAllBytes($ImagePath)
        $filePart = New-Object System.Net.Http.ByteArrayContent -ArgumentList (,$imgBytes)
        $filePart.Headers.ContentType =
            [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('application/octet-stream')
        $content.Add($filePart, 'memberProfilePicture', $img.Name)

        $msg = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, $Url)
        $msg.Content = $content
        if ($TokenKey) {
            $msg.Headers.Authorization =
                New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token[$TokenKey])
        }

        $resp   = $client.SendAsync($msg).GetAwaiter().GetResult()
        $status = [int]$resp.StatusCode
        $bytes  = $resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()

        if ($resp.Content.Headers.ContentType)   { $hdr['Content-Type']   = $resp.Content.Headers.ContentType.ToString() }
        if ($resp.Content.Headers.ContentLength) { $hdr['Content-Length'] = "$($resp.Content.Headers.ContentLength)" }
    }
    finally {
        $content.Dispose()
        $client.Dispose()
    }

    Write-CaptureResult -Id $Id -Status $status -Bytes $bytes -Hdr $hdr -Url $Url -Method 'POST'
}


# ===========================================================================
#  캡처 매트릭스  (명세 §10-3)
#    S0   정상 경로   (01~06)  ← 차단·전환 검증보다 먼저 본다 (P2)
#    S1   전환        (10~12)  ← 이번 작업이 바꾸는 것
#    S0-2 ADMIN 등록  (13)     ← after 전용 · 단독 실행
#    S2   동결        (20~24)  ← 바뀌면 범위 이탈 신호. 23=REF-401 · 24=REF-403
#
#  when 필드
#    both   : baseline · after 양쪽
#    after  : after 에서만 (before 는 11 이 대신한다 — 명세 §10-3 ★)
#    probe  : -Probe11 / -Probe13 스위치로만
#
#  ⚠ 여기의 S0~S2 는 **캡처 그룹** 이름이다. precedents.md 의 S1~S10 은 **선례** 번호다.
# ===========================================================================

function Get-Matrix {

    $loginUrl  = "$BaseUrl/login"
    $loginBody = { param($id, $pw) "{""memberId"":$id,""password"":""$pw""}" }
    $list      = { param($fg) "$BaseUrl/approvals?fg=$fg&page=0&title=&direction=DESC" }
    $mid       = $Member.A
    $signUp    = "$BaseUrl/signUp"

    @(
        # --- S0 정상 경로 (처방 후 불변이어야 한다) ---
        @{ id='01'; when='both'; url="$BaseUrl/showAllMembersPage"; tok='A'; note='구성원 목록 · 토큰A  ← ★파괴적 실측이 오염시키는 항목' }
        @{ id='02'; when='both'; url="$BaseUrl/departmentDetails";  tok='A'; note='부서 목록 · 토큰A' }
        @{ id='03'; when='both'; url="$BaseUrl/showAllPosition";    tok='A'; note='직급 목록 · 토큰A' }
        @{ id='04'; when='both'; url=(& $list 'given');             tok='A'; note='결재 목록 · 토큰A  ← R11 대상 (shape)' }
        @{ id='05'; when='both'; url=$loginUrl; method='POST'; body=(& $loginBody $Login.okId $Login.okPw)
           note='로그인 성공 · 토큰 재발급  ← 해시 비교 불가. shape + 메시지로 판정' }
        @{ id='06'; when='both'; url="$BaseUrl/members/$mid";       tok='A'; note='회원 상세 · 토큰A' }

        # --- S1 전환 : 이번 작업의 본체 ---
        @{ id='10'; when='both'; url=$signUp; method='POST'
           note='무토큰 · 파트없음  before=500/C999 → 401   ★역방향 판정문 있음' }
        @{ id='11'; when='after'; url=$signUp; method='POST'; multipart=$true; probeId=11
           note='무토큰 · 유효 2파트 → 401 (after 는 비파괴 — 필터가 파트 파싱 전에 끊는다)' }
        @{ id='12'; when='after'; url=$signUp; method='POST'; multipart=$true; probeId=12; tok='A'
           note='토큰A(MEMBER) · 유효 2파트 → 403/C005 · 계정 0건' }

        # --- S0-2 ADMIN 정상 등록 (after 전용 · 단독) ---
        @{ id='13'; when='probe'; url=$signUp; method='POST'; multipart=$true; probeId=13; tok='B'
           note='🔴 토큰B(ADMIN) · 유효 2파트 → 200 · 계정 1건 생성' }

        # --- S2 동결 (바뀌면 범위 이탈이다) ---
        @{ id='20'; when='both'; url="$BaseUrl/"; note='잔존 원소 / → 현재 동작 동결 (tasks/05 D5)' }
        @{ id='21'; when='both'; url=$loginUrl; method='POST'; body=(& $loginBody $Login.okId $Login.badPw)
           note='틀린 비밀번호 → 401 동결 (작업 F 결과)' }
        @{ id='22'; when='both'; url="$BaseUrl/announces"; method='POST'
           note='무토큰 · 본문없음 → 401 동결 (05 가 415→401 로 전환시킨 것)' }
        @{ id='23'; when='both'; url="$BaseUrl/showAllMembersPage"
           note='★ REF-401 — 무토큰 필터 차단 본문. 10·11 의 after 가 이것과 같아야 한다' }
        @{ id='24'; when='both'; url="$BaseUrl/resetPassword/$mid"; method='PUT'; tok='A'
           note='★ REF-403 — MEMBER 가 ADMIN 전용 호출 → 403/C005 (작업 B 회귀 검증 겸용)' }
    )
}


# ===========================================================================
#  index 병합 — -Only / -Probe 로 나눠 찍은 결과를 한 파일로 모은다
# ===========================================================================

function Merge-Index {
    param([string]$Path, [object[]]$Rows)

    $map = [ordered]@{}
    if (Test-Path $Path) {
        Import-Csv $Path | ForEach-Object { $map[$_.id] = $_ }
    }
    foreach ($r in $Rows) { $map[$r.id] = $r }

    $merged = @($map.Keys | Sort-Object | ForEach-Object { $map[$_] })
    $merged | Export-Csv $Path -NoTypeInformation -Encoding UTF8
    return $merged
}


# ===========================================================================
#  판정  (명세 §10-3 판정 그룹)
# ===========================================================================

function Invoke-Comparison {

    $bPath = Join-Path $Root 'baseline\_index.csv'
    $aPath = Join-Path $Root 'after\_index.csv'
    if (-not (Test-Path $bPath)) { throw "기준선이 없다: $bPath" }
    if (-not (Test-Path $aPath)) { throw "검증 캡처가 없다: $aPath" }

    $b = @{}; Import-Csv $bPath | ForEach-Object { $b[$_.id] = $_ }
    $a = @{}; Import-Csv $aPath | ForEach-Object { $a[$_.id] = $_ }

    $hashSame    = '01','02','03','06'
    $shapeFrozen = '04','05'
    $authnNow    = '10','11'                    # after 401 AND after == REF-401
    $authzNow    = '12'                         # after 403 AND after == REF-403
    $frozen      = '20','21','22','23','24'
    $afterOnly   = '12','13'                    # before 가 없다. 있으면 계정을 더 만든 것이다

    $ref401 = '23'; $ref403 = '24'
    foreach ($r in @($ref401, $ref403)) {
        if (-not $a.ContainsKey($r)) { throw "REF($r) 가 after 캡처에 없다. 판정 불가." }
    }
    $h401 = $a[$ref401].sha256; $p401 = Join-Path $Root "after\$ref401.json"
    $h403 = $a[$ref403].sha256; $p403 = Join-Path $Root "after\$ref403.json"

    Write-Host ""
    Write-Host "REF-401 = 캡처 $ref401 · sha256 = $($h401.Substring(0,16))…" -ForegroundColor Cyan
    Write-Host "REF-403 = 캡처 $ref403 · sha256 = $($h403.Substring(0,16))…" -ForegroundColor Cyan

    # --- 계정 누수 감시 : afterOnly 항목이 baseline 에 있으면 안 된다 (명세 v1.1 정정 1) ---
    $leak = @($afterOnly | Where-Object { $b.ContainsKey($_) })
    if ($leak.Count -gt 0) {
        Write-Host ""
        Write-Host "⚠ $($leak -join ', ') 이 baseline 에도 있다. after 전용 항목이다." -ForegroundColor Yellow
        Write-Host "  계정이 예정보다 더 만들어졌을 수 있다. §10-4 대역 조회로 확인할 것." -ForegroundColor Yellow
    }

    $rows = @()
    $ids  = @(@($b.Keys) + @($a.Keys) | Sort-Object -Unique)

    foreach ($id in $ids) {

        if (-not $a.ContainsKey($id)) {
            $rows += [pscustomobject]@{ id=$id; expect='—'; before="$($b[$id].status)"; after='없음'
                                        hashSame=$false; canonSame=$false; verdict='FAIL — after 캡처 누락' }
            continue
        }

        $expect =
            if     ($hashSame    -contains $id) { '불변' }
            elseif ($shapeFrozen -contains $id) { '불변 (shape)' }
            elseif ($authnNow    -contains $id) { '→ 401 · 본문=REF-401' }
            elseif ($authzNow    -contains $id) { '403 · 본문=REF-403' }
            elseif ($id -eq '13')               { '200 · 등록 성공' }
            elseif ($frozen      -contains $id) { '완전 동결' }
            else                                { '?' }

        $as = [int]$a[$id].status
        $hasB = $b.ContainsKey($id)
        $bs = if ($hasB) { [int]$b[$id].status } else { $null }

        $sameHash = ($hasB -and ($b[$id].sha256 -eq $a[$id].sha256))
        $sameCanon = $false
        if ($hasB -and -not $sameHash) {
            $pb = Join-Path $Root "baseline\$id.json"
            $pa = Join-Path $Root "after\$id.json"
            if ((Test-Path $pb) -and (Test-Path $pa)) {
                $sameCanon = Test-SameIgnoringKeyOrder -PathA $pb -PathB $pa
            }
        }
        $sameBody = ($sameHash -or $sameCanon)

        $verdict =
            if ($hashSame -contains $id) {
                if     ($sameHash)  { 'PASS' }
                elseif ($sameCanon) { 'PASS — 키 순서만 다름 (R11)' }
                else                { 'FAIL — 비회귀 깨짐' }
            }
            elseif ($shapeFrozen -contains $id) {
                $sb = Join-Path $Root "baseline\$id.shape.txt"
                $sa = Join-Path $Root "after\$id.shape.txt"
                $sameShape = ((Test-Path $sb) -and (Test-Path $sa) -and
                              ((Get-Content $sb -Raw) -ceq (Get-Content $sa -Raw)))
                if     (-not $sameShape) { 'FAIL — 응답 구조가 바뀌었다' }
                elseif ($as -ne $bs)     { "FAIL — status $bs → $as" }
                elseif (($id -eq '05') -and ($a[$id].loginOk -ne 'True')) { 'FAIL — 로그인 성공 메시지 없음' }
                else                     { 'PASS' }
            }
            elseif ($authnNow -contains $id) {
                # ★ before 상태는 판정에 쓰지 않고 기록만 한다 (11 의 before 는 파괴적 실측이다)
                $same = ($a[$id].sha256 -eq $h401)
                if (-not $same) {
                    $pa = Join-Path $Root "after\$id.json"
                    if ((Test-Path $pa) -and (Test-Path $p401)) {
                        $same = Test-SameIgnoringKeyOrder -PathA $pa -PathB $p401
                    }
                }
                # ★ 역방향 판정문 (명세 R2) — 상태 코드가 곧 어느 처방이 빠졌는지 지목한다
                if     ($as -eq 403)  { 'FAIL — 403 이다. [G1] 누락 (roleLessList 원소를 안 지웠다)' }
                elseif ($as -eq 500)  { 'FAIL — 500 이다. 익명 토큰 전제가 틀렸다 (명세 §3-3 재검토)' }
                elseif ($as -ne 401)  { "FAIL — status=$as (401 이어야 한다)" }
                elseif (-not $same)   { 'FAIL — 401 이지만 본문이 REF-401 과 다르다 (다른 경로로 차단됐다)' }
                elseif ($hasB -and $bs -eq 401) { 'WARN — before 도 이미 401 이다. 전환이 아니다' }
                else                  { "PASS — $(if($hasB){$bs}else{'(before 없음)'}) → 401" }
            }
            elseif ($authzNow -contains $id) {
                $same = ($a[$id].sha256 -eq $h403)
                if (-not $same) {
                    $pa = Join-Path $Root "after\$id.json"
                    if ((Test-Path $pa) -and (Test-Path $p403)) {
                        $same = Test-SameIgnoringKeyOrder -PathA $pa -PathB $p403
                    }
                }
                if     ($as -eq 401) { 'FAIL — 401 이다. 필터가 먼저 끊었다 (토큰이 죽었는지 확인)' }
                elseif ($as -eq 200) { 'FAIL — 200 이다. [G2] 누락 (계정이 생성됐다. 즉시 삭제할 것)' }
                elseif ($as -ne 403) { "FAIL — status=$as (403 이어야 한다)" }
                elseif (-not $same)  { 'FAIL — 403 이지만 본문이 REF-403 과 다르다' }
                else                 { 'PASS — 403 / C005' }
            }
            elseif ($id -eq '13') {
                if     ($as -ne 200)                  { "FAIL — status=$as (200 이어야 한다. 정상 경로 회귀)" }
                elseif ($a[$id].signupOk -ne 'True')  { 'FAIL — 200 이지만 등록 성공 메시지가 없다' }
                else                                  { 'PASS — ADMIN 정상 등록' }
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
            before    = if ($hasB) { "$bs / $($b[$id].bytes)B" } else { '—' }
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

    Write-Host ""
    Write-Host "⚠ 캡처 PASS 는 부작용 판정이 아니다. 명세 §10-4 ⑧ 4테이블 원복 확인이 남아 있다." -ForegroundColor Yellow

    $rows | Export-Csv (Join-Path $Root 'verdict.csv') -NoTypeInformation -Encoding UTF8
}


# ===========================================================================
#  실행
# ===========================================================================

if ($Compare) { Invoke-Comparison; return }

# ---------------------------------------------------------------------------
#  ★ multipart 전송 자가시험 (명세 §12 · 부작용 0)
#
#  왜 /announces 인가
#    05 가 이 경로를 roleLessList 에서 뺐다 → 무토큰 요청이 **필터에서 401 로 끊긴다.**
#    파트 파싱·핸들러·DB 어디에도 도달하지 않으므로 레코드가 생기지 않는다.
#    그런데도 boundary 조립 · 2파트 전송 · 바이너리 본문은 끝까지 검증된다.
#    ⚠ /signUp 으로 시험하면 안 된다 — 처방 전에는 무인증으로 통과해 계정이 생긴다.
#
#  무엇을 확인하는가 (§12 체크 항목 그대로)
#    1. 전송이 서버에 닿는가          → status 401
#    2. 산출물이 나오는가             → selftest\90.json / 90.shape.txt
#    3. -Compare 가 읽을 수 있는가    → _index.csv 재-Import + 필수 컬럼 존재
# ---------------------------------------------------------------------------
if ($SelfTest) {

    $OutDir = Join-Path $Root 'selftest'
    if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }

    Write-Host ""
    Write-Host "multipart 전송 자가시험 — POST /announces · 무토큰 (부작용 없음)" -ForegroundColor Cyan

    $r = Invoke-CaptureMultipart -Id '90' -Url "$BaseUrl/announces" `
                                 -MemberId ([int]$Probe.Prefix * 1000 + 990) `
                                 -Role 'ADMIN' -ImagePath $Probe.ImagePath
    $r | Add-Member -NotePropertyName note  -NotePropertyValue '자가시험 · multipart 전송'
    $r | Add-Member -NotePropertyName token -NotePropertyValue $null

    $idx = Join-Path $OutDir '_index.csv'
    @($r) | Export-Csv $idx -NoTypeInformation -Encoding UTF8

    Write-Host ("  90  status={0,-3}  {1,7}B   authFail={2}" -f $r.status, $r.bytes, $r.authFail)
    Write-Host ""

    $ok = $true

    if ([int]$r.status -eq 401) {
        Write-Host "  [1/3] 전송 도달 · 401        PASS" -ForegroundColor Green
    } else {
        $ok = $false
        Write-Host "  [1/3] status=$($r.status) — 401 이어야 한다" -ForegroundColor Red
        if ([int]$r.status -eq 415) {
            Write-Host "        415 는 필터를 통과했다는 뜻이다. 05 처방(roleLessList)이 되돌려졌는지 확인할 것." -ForegroundColor Red
        }
    }

    $files = @('90.json','90.shape.txt') | Where-Object { -not (Test-Path (Join-Path $OutDir $_)) }
    if ($files.Count -eq 0) {
        Write-Host "  [2/3] 산출물 생성            PASS" -ForegroundColor Green
    } else {
        $ok = $false
        Write-Host "  [2/3] 누락: $($files -join ', ')" -ForegroundColor Red
    }

    try {
        $back = @(Import-Csv $idx)
        $need = 'id','status','bytes','sha256'
        $miss = @($need | Where-Object { $back[0].PSObject.Properties.Name -notcontains $_ })
        if ($back.Count -eq 1 -and $miss.Count -eq 0 -and $back[0].sha256) {
            Write-Host "  [3/3] -Compare 판독 가능    PASS" -ForegroundColor Green
        } else {
            $ok = $false
            Write-Host "  [3/3] _index.csv 형식 불일치. 누락 컬럼: $($miss -join ', ')" -ForegroundColor Red
        }
    } catch {
        $ok = $false
        Write-Host "  [3/3] _index.csv 재-Import 실패: $($_.Exception.Message)" -ForegroundColor Red
    }

    Write-Host ""
    if ($ok) {
        Write-Host "자가시험 통과. 다음 : .\capture-signup-authz.ps1 -Phase baseline" -ForegroundColor Green
        Write-Host "⚠ selftest\ 폴더는 판정에 쓰이지 않는다. 지워도 무방하다." -ForegroundColor Yellow
    } else {
        Write-Host "자가시험 실패. 여기서 고친다 — 파괴적 실측(-Probe11) 전에 반드시 통과시킬 것." -ForegroundColor Red
    }
    return
}

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }
$idxPath = Join-Path $OutDir '_index.csv'

# --- 🔴 파괴적 실측 (단독 실행) -------------------------------------------
if ($Probe11 -or $Probe13) {

    if ($Probe11 -and $Probe13) { throw "한 번에 하나만 실행할 것." }
    if ($Probe11 -and $Phase -ne 'baseline') { throw "Probe11 은 -Phase baseline 에서만. 코드 수정 전이어야 한다 (P1)." }
    if ($Probe13 -and $Phase -ne 'after')    { throw "Probe13 은 -Phase after 에서만." }

    # ⚠ $pid 는 PowerShell 읽기전용 자동 변수다. 절대 쓰지 말 것
    $probeMid = if ($Probe11) { $Probe.Id11 } else { $Probe.Id13 }
    $pTok     = if ($Probe11) { $null }       else { 'B' }
    $pIdc     = if ($Probe11) { '11' }        else { '13' }

    if ([int]([math]::Floor($probeMid / 1000)) -ne [int]$Probe.Prefix) {
        throw "사번 $probeMid 이 Prefix $($Probe.Prefix) 대역이 아니다. §10-4 대역 삭제에 걸리지 않는다 (명세 D5)."
    }

    Write-Host ""
    Write-Host "🔴 파괴적 실측 — 계정이 실제로 생성된다." -ForegroundColor Red
    Write-Host "   캡처 $pIdc · 사번 $probeMid · role=ADMIN · $(if($pTok){"토큰 $pTok"}else{'무토큰'})" -ForegroundColor Red
    Write-Host "   실행 전 §10-4 ① 기준값 4개를 찍어 뒀는가?" -ForegroundColor Red
    $ans = Read-Host "   계속하려면 'yes' 를 입력"
    if ($ans -ne 'yes') { Write-Host "중단했다." -ForegroundColor Yellow; return }

    $p = @{ Id = $pIdc; Url = "$BaseUrl/signUp"; MemberId = $probeMid
            Role = 'ADMIN'; ImagePath = $Probe.ImagePath }
    if ($pTok) { $p['TokenKey'] = $pTok }

    $r = Invoke-CaptureMultipart @p
    $r | Add-Member -NotePropertyName note  -NotePropertyValue "🔴 파괴적 실측 · 사번 $probeMid"
    $r | Add-Member -NotePropertyName token -NotePropertyValue $pTok

    Merge-Index -Path $idxPath -Rows @($r) | Out-Null

    Write-Host ""
    Write-Host ("  {0}  status={1,-3}  {2,7}B   signupOk={3}" -f $r.id, $r.status, $r.bytes, $r.signupOk)
    Write-Host ""
    Write-Host "다음 — 명세 §10-4 를 순서대로. 건너뛰면 원복이 성립하지 않는다:" -ForegroundColor Yellow
    Write-Host "  ③ bootRun 콘솔에서 INSERT 앞 member_info SELECT 개수 (1=persist / 2 이상=merge)" -ForegroundColor Yellow
    Write-Host "     ⚠ 구분점은 구문이 아니라 컬럼 목록이다. exists=id 단일 / 스냅샷=전 컬럼" -ForegroundColor Yellow
    Write-Host "  ④ member_info · transferred_history · department_info · position_info 확인" -ForegroundColor Yellow
    Write-Host "  ⑤ FK 7건 0행 확인    ⑥ 삭제(transferred_history 먼저)    ⑧ 원복 확인" -ForegroundColor Yellow
    if ($Probe11) {
        Write-Host "  그 다음 5단계 : .\capture-signup-authz.ps1 -Phase baseline -Only 01" -ForegroundColor Yellow
    }
    return
}

# --- 일반 캡처 -------------------------------------------------------------
$targets = @(Get-Matrix | Where-Object {
    ($_.when -eq 'both') -or ($_.when -eq 'after' -and $Phase -eq 'after')
})
if ($Only) { $targets = @($targets | Where-Object { $Only -contains $_.id }) }
if ($targets.Count -eq 0) { throw "찍을 항목이 없다. -Only 값을 확인할 것." }

Write-Host "캡처 시작 : $Phase  →  $OutDir   ($($targets.Count) 항목)" -ForegroundColor Cyan

$index = @()
foreach ($c in $targets) {

    if ($c.multipart) {
        # ⚠ 11·12 의 after 는 401/403 으로 끊기므로 이 사번이 쓰일 일이 없다.
        #    그래도 **대역 안 값**을 넘긴다 — 처방이 깨져 계정이 생겨도 §10-4 ⑥ 대역 삭제에 걸린다.
        #    (0 이나 대역 밖 값을 넘기면 사고분이 삭제 SQL 을 빠져나가 원복이 성립하지 않는다)
        $p = @{ Id = $c.id; Url = $c.url; Role = 'ADMIN'; ImagePath = $Probe.ImagePath
                MemberId = ([int]$Probe.Prefix * 1000 + 900 + [int]$c.id) }
        if ($c.tok) { $p['TokenKey'] = $c.tok }
        $r = Invoke-CaptureMultipart @p
    } else {
        $p = @{ Id = $c.id; Url = $c.url; Binary = [bool]$c.bin }
        if ($c.method) { $p['Method']    = $c.method }
        if ($c.tok)    { $p['TokenKey']  = $c.tok }
        if ($c.raw)    { $p['RawHeader'] = $c.raw }
        if ($null -ne $c.body) { $p['Body'] = $c.body }
        $r = Invoke-Capture @p
    }

    $r | Add-Member -NotePropertyName note  -NotePropertyValue $c.note
    $r | Add-Member -NotePropertyName token -NotePropertyValue $c.tok
    $index += $r

    $mark = ''
    if ($r.authFail) { $mark = '  [본문 401]' }
    if ($r.denied)   { $mark = '  [본문 C005]' }
    if ($r.signupOk) { $mark = '  [⚠ 등록 성공]' }
    Write-Host ("  {0}  status={1,-3}  {2,7}B  {3}{4}" -f $r.id, $r.status, $r.bytes, $c.note, $mark)
}

$index = Merge-Index -Path $idxPath -Rows $index

# --- S0 건전성 : 정상 경로가 인증 실패면 토큰이 죽은 것이다 ---
$s0   = '01','02','03','04','05','06'
$dead = @($index | Where-Object { $s0 -contains $_.id -and ($_.authFail -eq 'True' -or $_.authFail -eq $true -or [int]$_.status -eq 401) })
if ($dead.Count -gt 0) {
    Write-Host ""
    Write-Host "중단. 정상 경로 $($dead.Count) 건이 인증 실패다 — 토큰을 재발급하고 다시 캡처할 것:" -ForegroundColor Red
    $dead | ForEach-Object { Write-Host "  $($_.id)  $($_.note)" }
    return
}

# --- ⚠ 예정에 없던 계정 생성 감시 ---
$made = @($index | Where-Object { $_.signupOk -eq 'True' -or $_.signupOk -eq $true })
if ($made.Count -gt 0) {
    Write-Host ""
    Write-Host "⚠ 중단. 아래가 등록 성공 응답이다 — 계정이 생성됐다. §10-4 로 확인·삭제 후 보고할 것:" -ForegroundColor Red
    $made | ForEach-Object { Write-Host "    $($_.id)  $($_.note)" -ForegroundColor Red }
}

if ($Phase -like 'baseline*' -and -not $Only) {
    Write-Host ""
    Write-Host "실측 기록용 — before 상태 코드 (명세 §3 표에 옮길 것):" -ForegroundColor Yellow
    $index | Where-Object { '10','20','22','23','24' -contains $_.id } |
        ForEach-Object { Write-Host ("    {0}  {1}  {2}" -f $_.id, $_.status, $_.note) -ForegroundColor Yellow }

    $c10 = @($index | Where-Object { $_.id -eq '10' })
    if ($c10.Count -eq 1 -and [int]$c10[0].status -ne 500) {
        Write-Host ""
        Write-Host "★ 10(무토큰·파트없음)의 before 가 500 이 아니다 (status=$($c10[0].status))." -ForegroundColor Red
        Write-Host "  명세 §3-5 전제가 흔들린다. 중단하고 보고할 것." -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "캡처 완료. 이번 실행 $($targets.Count) 항목 / 누적 $($index.Count) 항목" -ForegroundColor Green

if ($Phase -eq 'baseline' -and -not $Only) {
    Write-Host "다음 : .\capture-signup-authz.ps1 -Phase baseline2   (결정성 자가검증)" -ForegroundColor Yellow
}
elseif ($Phase -eq 'baseline2') {
    Write-Host "결정성 자가검증 :" -ForegroundColor Yellow
    $b1 = @{}; Import-Csv (Join-Path $Root 'baseline\_index.csv')  | ForEach-Object { $b1[$_.id] = $_.sha256 }
    $b2 = @{}; Import-Csv (Join-Path $Root 'baseline2\_index.csv') | ForEach-Object { $b2[$_.id] = $_.sha256 }

    $drift = @($b2.Keys | Where-Object { $b1.ContainsKey($_) -and $b1[$_] -ne $b2[$_] } | Sort-Object)
    if ($drift.Count -eq 0) {
        Write-Host "  전 항목 해시 동일." -ForegroundColor Green
    } else {
        Write-Host "  해시가 흔들리는 항목: $($drift -join ', ')" -ForegroundColor Red
        $known = '04','05'
        $extra = @($drift | Where-Object { $known -notcontains $_ })
        Write-Host "  04(결재 목록 · 파생 속성) · 05(토큰 재발급)는 흔들리는 것이 정상이고, -Compare 가 shape 로 판정한다." -ForegroundColor Yellow
        if ($extra.Count -gt 0) {
            Write-Host "  ⚠ 설명되지 않는 항목: $($extra -join ', ') — 동적 필드가 있다. 보고할 것." -ForegroundColor Red
            Write-Host "     특히 20(/)이 여기 있으면 Spring 기본 에러의 timestamp 다 → shapeFrozen 으로 옮긴다." -ForegroundColor Red
        } else {
            Write-Host "  그 외 흔들림 없음 — 판정 가능하다." -ForegroundColor Green
        }
    }

    Write-Host ""
    Write-Host "  ⚠ R11 : 이 검증은 '같은 프로세스 안에서 결정적인가'만 본다." -ForegroundColor Yellow
    Write-Host "    애플리케이션을 재기동한 뒤 -Phase baseline2 를 한 번 더 돌려둘 것." -ForegroundColor Yellow
    Write-Host "    (-Compare 는 해시 불일치 시 키 순서 정규화로 자동 재판정한다)" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  다음 4단계 : .\capture-signup-authz.ps1 -Phase baseline -Probe11   🔴 파괴적" -ForegroundColor Yellow
}
