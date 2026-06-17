param(
    [string] $BindHost = "127.0.0.1",
    [int] $Port = 8765,
    [string] $Database = "$PSScriptRoot\app-good-words.db",
    [string] $ApiKey = $env:APP_GOOD_WORDS_API_KEY,
    [switch] $Seed,
    [switch] $Open
)

$arguments = @(
    "$PSScriptRoot\app_good_words_server.mjs",
    "--host", $BindHost,
    "--port", $Port,
    "--db", $Database
)

if ($Seed) {
    $arguments += "--seed"
}

if ($ApiKey) {
    $env:APP_GOOD_WORDS_API_KEY = $ApiKey
}

if ($Open) {
    Start-Process "http://$BindHost`:$Port"
}

node @arguments
