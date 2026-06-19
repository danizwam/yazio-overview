# Benutzerleitfaden fuer Yazio Overview

Stand: 19.06.2026

Dieser Leitfaden beschreibt die Bedienung von Yazio Overview aus Anwendersicht. Die Screenshots zeigen Demo-Daten; bei echten Yazio-Daten sehen Namen, Mengen und Zeitraeume natuerlich anders aus.

## 1. Wofuer ist Yazio Overview da?

Yazio Overview ist ein lokales Webtool fuer Yazio-Ernaehrungsdaten. Du kannst damit:

- Yazio-Daten direkt synchronisieren
- vorhandene `products.json` und `days.json` manuell importieren
- einzelne Tage mit Mahlzeiten, Produkten und Makros auswerten
- Zeitraeume auswerten und grafisch anzeigen
- Listen und Verdichtungen erstellen
- Tagesauswertungen als Excel oder PDF exportieren
- Sport und Besonderheiten pro Tag lokal ergaenzen
- Profilinformationen wie Name und Geburtsdatum fuer Ausdrucke speichern

Das Tool laeuft lokal oder in deinem eigenen Netzwerk. Deine Daten werden lokal gespeichert und nicht an einen fremden Dienst uebertragen.

## 2. Starten

### Portable Windows-Version

Wenn du die portable Windows-Version nutzt:

1. ZIP-Datei aus dem GitHub Release herunterladen.
2. ZIP-Datei entpacken.
3. `Yazio Overview.exe` starten.
4. Der Browser oeffnet sich automatisch.

Deine Daten liegen im entpackten Ordner unter `data`.

### Docker

Wenn du Docker nutzt:

```bash
docker compose up --build
```

Danach erreichst du die Oberflaeche normalerweise unter:

```text
http://localhost:8080
```

Der Browser kann dabei "Nicht sicher" anzeigen, weil lokal per HTTP gearbeitet wird. Fuer einen lokalen oder internen Betrieb ist das normal.

## 3. Anmeldung und Benutzer

Ob eine Anmeldung erforderlich ist, haengt von der Konfiguration ab.

### Ohne Benutzerverwaltung

Wenn die Benutzerverwaltung deaktiviert ist, kommst du direkt in die App. Intern arbeitet das Tool dann mit dem Admin-Benutzer `1337` und speichert unter:

```text
data/1337
```

### Mit Benutzerverwaltung

Wenn die Benutzerverwaltung aktiviert ist, siehst du zuerst eine Login-Maske. Der Admin kann Benutzer anlegen, aktivieren, deaktivieren und loeschen.

Wichtig:

- Der Admin heisst `admin`.
- Der Admin hat die feste ID `1337`.
- Neue Benutzer bekommen fortlaufende IDs ab `1`.
- Jeder Benutzer hat einen eigenen Datenordner.
- Beim Loeschen eines Benutzers wird auch dessen Datenordner geloescht.
- Der Admin und der feste Demo-Benutzer koennen nicht geloescht werden.

Eine Anmeldung bleibt per Cookie erhalten und muss nicht bei jedem Neustart neu eingegeben werden. Ueber `Logout` kannst du dich abmelden.

### Demo-Login

Wenn die Benutzerverwaltung aktiv ist, gibt es den festen Demo-Login:

```text
Benutzername: Demo
Passwort: Demo
```

Dieser Benutzer arbeitet nur mit Mock-Daten. Es wird keine echte Yazio-Schnittstelle aufgerufen.

## 4. Demo-Modus

Der Demo-Modus ist zum Ausprobieren gedacht.

Im Demo-Modus passiert beim Import Folgendes:

- Es wird keine echte Verbindung zu Yazio aufgebaut.
- Es werden Mock-Produkte und Mock-Tage erzeugt.
- Eingegebene Yazio-Zugangsdaten werden ignoriert.
- Das Passwortfeld zeigt in der Session immer das Demo-Passwort `passwordMock123`.
- Die Demo-Daten gelten nur fuer die jeweilige Browser-Session.

Damit koennen mehrere Personen parallel testen, ohne sich gegenseitig echte Daten zu ueberschreiben.

## 5. Aufbau der Oberflaeche

Links oben findest du das Burger-Menue. Darueber wechselst du zwischen den Bereichen:

