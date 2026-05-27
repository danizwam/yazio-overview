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
java -cp out de.yazio.overview.YazioOverviewApp
```

Unter PowerShell:

```powershell
$files = Get-ChildItem -Recurse -Filter *.java src/main/java | ForEach-Object FullName
javac -encoding UTF-8 -d out $files
java -cp out de.yazio.overview.YazioOverviewApp
```

Optional kann der Datenordner überschrieben werden:

```powershell
$env:YAZIO_DATA_DIR = "C:\yazio-data"
java -cp out de.yazio.overview.YazioOverviewApp
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

Der manuelle Upload im Admin-Bereich bleibt für bestehende Exporte, Tests oder Reparaturen erhalten. Er überschreibt die konsolidierten Arbeitsdateien `data/days.json` und `data/products.json`, ersetzt aber nicht die Import-Snapshots unter `data/imports`.

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
- Manueller Admin-Upload von `products.json` und `days.json`
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

- `de.yazio.overview`: HTTP-Server, API-Handler und lokale Import-Konsolidierung
- `de.yazio.overview.export`: Excel- und PDF-Export
- `de.yazio.overview.json`: kleiner JSON-Parser/-Writer ohne externe Abhängigkeiten
- `de.yazio.overview.model`: Domain-Records und Anzeigeformatierung
- `de.yazio.overview.service`: Tagesauswertung, Listen und Makroberechnung
- `de.yazio.overview.sync`: Yazio-API-Sync und Fortschrittsstatus
