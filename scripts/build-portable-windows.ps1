param(
    [string]$Version = "0.0.0"
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$buildDir = Join-Path $root "build"
$classesDir = Join-Path $buildDir "classes"
$inputDir = Join-Path $buildDir "jpackage-input"
$distDir = Join-Path $root "dist"
$appName = "Yazio Overview"
$appImage = Join-Path $distDir $appName
$zipPath = Join-Path $distDir "Yazio-Overview-Windows-Portable.zip"
$appVersion = $Version.TrimStart("v")
if ($appVersion -notmatch '^\d+(\.\d+){0,3}$') {
    $appVersion = "0.0.0"
}

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    throw "javac wurde nicht gefunden. Bitte ein JDK 21 installieren und JAVA_HOME/PATH setzen."
}
if (-not (Get-Command jar -ErrorAction SilentlyContinue)) {
    throw "jar wurde nicht gefunden. Bitte ein JDK 21 installieren und JAVA_HOME/PATH setzen."
}
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage wurde nicht gefunden. Bitte ein JDK 21 installieren und JAVA_HOME/PATH setzen."
}

Remove-Item -Recurse -Force $buildDir, $distDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $classesDir, $inputDir, $distDir | Out-Null

$sources = Get-ChildItem -Path (Join-Path $root "src/main/java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d $classesDir $sources

$jarPath = Join-Path $inputDir "yazio-overview.jar"
jar --create --file $jarPath -C $classesDir .

jpackage `
    --type app-image `
    --name $appName `
    --input $inputDir `
    --main-jar "yazio-overview.jar" `
    --main-class "de.dazw.yazio.overview.YazioOverviewApp" `
    --dest $distDir `
    --java-options "-Dfile.encoding=UTF-8" `
    --java-options "-Dyazio.openBrowser=true" `
    --vendor "Yazio Overview" `
    --app-version $appVersion `
    --win-console

Copy-Item -Path (Join-Path $root "static") -Destination (Join-Path $appImage "static") -Recurse
New-Item -ItemType Directory -Force (Join-Path $appImage "data") | Out-Null

@"
Yazio Overview Portable
=======================

Start:
  Yazio Overview.exe ausfuehren.

Danach oeffnet sich der Browser automatisch unter:
  http://localhost:8080

Daten:
  Alle lokalen Daten liegen im Ordner data neben der EXE.
  Der Ordner kann mit dem kompletten Programmordner kopiert werden.

Hinweis:
  Das Programm laeuft lokal auf diesem Rechner. Fenster schliessen beendet den Server.
"@ | Set-Content -Path (Join-Path $appImage "README.txt") -Encoding UTF8

if (Test-Path $zipPath) {
    Remove-Item -Force $zipPath
}
Compress-Archive -Path (Join-Path $appImage "*") -DestinationPath $zipPath

Write-Host "Portable Paket erstellt:"
Write-Host "  $appImage"
Write-Host "  $zipPath"