- Auswertung
- Listen
- Profil
- Yazio Import
- manueller Import von JSON
- Benutzer, falls du Admin bist und Benutzerverwaltung aktiv ist
- Hilfe
- Changelog

Oben rechts siehst du den Status. Dort steht zum Beispiel, ob das Tool bereit ist, ob Demo-Daten aktiv sind und welcher Benutzer angemeldet ist.

## 6. Auswertung eines Tages

Im Bereich `Auswertung` kannst du zwischen `Ein Tag` und `Datumsbereich` wechseln.

![Tagesauswertung](screenshots/01-auswertung-tag.png)

Bei `Ein Tag` waehlst du ein Datum aus und klickst auf `Tag anzeigen`.

Du siehst danach:

- Datum und Wochentag
- Tagesmakros
- Freitextfeld `Sport`
- Freitextfeld `Besonderheiten an diesem Tag`
- alle Mahlzeiten des Tages
- alle Bestandteile je Mahlzeit
- Makros pro Mahlzeit
- Makros pro Produkt
- Zuordnung `gegessen` oder `getrunken`

### Navigation zum Vortag oder naechsten Tag

Mit `← Vortag` und `Naechster Tag →` wechselst du schnell zwischen einzelnen Tagen. Der Button fuer den naechsten Tag ist deaktiviert, wenn kein spaeterer Tag verfuegbar ist.

### Heute-Button

Der Button `Heute` setzt das Datumsfeld auf das aktuelle Datum.

### Sport erfassen

Im Feld `Sport` kannst du einen kurzen Freitext speichern, zum Beispiel:

```text
45 Minuten Krafttraining
```

Der Text wird lokal gespeichert und erscheint im Excel- und PDF-Export in der Sport-Zeile.

### Besonderheiten erfassen

Im Feld `Besonderheiten an diesem Tag` kannst du einen kurzen Hinweis zum Tag speichern, zum Beispiel:

```text
Dienstreise, spaetes Abendessen
```

Auch dieser Text wird lokal gespeichert und ueberlebt neue Yazio-Imports.

### Tag kopieren

Mit `Tag kopieren` wird die komplette Tagesauswertung in die Zwischenablage gelegt. Das ist praktisch fuer Dokumentationen, Nachrichten oder eigene Notizen.

### Mahlzeit kopieren

Mit `Mahlzeit kopieren` kopierst du nur die jeweilige Mahlzeit inklusive Bestandteilen und Makros.

### Gegessen oder getrunken

Hinter jedem Eintrag gibt es eine Auswahl `gegessen` oder `getrunken`.

Das Tool erkennt die Kategorie automatisch, zum Beispiel anhand von Einheit, Produktart und typischen Trinkprodukten. Wenn die Erkennung nicht passt, kannst du den Eintrag manuell umstellen.

Die manuelle Aenderung wird nur gespeichert, wenn sie von der erkannten Kategorie abweicht. So bleiben echte Korrekturen erhalten, ohne unnoetig viele Daten zu schreiben.

Wichtig:

- Eine manuelle Tageskorrektur gilt fuer genau diesen Eintrag an diesem Tag.
- Eine gelernte Produktregel kann zukuenftige und nicht explizit korrigierte Eintraege beeinflussen.
- Wenn ein Produkt spaeter in Yazio geloescht und neu importiert wird, kann eine alte manuelle Korrektur ins Leere laufen. Das ist unkritisch; sie wird dann nicht mehr auf einen vorhandenen Eintrag angewendet.

## 7. Auswertung eines Datumsbereichs

Bei `Datumsbereich` waehlst du ein Start- und Enddatum aus und klickst auf `Bereich anzeigen`.

![Datumsbereich mit Graph](screenshots/02-auswertung-zeitraum-graph.png)

Der Bereich zeigt alle Tage einzeln untereinander. Jeder Tag kann separat kopiert werden. Du kannst den kompletten Bereich auch als Excel oder PDF exportieren.

### Standard-Datumsbereich

Im Profil kannst du einstellen, wie viele Tage beim ersten Aufruf standardmaessig zurueckgeschaut werden sollen. Wenn dort zum Beispiel `14` steht, beginnt die Auswertung standardmaessig bei heute minus 14 Tage.

### Plausibilitaet der Datumsfelder

