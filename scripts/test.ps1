param(
    [string]$JUnitVersion = "1.10.2"
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$buildDir = Join-Path $root "build"
$classesDir = Join-Path $buildDir "classes"
$testClassesDir = Join-Path $buildDir "test-classes"
$toolsDir = Join-Path $buildDir "tools"
$junitJar = Join-Path $toolsDir "junit-platform-console-standalone-$JUnitVersion.jar"
$junitUrl = "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$JUnitVersion/junit-platform-console-standalone-$JUnitVersion.jar"

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    throw "javac wurde nicht gefunden. Bitte ein JDK 21 installieren und JAVA_HOME/PATH setzen."
}

function Invoke-Javac {
    param(
        [string[]]$Arguments,
        [string]$ErrorMessage
    )

    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & javac @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $oldPreference
    }
    if ($exitCode -ne 0) {
        $output | ForEach-Object { Write-Host $_ }
        throw $ErrorMessage
    }
    $filtered = $output | Where-Object {
        $line = [string]$_
        -not (
            $line.Contains("Im Compiler") -or
            $line.Contains("java.nio.file.AccessDeniedException") -or
            $line.Contains("ZipFileSystemProvider") -or
            $line.Contains("JavacFileManager") -or
            $line.Contains("jdk.compiler/com.sun.tools.javac") -or
            $line.Contains("java.base/") -or
            $line.TrimStart().StartsWith("at ")
        )
    }
    $filtered | ForEach-Object { Write-Host $_ }
}

Remove-Item -Recurse -Force $classesDir, $testClassesDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $classesDir, $testClassesDir, $toolsDir | Out-Null

if (-not (Test-Path $junitJar)) {
    Write-Host "Lade JUnit Console Runner $JUnitVersion..."
    Invoke-WebRequest -Uri $junitUrl -OutFile $junitJar
}

$sources = Get-ChildItem -Path (Join-Path $root "src/main/java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$testSources = Get-ChildItem -Path (Join-Path $root "src/test/java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$mainArgs = Join-Path $buildDir "main-sources.args"
$testArgs = Join-Path $buildDir "test-sources.args"
$sources | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' } | Set-Content -Path $mainArgs -Encoding ASCII
$testSources | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' } | Set-Content -Path $testArgs -Encoding ASCII

Invoke-Javac -Arguments @("-encoding", "UTF-8", "-d", $classesDir, "@$mainArgs") -ErrorMessage "Kompilierung des Hauptcodes ist fehlgeschlagen."
Invoke-Javac -Arguments @("-encoding", "UTF-8", "-cp", $junitJar, "-d", $testClassesDir, "@$mainArgs", "@$testArgs") -ErrorMessage "Kompilierung der Tests ist fehlgeschlagen."

$testClasspath = "$testClassesDir"
& java -jar $junitJar execute --class-path $testClasspath --scan-class-path
if ($LASTEXITCODE -ne 0) {
    throw "JUnit-Tests sind fehlgeschlagen."
}
