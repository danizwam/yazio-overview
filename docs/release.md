# Build und Release

Diese Datei ist fuer Maintainer gedacht. Normale Nutzer brauchen nur die README und ein fertiges Release-Paket.

## Lokaler Start ohne Docker

```bash
javac -encoding UTF-8 -d out $(find src/main/java -name "*.java")
java -cp out de.dazw.yazio.overview.YazioOverviewApp
```

Unter PowerShell:

```powershell
$files = Get-ChildItem -Recurse -Filter *.java src/main/java | ForEach-Object FullName
javac -encoding UTF-8 -d out $files
java -cp out de.dazw.yazio.overview.YazioOverviewApp
```

Optional kann der Datenordner ueberschrieben werden:

```powershell
$env:YAZIO_DATA_DIR = "C:\yazio-data"
java -cp out de.dazw.yazio.overview.YazioOverviewApp
```

## Tests ausfuehren

Die grundlegenden Funktionen sind mit JUnit 5 abgedeckt. Der echte Yazio-Import wird dabei nicht getestet.

Unter PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test.ps1
```

Beim ersten Lauf laedt das Skript den JUnit Console Runner in `build/tools`. Danach werden Hauptcode und Tests kompiliert und die Tests ausgefuehrt. Der GitHub-Workflow startet diese Tests ebenfalls vor dem Bau der portablen Windows-Version.

## Portable Windows-Version selbst bauen

Voraussetzung ist ein installiertes JDK 21. Es muss ein JDK sein, keine reine JRE, weil `javac`, `jar` und `jpackage` benoetigt werden.

Unter Windows kannst du den Build so starten:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-portable-windows.ps1
```

Das Skript erzeugt:

```text
dist/Yazio Overview/Yazio Overview.exe
dist/Yazio-Overview-Windows-Portable.zip
```

Die ZIP-Datei ist die portable Variante fuer die Weitergabe. Sie enthaelt eine eigene Java-Laufzeitumgebung.

Optional kann eine Versionsnummer uebergeben werden:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-portable-windows.ps1 -Version "1.0.0"
```

## Builds und Releases auf GitHub

Der Workflow `.github/workflows/windows-portable.yml` baut bei jedem Push auf `main` automatisch die portable Windows-Version und stellt sie als GitHub-Actions-Artifact bereit.

Wenn du nur einen Test-Build brauchst:

1. Aenderung nach GitHub pushen
2. Im Repository den Bereich `Actions` oeffnen
3. Den Lauf `Windows Portable EXE` auswaehlen
4. Unter `Artifacts` die Datei `Yazio-Overview-Windows-Portable` herunterladen

Hinweis: GitHub packt Artifacts selbst als ZIP. Deshalb wird dort der entpackte Portable-Ordner hochgeladen. Der Download enthaelt die EXE direkt und keine zweite ZIP-Datei im ZIP.

Wenn du eine Version als GitHub Release bereitstellen willst:

1. Im Repository den Bereich `Actions` oeffnen
2. Den Workflow `Windows Portable EXE` auswaehlen
3. `Run workflow` anklicken
4. `release_version` eintragen, z. B. `v1.0.0`
5. Optional `release_notes` mit dem Infotext zum Release befuellen
6. Workflow starten

Jede Release-Version muss eindeutig sein. Wenn `v1.0.0` bereits existiert, bricht der Workflow direkt ab. Fuer die naechste Version verwendest du z. B. `v1.0.1`.

Der Workflow erstellt automatisch den Git-Tag, baut die portable ZIP und haengt sie als Asset an den GitHub Release. Wenn `release_notes` leer bleibt, wird ein kurzer Standardtext verwendet.

Der Build dauert typischerweise wenige Minuten. Der erste Lauf kann etwas laenger dauern, weil GitHub den Runner vorbereitet und Java einrichtet.

## Code-Struktur

- `de.dazw.yazio.overview`: HTTP-Server, API-Handler und lokale Import-Konsolidierung
- `de.dazw.yazio.overview.export`: Excel- und PDF-Export
- `de.dazw.yazio.overview.json`: kleiner JSON-Parser/-Writer ohne externe Abhaengigkeiten
- `de.dazw.yazio.overview.model`: Domain-Records und Anzeigeformatierung
- `de.dazw.yazio.overview.service`: Tagesauswertung, Listen und Makroberechnung
- `de.dazw.yazio.overview.sync`: Yazio-API-Sync und Fortschrittsstatus