Das Startdatum darf nicht spaeter als das Enddatum sein. Wenn du ein ungueltiges Intervall waehlst, zeigt das Tool eine Meldung und wertet den Bereich nicht aus.

## 8. Graph fuer den Kalorienverlauf

Im Datumsbereich kannst du den Graphen einblenden. Der Graph zeigt pro Tag einen Punkt und verbindet die Punkte zu einem Verlauf.

Du kannst unterschiedliche Kennzahlen anzeigen, zum Beispiel:

- Kalorien
- Protein
- Kohlenhydrate
- Fett

Ein Klick auf einen Punkt oeffnet den jeweiligen Tag in einem neuen Browser-Tab. Dadurch bleibt die Bereichsliste erhalten und du musst nicht wieder nach oben scrollen.

Der Graph zeigt zusaetzlich Orientierungswerte, zum Beispiel Durchschnittswerte. Diese Linien sind bewusst dezent gehalten, damit sie die eigentlichen Tagespunkte nicht verdecken.

## 9. Exporte nach Excel und PDF

Du kannst einen einzelnen Tag oder einen Datumsbereich exportieren.

### Excel-Export

Bei einem Datumsbereich erzeugt das Tool pro Tag ein eigenes Tabellenblatt. Im Excel stehen:

- Titel der Tagesuebersicht
- Name
- Geburtsdatum
- Datum
- Wochentag
- Mahlzeiten
- gegessene Produkte
- getrunkene Produkte
- Makros pro Mahlzeit
- Gesamtmakros
- Sport-Freitext
- Besonderheiten des Tages

### PDF-Export

Der PDF-Export nutzt dasselbe Grundlayout wie der Excel-Export. Er ist fuer Ausdrucke gedacht und enthaelt ebenfalls Name, Geburtsdatum, Sport und Besonderheiten.

### Dateinamen

Wenn im Profil ein Name hinterlegt ist, wird er im Dateinamen verwendet.

Beispiele:

```text
Daniel_Zwamborn_Yazio-Export_19.06.2026.xlsx
Daniel_Zwamborn_Yazio-Export_05.06.2026-19.06.2026.pdf
```

Bei einem einzelnen Tag wird das Datum nicht doppelt ausgegeben.

## 10. Listen und Verdichtungen

Im Bereich `Listen` findest du Produktsuchen, Ranglisten und zusammengefasste Auswertungen.

![Produktsuche](screenshots/03-listen-produktsuche.png)

### Produktsuche

Die Produktsuche ist nicht case-sensitiv. Du kannst mit Sternchen suchen:

```text
*skyr*
```

Das findet Produkte, deren Name irgendwo `skyr` enthaelt.

Das Ergebnis zeigt:

- Lebensmittel
- konsumierte Menge
- Anzahl der Tage
- Kalorien
- Protein

Mit `Tage` oeffnest du die Liste der Tage, an denen dieses Produkt konsumiert wurde.

### Sortierung

Je nach Liste kannst du sortieren, zum Beispiel nach:

- Menge aufsteigend
- Menge absteigend
- Kalorien
- Protein
- Datum

### Top 100 Lebensmittel

Diese Liste zeigt die am meisten konsumierten Lebensmittel. Sie ist hilfreich, um haeufige Lebensmittel oder Gewohnheiten zu erkennen.

### Tage nach Kalorien

Diese Liste zeigt Tage sortiert nach Kalorien. Ein Klick auf einen Tag oeffnet die Tagesuebersicht in einem neuen Tab.

### Tage nach Protein

Diese Liste zeigt Tage sortiert nach Protein. Das ist nuetzlich, wenn du besonders proteinreiche oder proteinarme Tage finden moechtest.

### Verdichtung nach Mahlzeiten

Diese Auswertung fasst Daten nach Mahlzeiten zusammen, zum Beispiel Fruehstueck, Mittagessen, Abendessen und Snack.

### Verdichtung nach Wochentagen

Diese Auswertung zeigt Muster nach Wochentagen. So erkennst du zum Beispiel, ob Wochenenden anders aussehen als Arbeitstage.

### Verdichtung nach Monaten

Diese Auswertung fasst die Daten monatsweise zusammen. Das ist praktisch fuer laengere Zeitraeume.

### Ausgewaehlte Liste merken

