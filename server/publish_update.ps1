param(
    [string] $ApkPath,
    [string] $WebRoot
)

# 게시 규칙은 publish_update.mjs 한 곳에 있습니다. 서버가 맥에서도 돌아가야 해서, 같은 일을
# PowerShell에 한 벌 더 적어 두지 않습니다. 두 벌이면 한쪽만 고쳐졌을 때 운영체제에 따라
# 다른 latest.json이 나가고, 그것은 앱이 업데이트를 못 찾는 꼴로만 드러납니다.
$ErrorActionPreference = 'Stop'

$arguments = @("$PSScriptRoot\publish_update.mjs")
if ($ApkPath) { $arguments += @('--apk', $ApkPath) }
if ($WebRoot) { $arguments += @('--web-root', $WebRoot) }

node @arguments
if ($LASTEXITCODE -ne 0) { throw '게시하지 못했습니다.' }
