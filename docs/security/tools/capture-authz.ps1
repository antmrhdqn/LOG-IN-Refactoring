# ============================================================================
#  LOG-IN 보안 작업 E — 읽기 경로 인가 : 기준선 / 검증 캡처
#  PowerShell 5.1 전용
#
#  사용법
#    1) tokens.ps1 을 먼저 만든다 (아래 "준비" 참조). 리포 밖에 둔다.
#    2) 코드 수정 전 :  .\capture-read-authz.ps1 -Phase baseline
#       결정성 확인   :  .\capture-read-authz.ps1 -Phase baseline2
#                        → baseline 과 baseline2 의 해시가 전부 같아야 한다
#    3) 처방 후      :  .\capture-read-authz.ps1 -Phase after
#    4) 판정         :  .\capture-read-authz.ps1 -Compare
#
#  ⚠ 저장물에 사원 이름·부서·직급 PII 가 들어간다. 리포 안으로 옮기지 말 것.
# ============================================================================

[CmdletBinding()]
param(
    [ValidateSet('baseline','baseline2','after')]
    [string]$Phase = 'baseline',

    [switch]$Compare,

    [string]$Root = 'C:\temp\read-authz'
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# 준비 : C:\temp\read-authz\tokens.ps1 을 아래 내용으로 만든다
#        (토큰은 자격증명이다. 이 스크립트에 직접 적지 말 것)
#
#   $BaseUrl = 'http://localhost:8080'
#
#   $Token = @{
#       A = '‹240501544 기안자 토큰›'
#       Z = '‹240501629 결재자 토큰›'
#       R = '‹123 참조자 토큰›'
#       X = '‹999001 제3자 토큰›'
#   }
#
#   # 문서 세트를 만든 뒤 실제 값으로 채운다
#   $Doc = @{
#       D1 = '2026-xxx00001'   # PROCESSING / 기안 A / 결재 Z / 참조 R / 첨부 1
#       D2 = '2026-xxx00002'   # TEMP_SAVED / 기안 A
#       D3 = '2026-xxx00003'   # APPROVED   / 기안 A / 결재 Z (Z 승인 완료)
#       NONE = '2026-zzz99999' # 존재하지 않는 번호
#   }
#
#   # D1 의 첨부 (상세 조회 01 응답에서 그대로 복사)
#   $File = @{
#       savepath = 'C:/login/file/'
#       savename = '‹UUID32.png›'
#       oriname  = '‹원본파일명.png›'
#       noneSavename = 'ffffffffffffffffffffffffffffffff.png'
#   }
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
# 어디가 달라졌는지 국소화하는 데 쓴다.
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

    # 스칼라 : 존재는 부모의 키 목록에 이미 잡힌다
    return @()
}