Die zuletzt ausgewaehlte Liste wird gespeichert. Wenn du spaeter wieder in den Listenbereich gehst, steht die zuletzt genutzte Auswahl wieder bereit.

## 11. Profil

Im Bereich `Profil` speicherst du persoenliche Informationen und lokale Einstellungen.

![Profil](screenshots/05-profil.png)

### Name

Der Name erscheint in Excel- und PDF-Exporten. Wenn ein Name gespeichert ist, wird er auch fuer die Dateinamen der Exporte verwendet.

### Geburtsdatum

Das Geburtsdatum erscheint in Excel und PDF im Format:

```text
TT.MM.JJJJ
```

### Standard-Datumsbereich

Hier legst du fest, wie viele Tage die Auswertung standardmaessig zurueckgehen soll.

Beispiel:

- `7` bedeutet: Startdatum ist heute minus 7 Tage.
- `14` bedeutet: Startdatum ist heute minus 14 Tage.

### Yazio Benutzername und Passwort

Hier kannst du deine Yazio-Zugangsdaten lokal speichern. Sie werden fuer den direkten Yazio-Import genutzt.

Bei lokalem oder eigenem On-Prem-Betrieb werden die Daten lokal abgelegt. Im Demo-Modus werden eingegebene Credentials nicht verwendet.

### Passwort aendern

Wenn die Benutzerverwaltung aktiv ist, kannst du als normaler Benutzer dein lokales Passwort aendern. Der Demo-Benutzer hat ein festes Passwort.

## 12. Yazio Import

Im Bereich `Yazio Import` synchronisierst du Daten direkt aus Yazio.

![Yazio Import mit Log](screenshots/04-yazio-import-log.png)

Vor dem Import sollten im Profil Yazio-Benutzername und Passwort gespeichert sein.

Der Importbereich bietet:

- Startdatum
- Enddatum
- Heute-Buttons
- Import starten
- Statusmeldung
- scrollbares Log

### Vorgeschlagener Zeitraum

Das Tool schlaegt automatisch einen Importzeitraum vor:

- Wenn es einen unvollstaendigen Tag gibt: vom aeltesten unvollstaendigen Tag bis heute.
- Sonst: vom empfohlenen Startdatum bis heute.

Dadurch musst du historische Daten nicht immer wieder vollstaendig laden, kannst aber unvollstaendige Tage nachtraeglich vervollstaendigen.

### Import-Log

Unter der Statusmeldung erscheint ein scrollbarer Logbereich. Dort siehst du, was gerade passiert. Die Statusleiste bleibt kurz und lesbar; Details stehen darunter im Log.

### Inkrementelle Imports

Jeder Import wird als eigener Snapshot gespeichert. Danach erstellt das Tool daraus eine konsolidierte Sicht.

Bei ueberschneidenden Tagen gilt:

- vollstaendige Tage schlagen unvollstaendige Tage
- bei gleicher Qualitaet gewinnt der spaetere Import

So kann ein Tag, der mittags nur halb importiert wurde, spaeter durch einen vollstaendigen Import ersetzt werden.

## 13. Manueller Import von JSON

Im Bereich `manueller Import von JSON` kannst du `products.json` und `days.json` hochladen.

![Manueller Import und Admin-Werkzeuge](screenshots/06-admin-json-backup-qualitaet.png)

Das ist vor allem nuetzlich fuer:

- alte Exporte
- Reparaturen
- Tests
- Backup-Wiederherstellungen
- Daten, die mit einem externen Exporter erzeugt wurden

Wichtig: Der manuelle Import ueberschreibt die konsolidierten Arbeitsdateien. Lokale Zusatzdaten wie Sport und Besonderheiten bleiben getrennt gespeichert.

## 14. Backup und Wiederherstellung

Im Admin-/Importbereich gibt es Funktionen fuer Backup und Wiederherstellung.

Ein Backup ist sinnvoll:

- vor groesseren Imports
- vor Benutzerumstellungen
- vor Tests mit echten Daten
- bevor du Daten manuell austauschst

Beim Wiederherstellen werden gespeicherte Daten aus dem Backup zurueckgespielt.

## 15. Datenqualitaet und Zuordnungsregeln

Das Tool kann Datenqualitaet anzeigen und Regeln fuer `gegessen` oder `getrunken` verwalten.

