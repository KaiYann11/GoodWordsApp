param(
    [string] $ApkPath = "$PSScriptRoot\..\app\build\outputs\apk\debug\app-debug.apk",
    [string] $WebRoot = "$PSScriptRoot\web"
)

$ErrorActionPreference = 'Stop'
$apk = Get-Item -LiteralPath $ApkPath
$metadataPath = Join-Path $apk.DirectoryName 'output-metadata.json'
$metadata = [IO.File]::ReadAllText($metadataPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
if ($metadata.applicationId -ne 'com.codex.appgoodwords') { throw '오늘의 글귀 APK가 아닙니다.' }
$release = @($metadata.elements | Where-Object { $_.outputFile -eq $apk.Name })
if ($release.Count -ne 1 -or $release[0].versionName -notmatch '^[a-zA-Z0-9._-]+$') {
    throw 'APK의 버전 정보를 확인할 수 없습니다.'
}

$hash = (Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
$fileName = "app-good-words-$($release[0].versionName)-$($release[0].versionCode)-$($hash.Substring(0, 8)).apk"
$updateRoot = [IO.Path]::GetFullPath((Join-Path $WebRoot 'update'))
[IO.Directory]::CreateDirectory($updateRoot) | Out-Null
Copy-Item -LiteralPath $apk.FullName -Destination (Join-Path $updateRoot $fileName)

$manifest = [ordered]@{
    versionName = $release[0].versionName
    versionCode = $release[0].versionCode
    publishedAt = [DateTimeOffset]::UtcNow.ToString('o')
    fileName = $fileName
    sizeBytes = $apk.Length
    sha256 = $hash
}
$manifestPath = Join-Path $updateRoot 'latest.json'
$temporaryPath = Join-Path $updateRoot 'latest.json.tmp'
# 파일 복사가 끝난 뒤 목록을 교체해, 내려받는 중인 사용자에게 미완성 파일을 안내하지 않습니다.
[IO.File]::WriteAllText($temporaryPath, ($manifest | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))
Move-Item -LiteralPath $temporaryPath -Destination $manifestPath -Force
Write-Output "게시 완료: 버전 $($release[0].versionName), $fileName"
