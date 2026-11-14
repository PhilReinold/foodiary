# FooDiary

Ernährungstagebuch für Android. Mahlzeiten und Befinden werden lokal auf dem Gerät gespeichert, der CSV-Export passiert nur auf Wunsch.

## Funktionen (MVP)

- Erinnerungen zu Frühstück (10 Uhr), Mittagessen (14 Uhr), Abendessen (20 Uhr) und Snack-Check (22 Uhr), anpassbar unter „Erinnerungen“
- Eingabe per Freitext, Befinden 1–10 und Beschwerden als Freitext
- Essenszeit per Schnellwahl oder Uhrzeit-Picker
- Übersicht nach Tagen, Einträge nachträglich bearbeiten oder löschen
- CSV-Export im Langformat über den Android-Dateidialog

## APK bauen

1. Repo auf GitHub pushen (Branch `main`).
2. Unter „Actions“ läuft der Workflow „Build APK“ automatisch. Alternativ über „Run workflow“ starten.
3. Nach dem Lauf unter dem Workflow-Run das Artefakt `foodiary-debug-apk` herunterladen.
4. Die `app-debug.apk` aufs Handy kopieren und installieren. Dafür muss „Installation unbekannter Apps“ für die verwendete App (Dateimanager oder Browser) erlaubt sein.

## Berechtigungen auf dem Handy

- Benachrichtigungen: beim ersten Start abfragen.
- Exakte Alarme: ab Android 12 muss man sie unter „Apps → FooDiary → Wecker und Erinnerungen“ erlauben. Ohne diese Erlaubnis kommen Erinnerungen ungefähr zur Zeit, nicht exakt.
- Akku-Optimierung: für zuverlässige Erinnerungen „Uneingeschränkt“ einstellen.

## Datenschutz

Alle Daten liegen in einer lokalen SQLite-Datenbank (`foodiary.db`). Es gibt keine Cloud, keine Konten und keine Netzwerkzugriffe. Die App stellt keine Diagnose.