Die automatische Erkennung betrachtet unter anderem:

- Einheit
- Produktinformationen
- bekannte Trinkkategorien
- Produktnamen als schwaches Signal
- manuelle Korrekturen

Das ist absichtlich nicht nur eine reine Namenssuche. Ein Proteinriegel mit Einheit `bar` soll zum Beispiel nicht als Getraenk erscheinen, nur weil der Name Sport- oder Proteinbegriffe enthaelt.

## 16. Benutzerverwaltung

Wenn du als Admin angemeldet bist und die Benutzerverwaltung aktiv ist, erscheint der Bereich `Benutzer`.

Dort kannst du:

- Benutzer anlegen
- Benutzer aktivieren
- Benutzer deaktivieren
- Benutzer loeschen

Beim Loeschen eines normalen Benutzers wird auch dessen Datenordner entfernt.

Hinweise:

- Der Admin-Benutzer kann nicht geloescht werden.
- Der Demo-Benutzer kann nicht geloescht werden.
- Deaktivierte Benutzer koennen sich nicht anmelden.
- Bestehende Daten bleiben je Benutzer getrennt.

## 17. Hilfe und Changelog

Die App enthaelt eine eingebaute Hilfe und einen Changelog-Bereich.

![Hilfe](screenshots/07-hilfe.png)

Die Hilfe erklaert die wichtigsten Funktionen direkt in der Oberflaeche. Im Changelog findest du die letzten Aenderungen chronologisch, neueste zuerst.

## 18. Wo werden Daten gespeichert?

Standardmaessig liegen die Daten lokal im Ordner `data`.

Mit Benutzerverwaltung:

```text
data/<user_id>
```

Ohne Benutzerverwaltung:

```text
data/1337
```

Wichtige Dateien:

```text
products.json
days.json
settings.json
notes.json
sport-notes.json
```

Zusatzdaten wie Sport und Besonderheiten werden bewusst separat gespeichert. Dadurch gehen sie bei neuen Yazio-Imports nicht verloren.

## 19. Haeufige Fragen

### Warum zeigt der Browser "Nicht sicher"?

Weil die App lokal per HTTP laeuft. Fuer einen lokalen Betrieb ist das normal. HTTPS waere moeglich, ist fuer den reinen lokalen oder internen Betrieb aber meist unnoetig kompliziert.

### Werden meine Yazio-Zugangsdaten ins Internet gesendet?

Die Zugangsdaten werden nur fuer den direkten Import gegen Yazio verwendet. Im Demo-Modus werden sie nicht verwendet. Das Tool selbst ist fuer lokalen Betrieb gedacht.

### Funktioniert Login mit Google oder 2FA?

Der direkte Import nutzt den klassischen Yazio-Login. Google-Login und 2FA sind nicht Teil des aktuellen Imports.

### Was passiert, wenn ich Daten spaeter erneut importiere?

Historische vollstaendige Daten bleiben erhalten. Ueberschneidende Tage werden nach Qualitaet und Importzeitpunkt zusammengefuehrt.

### Was passiert mit Sport und Besonderheiten bei einem neuen Import?

Diese Felder bleiben erhalten, weil sie lokal getrennt von den Yazio-Daten gespeichert werden.

### Warum sehe ich keine Daten?

Moegliche Ursachen:

- Es wurde noch nichts importiert.
- Der gewaehlte Zeitraum enthaelt keine Daten.
- Du bist mit einem anderen Benutzer angemeldet.
- Die Userverwaltung wurde aktiviert und du nutzt einen neuen Datenordner.

### Kann ich das Tool parallel mit mehreren Benutzern nutzen?

Ja. Mit Benutzerverwaltung hat jeder Benutzer einen eigenen Datenordner. Demo-Sessions sind ebenfalls voneinander getrennt.

### Kann ich die JSON-Dateien trotzdem anschauen?

Ja. Das Tool erzeugt weiterhin `products.json` und `days.json` als konsolidierte Arbeitsdateien. Sie liegen im jeweiligen Datenordner.

### Was sollte ich vor groesseren Aenderungen tun?

Erstelle ein Backup. Das ist besonders sinnvoll vor manuellen Imports, Benutzerumstellungen oder Tests mit echten Daten.
