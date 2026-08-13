# ============================================================================
#  LOG-IN 보안 작업 04 — 인증 실패 응답 정상화(200 → 401) : 기준선 / 검증 캡처
#  PowerShell 5.1 전용
#
#  작업 E의 capture-authz.ps1 개조본. 판정 축이 하나 늘었다.
#    작업 E : "해시 동일(불변)" / "404 전환"
#    작업 04: "해시 동일(불변)" / "해시 동일 + 상태 200→401" / "완전 동결"
#
#  사용법
#    1) tokens.ps1 을 먼저 만든다 (아래 "준비" 참조). 리포 밖에 둔다.
#    2) 코드 수정 전 :  .\capture-auth-status.ps1 -Phase baseline
#       결정성 확인   :  .\capture-auth-status.ps1 -Phase baseline2
#       (⚠ 재기동 후 baseline2 를 한 번 더 돌릴 것 — R11)
#    3) 처방 후      :  .\capture-auth-status.ps1 -Phase after
#    4) 판정         :  .\capture-auth-status.ps1 -Compare
#
#  ⚠ 저장물에 사원 이름·부서·직급 PII 와 유효 토큰이 들어간다. 리포 안으로 옮기지 말 것.
# ============================================================================

[CmdletBinding()]
param(
    [ValidateSet('baseline','baseline2','after')]
    [string]$Phase = 'baseline',

    [switch]$Compare,

    [string]$Root = 'C:\temp\auth-status'
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# 준비 : C:\temp\auth-status\tokens.ps1 을 아래 내용으로 만든다
#        (토큰·비밀번호는 자격증명이다. 이 스크립트에 직접 적지 말 것)
#
#   $BaseUrl = 'http://localhost:8080'
#
#   $Token = @{
#       A       = '<240501544 정상 토큰 — 새로 발급>'
#       EXPIRED = '<24h 지난 토큰 — C:\temp\read-authz\tokens.ps1 의 것을 그대로 쓴다>'
#       FORGED  = '<위 A 토큰의 마지막 서명 문자 1개만 바꾼 것>'
#   }
#
#   $Doc = @{ D1 = '2026-non00003' }      # PROCESSING / 기안 A / 첨부 1
#
#   $File = @{                            # D1 의 첨부 (상세 조회 01 응답에서 복사)
#       savepath = 'C:/login/file/'
#       savename = '<UUID32.png>'
#       oriname  = '<원본파일명.png>'
#   }
#
#   $Login = @{
#       okId    = '240501544'             # A. 재직 상태여야 한다
#       okPw    = '<변경된 비밀번호>'
#       badPw   = 'wrong-password-0000'
#       noSuchId= '888777'                # 존재하지 않는 사번
#   }
#
#   # 휴면(재직 아님) 계정이 DB 에 있으면 채운다. 없으면 $null 로 두면 18 을 건너뛴다.
#   # ⚠ 이 검증을 위해 계정을 새로 만들지 않는다 (범위 밖).
#   $Dormant = $null                      # 또는 @{ id='<사번>'; pw='<비밀번호>' }
# ---------------------------------------------------------------------------

$tokenFile = Join-Path $Root 'tokens.ps1'
if (-not (Test-Path $tokenFile)) {
    throw "tokens.ps1 이 없다: $tokenFile  (스크립트 상단 주석 참조)"
}
. $tokenFile

$OutDir = Join-Path $Root $Phase


# ===========================================================================
#  공통 함수
# ===========================================================================

function Write-Utf8NoBom {
    param([string]$Path, [string]$Text)
    [System.IO.File]::WriteAllText($Path, $Text, (New-Object System.Text.UTF8Encoding($false)))
}

function Get-Sha256 {
    param([byte[]]$Bytes)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try   { return (($sha.ComputeHash($Bytes) | ForEach-Object { $_.ToString('x2') }) -join '') }
    finally { $sha.Dispose() }
}

# 응답 JSON 의 "모양"을 숫자 지문으로 만든다.
# 값이 아니라 키 개수 / 배열 길이만 뽑으므로, 해시가 깨졌을 때
# 어디가 달라졌는지 국소화하는 데 쓴다. 06(로그인 성공)은 이것이 주 판정 수단이다.
function Get-JsonShape {
    param($Node, [string]$Path = '$')

    $lines = @()

    if ($null -eq $Node) { return @("$Path = null") }

    if ($Node -is [System.Object[]]) {
        $lines += "$Path[] length = $($Node.Count)"
        if ($Node.Count -gt 0) { $lines += Get-JsonShape -Node $Node[0] -Path "$Path[0]" }
        return $lines
    }

    if ($Node -is [System.Management.Automation.PSCustomObject]) {
        $props = @($Node.PSObject.Properties | Sort-Object Name)
        $names = ($props | ForEach-Object { $_.Name }) -join ','
        $lines += "$Path = $($props.Count) keys : $names"
        foreach ($p in $props) { $lines += Get-JsonShape -Node $p.Value -Path "$Path.$($p.Name)" }
        return $lines
    }

    return @()
}

# JSON 의 키 순서를 재귀적으로 정렬해 정규화한다 (R11).
# ⚠ 이번 작업에서 특히 중요하다. 인증 실패 본문은 json-simple 의 JSONObject 로
#    만들어지는데 이것은 HashMap 기반이라 {status, message, reason} 의 직렬화 순서가
#    보장되지 않는다. Jackson 파생 속성과 같은 이유로 재기동 시 해시가 흔들릴 수 있다.
function Get-CanonicalJson {
    param($Node)

    if ($null -eq $Node) { return $null }

    if ($Node -is [System.Object[]]) {
        return @($Node | ForEach-Object { Get-CanonicalJson -Node $_ })
    }

    if ($Node -is [System.Management.Automation.PSCustomObject]) {
        $ordered = [ordered]@{}
        foreach ($p in ($Node.PSObject.Properties | Sort-Object Name)) {
            $ordered[$p.Name] = Get-CanonicalJson -Node $p.Value
        }
        return [pscustomobject]$ordered
    }

    return $Node
}

function Test-SameIgnoringKeyOrder {
    param([string]$PathA, [string]$PathB)
    try {
        $a = (Get-CanonicalJson -Node ([System.IO.File]::ReadAllText($PathA, [System.Text.Encoding]::UTF8) | ConvertFrom-Json)) | ConvertTo-Json -Depth 40 -Compress
        $b = (Get-CanonicalJson -Node ([System.IO.File]::ReadAllText($PathB, [System.Text.Encoding]::UTF8) | ConvertFrom-Json)) | ConvertTo-Json -Depth 40 -Compress
        return ($a -ceq $b)
    } catch {
        return $false
    }
}

# 비-2xx 도 본문을 읽어야 한다.
# 기준선에서는 인증 실패가 200 이지만, 처방 후 08~15 는 401 이 되고
# PS 5.1 의 Invoke-WebRequest 는 이때 예외를 던진다.
function Invoke-Capture {
    param(
        [string]$Id,
        [string]$Url,
        [string]$Method = 'GET',
        [string]$Body,                 # POST /login 용 (JSON 문자열)
        [string]$TokenKey,             # $Token 해시테이블의 키. "Bearer <token>" 으로 조립된다
        [string]$RawHeader,            # Authorization 헤더 원문. 형식 오류 케이스(11·12)용
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

    try {
        $resp = Invoke-WebRequest @args
        $status = [int]$resp.StatusCode

        # ⚠ $resp.Content 를 쓰면 안 된다. Content-Type 에 charset 이 붙어 있으면
        #    PowerShell 이 바이너리까지 문자열로 디코딩해 바이트가 훼손된다.
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

    # 본문 저장 (원문 그대로)
    if ($Binary) {
        [System.IO.File]::WriteAllBytes((Join-Path $OutDir "$Id.bin"), $bytes)
    } else {
        Write-Utf8NoBom -Path (Join-Path $OutDir "$Id.json") -Text $text
    }

    # 모양 지문
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
        # 본문에만 실린 401. 기준선에서 S1 이 이것을 갖는 것이 정상이고,
        # S0 가 이것을 가지면 토큰이 죽은 것이다.
        authFail = ($head -match '"status"\s*:\s*401')
        # 로그인 성공 본문 판정용 (06). 토큰이 매번 달라 해시로는 못 본다.
        loginOk  = ($head -match '로그인 성공')
    }
}


# ===========================================================================
#  캡처 매트릭스
#    S0 정상 비회귀 (01~07)  ← 차단·전환 검증보다 먼저 본다
#    S1 인증 실패   (08~15)  ← 이번 작업이 바꾸는 것
#    S2 범위 밖     (16~18)  ← 바뀌면 범위 이탈 신호
# ===========================================================================

function Get-Matrix {

    $detail  = { param($no) "$BaseUrl/approvals/$no" }
    $list    = { param($fg) "$BaseUrl/approvals?fg=$fg&page=0&title=&direction=DESC" }
    # ⚠ 변수명을 $file 로 두면 안 된다. PowerShell 은 대소문자를 구분하지 않아
    #    tokens.ps1 의 $File 해시테이블을 이 함수 스코프에서 가려버린다.
    $fileUrl = {
        $q = "fileSavepath=$([uri]::EscapeDataString($File.savepath))" +
             "&fileSavename=$([uri]::EscapeDataString($File.savename))" +
             "&fileOriname=$([uri]::EscapeDataString($File.oriname))"
        "$BaseUrl/approvals/files?$q"
    }
    $loginUrl = "$BaseUrl/login"
    $loginBody = {
        param($id, $pw)
        "{""memberId"":$id,""password"":""$pw""}"
    }

    $m = @(
        # --- S0 정상 경로 (처방 후 불변이어야 한다) ---
        @{ id='01'; url=(& $detail $Doc.D1); tok='A'; note='상세 · 기안자 · D1' }
        @{ id='02'; url=(& $list 'given');   tok='A'; note='목록 · given' }
        @{ id='03'; url=(& $fileUrl); tok='A'; bin=$true; note='파일 · 기안자 · D1  ← 바이너리 응답' }
        @{ id='04'; url="$BaseUrl/announces"; note='roleLessList · 무토큰  ← Q5' }
        @{ id='05'; url="$BaseUrl/showAllMembersPage"; note='roleLessList · 무토큰  ← Q5' }
        @{ id='06'; url=$loginUrl; method='POST'; body=(& $loginBody $Login.okId $Login.okPw)
           note='로그인 성공  ← 해시 비교 불가. shape + 메시지로 판정' }
        # ⚠ 기준선 실측 : favicon.ico 가 없어 500 + Spring 기본 에러 JSON 이 나온다.
        #    이미지가 아니므로 바이너리로 잡지 않는다. timestamp 때문에 shape 로 판정한다.
        @{ id='07'; url="$BaseUrl/favicon.ico"; note='정적 리소스 · 무토큰 (500 · shape 판정)' }

        # --- S1 인증 실패 (기준선 200 = 결함 / 처방 후 401. 본문은 불변) ---
        @{ id='08'; url=(& $detail $Doc.D1); note='필터 · 무토큰            → 401' }
        @{ id='09'; url=(& $detail $Doc.D1); tok='EXPIRED'; note='필터 · 만료 토큰          → 401' }
        @{ id='10'; url=(& $detail $Doc.D1); tok='FORGED';  note='필터 · 위조 토큰(서명)    → 401' }
        @{ id='11'; url=(& $detail $Doc.D1); raw='abc';     note='필터 · 헤더 형식오류(공백X) → 401' }
        @{ id='12'; url=(& $detail $Doc.D1); raw='Bearer '; note='필터 · 빈 토큰            → 401' }
        @{ id='13'; url=(& $fileUrl); bin=$true; note='필터 · 무토큰 · 파일 경로 → 401' }
        @{ id='14'; url=$loginUrl; method='POST'; body=(& $loginBody $Login.okId $Login.badPw)
           note='핸들러 · 틀린 비밀번호    → 401  ← Q3' }
        @{ id='15'; url=$loginUrl; method='POST'; body=(& $loginBody $Login.noSuchId 'x')
           note='핸들러 · 없는 사번        → 401  ← Q3' }

        # --- S2 범위 밖 (완전 동결. 바뀌면 범위 이탈이다) ---
        @{ id='16'; url=$loginUrl; method='POST'; body='{ broken json '
           note='깨진 JSON 본문 → 현재 동작 동결  ← Q7' }
        @{ id='17'; url=$loginUrl; method='POST'; body='{"memberId":99999999999,"password":"x"}'
           note='사번 int 범위 초과 → 현재 동작 동결' }
    )

    if ($Dormant) {
        $m += @{ id='18'; url=$loginUrl; method='POST'; body=(& $loginBody $Dormant.id $Dormant.pw)
                 note='휴면 계정 로그인 → 200 동결 (성공 핸들러 · 범위 밖)' }
    }

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

    $unchanged  = '01','02','03','04','05'                # 해시 동일
    $loginOkId  = '06'                                    # shape + 메시지
    $statusOnly = '08','09','10','11','12','13','14','15' # 해시 동일 + 200 → 401
    $frozen     = '07','18'                               # 해시 동일 + 상태 동일
    # ⚠ 기준선 실측 : 16·17 의 Spring 기본 에러 본문에는 timestamp(ms) 가 들어가
    #    매 요청 해시가 달라진다. 이 둘만 shape + 상태 코드로 판정한다.
    #    07 은 timestamp 가 없어 재기동을 건너서도 해시가 동일했다 → 해시 판정으로 둔다.
    $shapeFrozen = '16','17'

    $rows = @()

    foreach ($id in ($b.Keys | Sort-Object)) {
        if (-not $a.ContainsKey($id)) {
            $rows += [pscustomobject]@{ id=$id; expect='—'; before="$($b[$id].status)"; after='없음'
                                        hashSame=$false; canonSame=$false; verdict='FAIL — after 캡처 누락' }
            continue
        }

        $expect =
            if     ($unchanged  -contains $id) { '불변' }
            elseif ($id -eq $loginOkId)        { '불변 (shape)' }
            elseif ($statusOnly  -contains $id) { '본문 불변 · 200→401' }
            elseif ($frozen      -contains $id) { '완전 동결' }
            elseif ($shapeFrozen -contains $id) { '동결 (shape)' }
            else                                { '?' }

        $sameHash = ($b[$id].sha256 -eq $a[$id].sha256)

        # 해시가 다르면 키 순서 정규화 후 재판정한다 (R11).
        # 인증 실패 본문(json-simple HashMap)에도 적용된다.
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
            elseif ($shapeFrozen -contains $id) {
                $sb = Join-Path $Root "baseline\$id.shape.txt"
                $sa = Join-Path $Root "after\$id.shape.txt"
                $sameShape = ((Test-Path $sb) -and (Test-Path $sa) -and
                              ((Get-Content $sb -Raw) -ceq (Get-Content $sa -Raw)))
                if     (-not $sameShape) { 'FAIL — 응답 구조가 바뀌었다 (범위 이탈)' }
                elseif ($as -ne $bs)     { "FAIL — status $bs → $as (범위 이탈)" }
                else                     { 'PASS' }
            }
            elseif ($id -eq $loginOkId) {
                $sb = Join-Path $Root "baseline\$id.shape.txt"
                $sa = Join-Path $Root "after\$id.shape.txt"
                $sameShape = ((Test-Path $sb) -and (Test-Path $sa) -and
                              ((Get-Content $sb -Raw) -ceq (Get-Content $sa -Raw)))
                if     (-not $sameShape)            { 'FAIL — 성공 응답 구조가 바뀌었다' }
                elseif ($as -ne $bs)                { "FAIL — status=$as" }
                elseif ($a[$id].loginOk -ne 'True') { 'FAIL — 성공 메시지 없음' }
                else                                { 'PASS' }
            }
            elseif ($statusOnly -contains $id) {
                if     (-not $sameBody)          { 'FAIL — 본문이 바뀌었다 (Q2 위반)' }
                elseif ($bs -ne 200)             { "SKIP — 기준선이 200 이 아니다 (before=$bs)" }
                elseif ($as -ne 401)             { "FAIL — status=$as" }
                elseif ($sameCanon)              { 'PASS — 키 순서만 다름 (R11)' }
                else                             { 'PASS' }
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

Write-Host "캡처 시작 : $Phase  →  $OutDir" -ForegroundColor Cyan

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
    $mg   = if ($c.bin) { " [$($r.magic)]" } else { '' }
    Write-Host ("  {0}  status={1,-3}  {2,7}B{3}  {4}{5}" -f $r.id, $r.status, $r.bytes, $mg, $c.note, $mark)
}

$index | Export-Csv (Join-Path $OutDir '_index.csv') -NoTypeInformation -Encoding UTF8

# --- S0 건전성 : 정상 경로가 인증 실패로 나오면 토큰이 죽은 것이다 ---
#     ⚠ 작업 E 와 달리 S1(08~15)은 기준선에서 본문 401 을 갖는 것이 정상이다.
#        따라서 이 검사는 S0 에만 적용한다.
$s0   = '01','02','03','04','05','06','07'
$dead = @($index | Where-Object { $s0 -contains $_.id -and $_.authFail })
if ($dead.Count -gt 0) {
    Write-Host ""
    Write-Host "중단. 정상 경로 $($dead.Count) 건이 인증 실패다 — 토큰을 재발급하고 다시 캡처할 것:" -ForegroundColor Red
    $dead | ForEach-Object { Write-Host "  $($_.id)  $($_.note)" }
    return
}

$ok06 = @($index | Where-Object { $_.id -eq '06' -and $_.loginOk })
if ($ok06.Count -eq 0) {
    Write-Host ""
    Write-Host "중단. 06 로그인 성공 응답이 아니다 — 자격증명(비밀번호 변경분)을 확인할 것." -ForegroundColor Red
    return
}

Write-Host ""
Write-Host "캡처 완료. $($index.Count) 항목" -ForegroundColor Green

if ($Phase -eq 'baseline') {
    Write-Host "다음 : .\capture-auth-status.ps1 -Phase baseline2   (결정성 자가검증)" -ForegroundColor Yellow
}
elseif ($Phase -eq 'baseline2') {
    Write-Host "결정성 자가검증 :" -ForegroundColor Yellow
    $b1 = @{}; Import-Csv (Join-Path $Root 'baseline\_index.csv')  | ForEach-Object { $b1[$_.id] = $_.sha256 }
    $b2 = @{}; Import-Csv (Join-Path $Root 'baseline2\_index.csv') | ForEach-Object { $b2[$_.id] = $_.sha256 }

    $drift = @($b1.Keys | Where-Object { $b1[$_] -ne $b2[$_] } | Sort-Object)
    if ($drift.Count -eq 0) {
        Write-Host "  전 항목 해시 동일 — 같은 프로세스 안에서는 결정적이다." -ForegroundColor Green
    } else {
        Write-Host "  해시가 흔들리는 항목: $($drift -join ', ')" -ForegroundColor Red
        $known = '06','16','17'
        $extra = @($drift | Where-Object { $known -notcontains $_ })
        Write-Host "  06(토큰 재발급) · 16·17(Spring 기본 에러 본문의 timestamp)은 흔들리는 것이 정상이고," -ForegroundColor Yellow
        Write-Host "  -Compare 가 shape 로 판정한다." -ForegroundColor Yellow
        if ($extra.Count -gt 0) {
            Write-Host "  ⚠ 설명되지 않는 항목: $($extra -join ', ') — 동적 필드가 있다. 보고할 것." -ForegroundColor Red
        } else {
            Write-Host "  그 외 흔들림 없음 — 판정 가능하다." -ForegroundColor Green
        }
    }

    Write-Host ""
    Write-Host "  ⚠ R11 : 이 검증은 '같은 프로세스 안에서 결정적인가'만 본다." -ForegroundColor Yellow
    Write-Host "    인증 실패 본문은 json-simple 의 JSONObject(HashMap 기반)라 키 순서가 보장되지 않는다." -ForegroundColor Yellow
    Write-Host "    애플리케이션을 재기동한 뒤 -Phase baseline2 를 한 번 더 돌려둘 것." -ForegroundColor Yellow
    Write-Host "    (-Compare 는 해시 불일치 시 키 순서 정규화로 자동 재판정한다)" -ForegroundColor Yellow
}
