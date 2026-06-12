# Yazio Overview

Yazio Overview ist ein lokales Webtool zur Auswertung von Yazio-Ernaehrungsdaten. Das Tool kann Daten direkt aus Yazio synchronisieren, daraus konsolidierte `products.json` und `days.json` erzeugen und diese fuer Tagesauswertungen, Listen, Excel- und PDF-Exporte verwenden.

## Start mit Docker

```bash
docker compose up --build
```

Danach ist die Oberflaeche unter <http://localhost:8080> erreichbar. Die persistenten Daten liegen standardmaessig im lokalen Ordner `./data`.

## Portable Windows-Version verwenden

Wenn du die fertige portable Version nutzt, brauchst du kein Java und keine PowerShell:

1. ZIP-Datei aus dem GitHub Release herunterladen
2. ZIP-Datei entpacken
3. `Yazio Overview.exe` starten
4. Der Browser oeffnet sich automatisch unter <http://localhost:8080>

Deine lokalen Daten liegen neben der EXE im Ordner `data`. Du kannst den kompletten entpackten Ordner kopieren oder auf einen anderen Rechner verschieben.

Die portable EXE enthaelt eine Standard-Konfigurationsdatei `yazio-overview.properties`. Standardmaessig ist die Userverwaltung deaktiviert, der Demo-Modus aus und das Admin-Passwort steht auf `admin`.

## Demo-Modus

In der `docker-compose.yaml` kann der Demo-Modus ueber eine Umgebungsvariable aktiviert werden:

```yaml
environment:
  YAZIO_DEMO_MODE: "true"
```

Im expliziten Demo-Modus gibt es keine Userverwaltung. Beim Yazio-Import wird keine echte Yazio-Schnittstelle aufgerufen. Stattdessen erzeugt das Tool pro Browser-Session Mock-Produkte und Mock-Tage. Eingegebene Zugangsdaten werden nicht dauerhaft gespeichert oder verwendet; das Passwortfeld zeigt sessionbasiert immer das Demo-Passwort `passwordMock123`.

Wenn die Userverwaltung aktiv ist, gibt es zusaetzlich den fest eingebauten Demo-Login `Demo` / `Demo`. Dieser Benutzer arbeitet ebenfalls nur mit Mock-Daten in seiner Browser-Session und beruehrt keine echten Yazio-Daten.

## Userverwaltung und Login

Die Userverwaltung kann in der `docker-compose.yaml` aktiviert werden:

```yaml
environment:
  YAZIO_USER_MANAGEMENT: "true"
  YAZIO_ADMIN_PASSWORD: "bitte-aendern"
```

Wenn die Userverwaltung aktiv ist, musst du dich vor der Nutzung einloggen. Der Admin-Benutzer heisst `admin`, hat die feste ID `1337` und verwendet das Passwort aus `YAZIO_ADMIN_PASSWORD`. Nur der Admin kann neue Benutzer anlegen. Normale Benutzer bekommen fortlaufende IDs ab `1`.

Eine Anmeldung wird im Browser per Cookie fuer 30 Tage gehalten, bei Nutzung erneuert und lokal in `data/sessions.json` gespeichert. Dadurch bleibt sie auch nach einem Neustart des Servers oder Containers gueltig.

Die Daten werden je Benutzer getrennt gespeichert:

```text
data/<user_id>/products.json
data/<user_id>/days.json
data/<user_id>/settings.json
```

Ohne Userverwaltung ist kein Login notwendig. Das Tool nutzt dann automatisch den Admin-Benutzer mit der ID `1337` und speichert unter `data/1337`. Wenn du spaeter von Betrieb ohne Userverwaltung auf Userverwaltung wechselst, importierst du unter dem neuen Benutzer neu oder nutzt Backup/Restore.

Loeschst du einen Benutzer in der Benutzerverwaltung, wird auch sein Datenordner `data/<user_id>` entfernt. Der Admin-Benutzer und der feste Demo-Benutzer koennen nicht geloescht werden.

## Konfiguration ohne Docker

Beim Start per Java oder als portable EXE werden die wichtigsten Werte aus `yazio-overview.properties` neben der gestarteten App gelesen. Eine Vorlage mit Kommentaren liegt zusaetzlich in `yazio-overview.properties.example`.

```properties
server.port=8080
yazio.data.dir=data
yazio.static.dir=static
yazio.demo.mode=false
yazio.user.management=true
yazio.admin.password=bitte-aendern
```

Java-Systemproperties und Umgebungsvariablen funktionieren weiterhin und ueberschreiben die Werte aus der Datei. Eine andere Konfigdatei kannst du mit `-Dyazio.config.file=...` oder `YAZIO_CONFIG_FILE` angeben.

## Datenhaltung

Der normale Weg ist der direkte Yazio-Import ueber die Oberflaeche. Jeder Import wird als eigener Snapshot gespeichert:

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

Diese beiden Dateien sind also keine reinen Upload-Dateien mehr, sondern die aktuell zusammengefuehrte Sicht auf alle bekannten Imports. Bei ueberschneidenden Tagen gilt: vollstaendige Tage schlagen unvollstaendige Tage, bei gleicher Qualitaet gewinnt der spaetere Import. So kann ein heute nur halb importierter Tag spaeter automatisch durch einen vollstaendigen Import ersetzt werden.

Der manuelle Upload im Bereich manueller Import von JSON bleibt fuer bestehende Exporte, Altimporte oder Reparaturen erhalten. Er ueberschreibt die konsolidierten Arbeitsdateien `data/days.json` und `data/products.json`, ersetzt aber nicht die Import-Snapshots unter `data/imports`.

Zusaetzliche lokale Daten:

- `data/settings.json`: Profil, Geburtsdatum, Yazio-Zugangsdaten und Standard-Datumsbereich
- `data/notes.json`: Besonderheiten pro Tag

## Importverhalten

Der Yazio-Import schlaegt automatisch einen Zeitraum vor:

- wenn es unvollstaendige Imports gibt: vom aeltesten unvollstaendigen Tag bis heute
- sonst: von heute minus 14 Tage bis heute

Damit muss nicht jedes Mal der komplette historische Zeitraum neu geladen werden, alte vollstaendige Daten bleiben aber erhalten.

## Funktionen

- Direkter Yazio-Sync per Benutzername, Passwort und Datumsbereich
- Manueller Import von `products.json` und `days.json`
- Inkrementelle Import-Snapshots mit konsolidierter Arbeitsdatei
- Einzelner Tag mit Mahlzeiten, Bestandteilen und Makros
- Datumsbereich mit getrennt kopierbaren Tages- und Mahlzeitentexten
- Konfigurierbarer Standard-Datumsbereich fuer die Auswertung
- Tagesnotizen fuer "Besonderheiten an diesem Tag", getrennt von den Yazio-Daten
- Profilinformationen fuer Name und Geburtsdatum in Excel/PDF
- Listenansicht mit Produktsuche, Top-100-Lebensmitteln und Tagesranking
- Verdichtungen nach Mahlzeiten, Wochentagen und Monaten
- Klick von Listen auf Produkt-Verzehrtage oder Tagesuebersicht in einem neuen Tab
- Excel-Export fuer einen Tag oder Datumsbereich, ein Tabellenblatt pro Tag
- PDF-Export fuer einen Tag oder Datumsbereich
- Hilfe-Seite, Tooltips und Einstiegshinweis bei leerem Datenbestand
- Responsive Weboberflaeche ohne externe Java- oder JavaScript-Abhaengigkeiten