# JSON 의 키 순서를 재귀적으로 정렬해 정규화한다.
# Jackson 은 getter 기반 파생 속성(Pageable 의 paged/unpaged 등)의 직렬화 순서를
# 보장하지 않으며, 그 순서는 JVM 인스턴스마다 다를 수 있다. 애플리케이션을 재기동하면
# 내용이 같아도 해시가 달라진다 — 작업 E 검증에서 FAIL 4건이 전부 이 원인이었다(R11).
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
# 처방 후 13~16 은 404 가 되고, PS 5.1 의 Invoke-WebRequest 는 이때 예외를 던진다.
function Invoke-Capture {
    param(
        [string]$Id,
        [string]$Url,
        [string]$TokenKey,
        [switch]$Binary
    )

    $headers = @{}
    if ($TokenKey) { $headers['Authorization'] = "Bearer $($Token[$TokenKey])" }

    $status  = $null
    $bytes   = $null
    $hdr     = @{}

    try {
        $resp = Invoke-WebRequest -Uri $Url -Method GET -Headers $headers `
                                  -UseBasicParsing -ErrorAction Stop
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

    # 바이너리라도 앞부분은 반드시 본다. 인증 실패가 200 + JSON 으로 오기 때문이다(제약 #6).
    $head = [System.Text.Encoding]::UTF8.GetString($bytes, 0, [Math]::Min(512, $bytes.Length))
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
        # 바이너리 판독용 : 89504e47 = PNG / 7b22 = JSON(에러 본문)
        magic    = if ($bytes.Length -ge 4) {
                       (($bytes[0..3]) | ForEach-Object { $_.ToString('x2') }) -join ''
                   } else { '' }
        # ⚠ 제약 #6 : 인증 실패가 HTTP 200 으로 나간다. 본문을 봐야 한다
        authFail = ($head -match '"status"\s*:\s*401')
    }
}


# ===========================================================================
#  캡처 매트릭스 — 정상 경로(01~12)가 차단 검증(13~16)보다 먼저다
# ===========================================================================

function Get-Matrix {

    $detail = { param($no) "$BaseUrl/approvals/$no" }
    $list   = { param($fg) "$BaseUrl/approvals?fg=$fg&page=0&title=&direction=DESC" }
    # ⚠ 변수명을 $file 로 두면 안 된다. PowerShell 은 대소문자를 구분하지 않아
    #    tokens.ps1 의 $File 해시테이블을 이 함수 스코프에서 가려버린다.
    $fileUrl = {
        param($savename)
        $q = "fileSavepath=$([uri]::EscapeDataString($File.savepath))" +
             "&fileSavename=$([uri]::EscapeDataString($savename))" +
             "&fileOriname=$([uri]::EscapeDataString($File.oriname))"
        "$BaseUrl/approvals/files?$q"
    }

    @(
        # --- 정상 경로 (처방 후 불변이어야 한다) ---
        @{ id='01'; url=(& $detail $Doc.D1); tok='A'; note='상세 · 기안자 · D1' }
        @{ id='02'; url=(& $detail $Doc.D1); tok='Z'; note='상세 · 결재자 · D1' }
        @{ id='03'; url=(& $detail $Doc.D1); tok='R'; note='상세 · 참조자 · D1' }
        @{ id='04'; url=(& $detail $Doc.D2); tok='A'; note='상세 · 기안자 · D2 TEMP_SAVED' }
        @{ id='05'; url=(& $detail $Doc.D3); tok='Z'; note='상세 · 결재자 · D3 APPROVED  ← P3 실증' }
        @{ id='06'; url=(& $fileUrl $File.savename); tok='A'; bin=$true; note='파일 · 기안자 · D1' }
        @{ id='07'; url=(& $fileUrl $File.savename); tok='R'; bin=$true; note='파일 · 참조자 · D1  ← P6 실증' }
        @{ id='08'; url=(& $list 'given');       tok='A'; note='목록 · given' }
        @{ id='09'; url=(& $list 'tempGiven');   tok='A'; note='목록 · tempGiven' }
        @{ id='10'; url=(& $list 'receivedAll'); tok='A'; note='목록 · receivedAll (미구현 → 400/C001)' }
        @{ id='11'; url=(& $list 'received');    tok='Z'; note='목록 · received (Z 기준)' }
        @{ id='12'; url=(& $list 'receivedRef'); tok='R'; note='목록 · receivedRef (R 기준)' }

        # --- 차단 대상 (기준선 200 = 결함 / 처방 후 404) ---
        @{ id='13'; url=(& $detail $Doc.D1); tok='X'; note='상세 · 제3자 · D1        → 404/AP001' }
        @{ id='14'; url=(& $detail $Doc.D3); tok='R'; note='상세 · 무관 ADMIN · D3   → 404/AP001  ← P1 실증' }
        @{ id='15'; url=(& $detail $Doc.D2); tok='Z'; note='상세 · 결재자 · D2 임시저장 → 404/AP001  ← P2 실증' }
        @{ id='16'; url=(& $fileUrl $File.savename); tok='X'; bin=$true; note='파일 · 제3자 · D1 → 404/AP007' }

        # --- 현재 동작 확인 (처방 후 불변) ---
        @{ id='17'; url=(& $detail $Doc.NONE); tok='A'; note='상세 · 없는 번호 → 404/AP001' }
        @{ id='18'; url=(& $fileUrl $File.noneSavename); tok='A'; bin=$true; note='파일 · 없는 savename → 404/AP007' }

        # 기준선 캡처 중 발견 : 빈 savename 이 업로드 디렉터리 자신을 200 으로 반환한다.
        # P4 처방(savename → Attachment 조회)이 부수적으로 닫는다.
        @{ id='19'; url=(& $fileUrl ''); tok='A'; bin=$true; note='파일 · 빈 savename → 404/AP007  ← 디렉터리 목록 노출' }

        # 경로 이탈 확인 : resolve(savename).normalize() 에 베이스 경로 포함 검사가 없다.
        # 상위 디렉터리가 열리는지만 가른다. 실제 파일 읽기로는 확대하지 않는다.
        @{ id='20'; url=(& $fileUrl '..'); tok='A'; bin=$true; note='파일 · savename=".." → 404/AP007  ← 경로 이탈' }
    )
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

    $unchanged = '01','02','03','04','05','06','07','08','09','10','11','12','17','18'
    $blocked   = '13','14','15','16','19','20'

    $rows = @()

    foreach ($id in ($b.Keys | Sort-Object)) {
        $expect = if ($unchanged -contains $id) { '불변' } else { '404 로 전환' }
        $sameHash = ($b[$id].sha256 -eq $a[$id].sha256)

        # 해시가 다르면 키 순서 정규화 후 재판정한다 (R11).
        # JSON 이 아닌 항목(.bin)은 정규화 대상이 아니므로 건너뛴다.
        $sameCanon = $false
        if (-not $sameHash) {
            $pb = Join-Path $Root "baseline\$id.json"
            $pa = Join-Path $Root "after\$id.json"
            if ((Test-Path $pb) -and (Test-Path $pa)) {
                $sameCanon = Test-SameIgnoringKeyOrder -PathA $pb -PathB $pa
            }
        }

        $verdict =
            if ($unchanged -contains $id) {
                if ($sameHash)        { 'PASS' }
                elseif ($sameCanon)   { 'PASS — 키 순서만 다름 (R11)' }
                else                  { 'FAIL — 비회귀 깨짐' }
            } elseif ($blocked -contains $id) {
                if ([int]$a[$id].status -eq 404) { 'PASS' } else { "FAIL — status=$($a[$id].status)" }
            } else { '?' }

        $rows += [pscustomobject]@{
            id        = $id
            expect    = $expect
            before    = "$($b[$id].status) / $($b[$id].bytes)B"
            after     = "$($a[$id].status) / $($a[$id].bytes)B"
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
    $r = Invoke-Capture -Id $c.id -Url $c.url -TokenKey $c.tok -Binary:([bool]$c.bin)
    $r | Add-Member -NotePropertyName note -NotePropertyValue $c.note
    $r | Add-Member -NotePropertyName token -NotePropertyValue $c.tok
    $index += $r

    $mark = if ($r.authFail) { '  ← 인증 실패!' } else { '' }
    $mg   = if ($c.bin) { " [$($r.magic)]" } else { '' }
    Write-Host ("  {0}  status={1,-3}  {2,7}B{3}  {4}{5}" -f $r.id, $r.status, $r.bytes, $mg, $c.note, $mark)
}

$index | Export-Csv (Join-Path $OutDir '_index.csv') -NoTypeInformation -Encoding UTF8

# --- 제약 #6 : 토큰 만료가 200 으로 나가 "성공"으로 오판된다 ---
$bad = @($index | Where-Object { $_.authFail })
if ($bad.Count -gt 0) {
    Write-Host ""
    Write-Host "중단. 본문에 status:401 이 있는 항목 $($bad.Count) 건 — 토큰을 재발급하고 다시 캡처할 것:" -ForegroundColor Red
    $bad | ForEach-Object { Write-Host "  $($_.id)  $($_.note)" }
    return
}

Write-Host ""
Write-Host "캡처 완료. $($index.Count) 항목" -ForegroundColor Green

if ($Phase -eq 'baseline') {
    Write-Host "다음 : .\capture-read-authz.ps1 -Phase baseline2   (결정성 자가검증)" -ForegroundColor Yellow
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
        Write-Host "  이 항목들은 동적 필드가 있다. 해시 대신 .shape.txt 로 판정해야 한다." -ForegroundColor Red
    }

    Write-Host ""
    Write-Host "  ⚠ R11 : 이 검증은 '같은 프로세스 안에서 결정적인가'만 본다." -ForegroundColor Yellow
    Write-Host "    Jackson 의 getter 기반 파생 속성 순서는 JVM 인스턴스마다 달라질 수 있다." -ForegroundColor Yellow
    Write-Host "    애플리케이션을 재기동한 뒤 -Phase baseline2 를 한 번 더 돌려두면" -ForegroundColor Yellow
    Write-Host "    재기동을 건너는 순서 변동까지 미리 드러난다." -ForegroundColor Yellow
    Write-Host "    (-Compare 는 해시 불일치 시 키 순서 정규화로 자동 재판정한다)" -ForegroundColor Yellow
}
