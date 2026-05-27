# Yazio Overview

Java-21-Webtool zur Auswertung eines Yazio-Exports mit `products.json` und `days.json`.

## Start mit Docker

```bash
docker compose up --build
```

Danach ist die Oberfläche unter <http://localhost:8080> erreichbar. Uploads werden in `./data/products.json` und `./data/days.json` gespeichert und bei einem späteren Upload überschrieben.

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

## Features

- Upload und persistente Speicherung von `products.json` und `days.json`
- Aktualisierung durch erneuten Upload
- Direkter Yazio-Sync per Benutzername, Passwort und Datumsbereich
- Inkrementelle Yazio-Imports unter `data/imports`, danach konsolidierte `products.json` und `days.json`
- Automatischer Sync-Vorschlag vom letzten unvollständigen Tag bis heute, mindestens aber für die letzten 14 Tage
- Lokale Speicherung persönlicher Exportdaten wie Name und Geburtsdatum
- Lokale Tagesnotizen für "Besonderheiten an diesem Tag", getrennt von den Yazio-Exportdaten
- Verknüpfung der Produkt-IDs aus `days.json` mit den Produktdaten aus `products.json`
- Einzelner Tag mit Mahlzeiten, Bestandteilen und Makros
- Datumsbereich mit getrennt kopierbaren Tages- und Mahlzeitentexten
- Excel-Export für einen Tag oder Datumsbereich, ein Tabellenblatt pro Tag
- PDF-Export für einen Tag oder Datumsbereich
- Hilfe-Seite, Tooltips und Einstiegshinweis bei leerem Datenbestand
- Responsive Weboberfläche ohne externe Java- oder JavaScript-Abhängigkeiten

## Code-Struktur

- `de.yazio.overview`: HTTP-Server und API-Handler
- `de.yazio.overview.export`: Excel- und PDF-Export
- `de.yazio.overview.json`: kleiner JSON-Parser/-Writer ohne externe Abhängigkeiten
- `de.yazio.overview.model`: Domain-Records und Anzeigeformatierung
- `de.yazio.overview.service`: Tagesauswertung und Makroberechnung
- `de.yazio.overview.sync`: Yazio-API-Sync und Fortschrittsstatus
