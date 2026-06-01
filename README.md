# Yazio Overview

Java-21-Webtool zur lokalen Auswertung von Yazio-Ernährungsdaten. Das Tool kann Daten direkt aus Yazio synchronisieren, daraus konsolidierte `products.json` und `days.json` erzeugen und diese für Tagesauswertungen, Listen, Excel- und PDF-Exporte verwenden.

## Start mit Docker

```bash
docker compose up --build
```

Danach ist die Oberfläche unter <http://localhost:8080> erreichbar. Die persistenten Daten liegen standardmäßig im lokalen Ordner `./data`.

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

## Portable Windows-Version verwenden

Wenn du die fertige portable Version nutzt, brauchst du kein Java und keine PowerShell:

1. ZIP-Datei aus dem GitHub Release oder aus den GitHub-Actions-Artefakten herunterladen
2. ZIP-Datei entpacken
3. `Yazio Overview.exe` starten
4. Der Browser oeffnet sich automatisch unter <http://localhost:8080>

Deine lokalen Daten liegen neben der EXE im Ordner `data`. Du kannst den kompletten entpackten Ordner kopieren oder auf einen anderen Rechner verschieben.

## Portable Windows-Version selbst bauen

Dieser Abschnitt ist nur relevant, wenn du selbst eine neue portable ZIP-Datei erzeugen willst. Fuer die normale Nutzung reicht der Download der fertigen ZIP-Datei.

Voraussetzung ist ein installiertes JDK 21. Wichtig: Es muss ein JDK sein, keine reine JRE, weil `javac`, `jar` und `jpackage` benoetigt werden.

Unter Windows kannst du den Build so starten:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-portable-windows.ps1
```

Das Skript erzeugt:

```text
dist/Yazio Overview/Yazio Overview.exe
dist/Yazio-Overview-Windows-Portable.zip
```

Die ZIP-Datei ist die portable Variante fuer die Weitergabe. Sie enthaelt eine eigene Java-Laufzeitumgebung. Du musst also kein Java installieren:

1. ZIP entpacken
2. `Yazio Overview.exe` starten
3. Browser oeffnet sich automatisch unter <http://localhost:8080>

Die lokalen Daten liegen neben der EXE im Ordner `data`. Du kannst den kompletten Ordner kopieren oder auf einen anderen Rechner verschieben.

Optional kann eine Versionsnummer uebergeben werden:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-portable-windows.ps1 -Version "1.0.0"
```

## Automatischer Build auf GitHub

Der Workflow `.github/workflows/windows-portable.yml` baut bei jedem Push auf `main` automatisch die portable Windows-Version und stellt sie als GitHub-Actions-Artifact bereit.

Wenn du nur einen Test-Build brauchst:

1. Aenderung nach GitHub pushen
2. Im Repository den Bereich `Actions` oeffnen
3. Den Lauf `Windows Portable EXE` auswaehlen
4. Unter `Artifacts` die Datei `Yazio-Overview-Windows-Portable` herunterladen

Hinweis: GitHub packt Artifacts selbst als ZIP. Deshalb wird dort der entpackte Portable-Ordner hochgeladen. Der Download enthaelt die EXE direkt und keine zweite ZIP-Datei im ZIP.

Wenn du eine Version fuer andere bereitstellen willst, erstelle einen Tag:

```powershell
git tag v1.0.0
git push origin v1.0.0
```

Bei Tags, die mit `v` beginnen, erstellt der Workflow zusaetzlich einen GitHub Release und haengt die vom Build-Skript erzeugte portable ZIP dort an. Das ist der normale Weg, um fertige Downloads bereitzustellen.

Der Build dauert typischerweise wenige Minuten. Der erste Lauf kann etwas laenger dauern, weil GitHub den Runner vorbereitet und Java einrichtet.

Optional kann der Datenordner überschrieben werden:

```powershell
$env:YAZIO_DATA_DIR = "C:\yazio-data"
java -cp out de.dazw.yazio.overview.YazioOverviewApp
```

## Datenhaltung

Der normale Weg ist der direkte Yazio-Import über die Oberfläche. Jeder Import wird als eigener Snapshot gespeichert:

```text
data/imports/<zeitstempel>/days.json
data/imports/<zeitstempel>/products.json
data/imports/<zeitstempel>/metadata.json
```

Nach jedem Import erzeugt das Tool daraus konsolidierte Arbeitsdateien:

```text
data/days.json
data/products.json
```

Diese beiden Dateien sind also keine reinen Upload-Dateien mehr, sondern die aktuell zusammengeführte Sicht auf alle bekannten Imports. Bei überschneidenden Tagen gilt: vollständige Tage schlagen unvollständige Tage, bei gleicher Qualität gewinnt der spätere Import. So kann ein heute nur halb importierter Tag später automatisch durch einen vollständigen Import ersetzt werden.

Der manuelle Upload im Bereich manueller Import von JSON bleibt für bestehende Exporte, Tests oder Reparaturen erhalten. Er überschreibt die konsolidierten Arbeitsdateien `data/days.json` und `data/products.json`, ersetzt aber nicht die Import-Snapshots unter `data/imports`.

Zusätzliche lokale Daten:

- `data/settings.json`: Profil, Geburtsdatum und Yazio-Zugangsdaten
- `data/notes.json`: Besonderheiten pro Tag

## Importverhalten

Der Yazio-Import schlägt automatisch einen Zeitraum vor:

- wenn es unvollständige Imports gibt: vom ältesten unvollständigen Tag bis heute
- sonst: von heute minus 14 Tage bis heute

Damit muss nicht jedes Mal der komplette historische Zeitraum neu geladen werden, alte vollständige Daten bleiben aber erhalten.

## Funktionen

- Direkter Yazio-Sync per Benutzername, Passwort und Datumsbereich
- Manueller Import von `products.json` und `days.json`
- Inkrementelle Import-Snapshots mit konsolidierter Arbeitsdatei
- Einzelner Tag mit Mahlzeiten, Bestandteilen und Makros
- Datumsbereich mit getrennt kopierbaren Tages- und Mahlzeitentexten
- Tagesnotizen für "Besonderheiten an diesem Tag", getrennt von den Yazio-Daten
- Profilinformationen für Name und Geburtsdatum in Excel/PDF
- Listenansicht mit Produktsuche, Top-100-Lebensmitteln und Tagesranking
- Verdichtungen nach Mahlzeiten, Wochentagen und Monaten
- Klick von Listen auf Produkt-Verzehrtage oder Tagesübersicht in einem neuen Tab
- Excel-Export für einen Tag oder Datumsbereich, ein Tabellenblatt pro Tag
- PDF-Export für einen Tag oder Datumsbereich
- Hilfe-Seite, Tooltips und Einstiegshinweis bei leerem Datenbestand
- Responsive Weboberfläche ohne externe Java- oder JavaScript-Abhängigkeiten

## Code-Struktur

- `de.dazw.yazio.overview`: HTTP-Server, API-Handler und lokale Import-Konsolidierung
- `de.dazw.yazio.overview.export`: Excel- und PDF-Export
- `de.dazw.yazio.overview.json`: kleiner JSON-Parser/-Writer ohne externe Abhängigkeiten
- `de.dazw.yazio.overview.model`: Domain-Records und Anzeigeformatierung
- `de.dazw.yazio.overview.service`: Tagesauswertung, Listen und Makroberechnung
- `de.dazw.yazio.overview.sync`: Yazio-API-Sync und Fortschrittsstatus
