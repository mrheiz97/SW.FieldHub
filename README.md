# StumpfWorks FieldHub

**One platform. Many workspaces.**

> **Projektstatus: frühe Entwicklung / Proof of Concept.** FieldHub ist noch nicht produktionsreif. Die unten beschriebenen Funktionen sind teils bereits als Android-Prototyp erprobt, teils geplant. Diese README beschreibt die gemeinsame Produktvision und die geplante technische Architektur.

**FieldHub** ist eine modular aufgebaute, selbst hostbare Open-Source-Plattform für Teams, Vereine, Organisationen und kleine Unternehmen. Sie verbindet Arbeitsbereiche (*Workspaces*), Aufgaben und Abläufe, Recruiting, Mitglieder- und Veranstaltungsorganisation sowie auftragsbezogene Werkzeuge in einer gemeinsamen Anwendung.

Das Ziel ist nicht, für jede Organisation eine eigene App zu entwickeln. Stattdessen stellt FieldHub einen schlanken technischen Kern bereit, dessen Oberfläche, Module und Berechtigungen sich an den jeweiligen Workspace und die angemeldete Person anpassen.

## Warum FieldHub?

Viele Teams nutzen mehrere voneinander getrennte Werkzeuge: Tabellen für Mitglieder, Formulare für Interessenten, Chatgruppen für Veranstaltungen, separate Apps für Aufträge und weitere Lösungen für Aufgaben und Auswertungen.

FieldHub soll diese Abläufe dort zusammenführen, wo es sinnvoll ist, ohne alle Benutzer mit einer überladenen Oberfläche zu konfrontieren.

**Grundidee:** Eine Anmeldung, mehrere mögliche Workspaces – aber jede Person sieht ausschließlich die Bereiche und Funktionen, für die sie berechtigt ist.

## Kernkonzept

### Workspaces

Ein Workspace ist ein eigenständiger Arbeitsbereich, beispielsweise für einen Verein, ein Unternehmen oder ein Projekt. Er besitzt eigene Mitglieder, Einstellungen, aktivierte Module, Designvorgaben und Berechtigungen.

Beispiele aus der Entwicklung:

- **stumpfWORKS:** Kalkulation, Materialien, Aufträge, Produktion und Druckeranbindung.
- **Ravens Fürstenwalde RFC:** Recruiting, Aktionen, Veranstaltungen, Bounties und Teamorganisation.
- **Weitere Organisationen:** Frei konfigurierbare Workspaces mit den jeweils benötigten Modulen.

Diese Beispiele dienen als erste Anwendungsfälle. Die Plattform soll nicht fest an sie gebunden sein.

### Rollen und Berechtigungen

FieldHub ist auf differenzierte Zugriffsrechte ausgelegt:

- Benutzer können einem oder mehreren Workspaces angehören.
- Rechte werden pro Workspace und Modul vergeben.
- Menüs und Aktionen werden anhand der Berechtigungen dargestellt.
- Personen mit nur einem Workspace können direkt in diesen Bereich gelangen.
- Der Server prüft jede geschützte Operation unabhängig von der sichtbaren Benutzeroberfläche.
- Daten unterschiedlicher Workspaces werden logisch getrennt und serverseitig isoliert.

Das Ausblenden eines Menüpunkts ist **keine Sicherheitsmaßnahme**. Die tatsächliche Zugriffskontrolle muss immer im Backend erfolgen.

### Servergesteuerte Benutzeroberfläche

FieldHub verfolgt einen **Server-Driven-UI-Ansatz** für die Konfiguration der Oberfläche. Nach erfolgreicher Anmeldung kann die App eine versionierte Bootstrap-Konfiguration abrufen, beispielsweise mit:

- verfügbaren Workspaces und Modulen,
- Navigation und Schnellaktionen,
- Farben, Logos und Designvorgaben,
- Dashboard-Bausteinen und Formularschemata,
- Funktionsschaltern und Konfigurationsversionen.

Die Clients rendern diese Konfiguration mit **vordefinierten, sicheren UI-Komponenten**. Beliebiger ausführbarer Code wird nicht vom Server nachgeladen. Neue native Fähigkeiten benötigen weiterhin ein App-Update.

## Geplante Module

| Modul | Zweck | Status |
| --- | --- | --- |
| Workspace Core | Workspaces, Navigation, Konfiguration | Android-Prototyp / Ausbau geplant |
| Identity & Access | Anmeldung, Mitgliedschaften, Rollen und Rechte | Geplant |
| Recruiting | Interessenten, Kampagnen, Erfassung und Nachverfolgung | Geplant |
| Bounties & Rewards | Nachvollziehbare Punkte, Freigaben und Belohnungen | Geplant |
| Events | Veranstaltungen, Aktionen und Teilnahme | Geplant |
| Club Management | Vereins- und Mitgliederorganisation | Geplant |
| Tasks & Operations | Aufgaben, Verantwortlichkeiten und Abläufe | Geplant |
| Orders & Production | Aufträge, Produktionsschritte und Status | Frühe Prototyp-Ideen / Ausbau geplant |
| Pricing & Materials | Materialkosten, Kalkulation und Preisgestaltung | Android-Prototyp / Ausbau geplant |
| Printing | Beleg- und Etikettendruck über unterstützte Geräte | Geplant |
| Reporting | Kennzahlen und datenschutzgerechte Auswertungen | Geplant |

Module sollen unabhängig aktivierbar und über klar definierte Schnittstellen erweiterbar sein. Nicht jede Installation benötigt alle Module.

## Clients

### Android

Der erste Client ist eine **native Android-App in Kotlin**. Ein zentrales Entwicklungsziel ist ein ressourcenschonender Betrieb auch auf älteren Android-Geräten und Tablets.

Der bisherige Prototyp dient insbesondere zur Erprobung von:

- Workspace-Auswahl und Navigation,
- unterschiedlichen Benutzerprofilen,
- JSON-basierter UI-Konfiguration,
- ersten Kalkulationsabläufen.

Die derzeitigen Testprofile und lokalen Konfigurationen sind **keine produktive Authentifizierung oder Autorisierung**.

### Web

Eine responsive Weboberfläche ist geplant, sowohl für Administration als auch für die spätere Nutzung von Modulen im Browser. Vorgesehen sind unter anderem:

- Workspace- und Benutzerverwaltung,
- Rollen- und Rechteverwaltung,
- Modul- und Formularkonfiguration,
- Dashboard- und Navigationseditor,
- operative Funktionen je nach Berechtigung.

### iOS

Ein iOS-Client ist langfristig denkbar. Gemeinsame Geschäftslogik und Schnittstellen sollen so gestaltet werden, dass zusätzliche Clients möglich bleiben. Ein konkreter iOS-Technologiestack ist noch nicht festgelegt.

## Geplante Architektur

FieldHub soll zunächst als **modularer Monolith** umgesetzt werden: ein überschaubarer Backend-Dienst mit klar getrennten Fachmodulen, statt frühzeitig eine komplexe Microservice-Landschaft aufzubauen.

```text
                   +---------------------------+
                   |       FieldHub Clients    |
                   | Android | Web | future iOS|
                   +-------------+-------------+
                                 |
                              HTTPS
                                 |
                   +-------------v-------------+
                   |       FieldHub API        |
                   |  Bootstrap / REST / Sync  |
                   +------+------+-------------+
                          |      |
                 +--------v-+  +-v----------------+
                 | OIDC IdP |  | FieldHub Modules |
                 | external |  | RBAC / Workspaces|
                 +----------+  +--------+---------+
                                       |
                               +-------v-------+
                               |  PostgreSQL   |
                               +---------------+
```

**Vorgesehene Bausteine:**

- **Backend:** API mit versionierten Endpunkten; konkrete Sprache und Framework werden noch festgelegt.
- **Datenbank:** PostgreSQL.
- **Authentifizierung:** OpenID Connect (OIDC), für öffentliche Clients mit Authorization Code Flow und PKCE.
- **Berechtigungen:** Serverseitige, workspacebezogene Zugriffskontrolle.
- **Deployment:** Docker Compose als einfacher Einstieg für Self-Hosting.
- **Reverse Proxy:** HTTPS über einen frei wählbaren Reverse Proxy.
- **Monitoring:** Optional integrierbar, beispielsweise mit Prometheus und Grafana.
- **Offline-Fähigkeit:** Lokaler Cache und später synchronisierbare Daten für unterstützte Funktionen.

### Identity-Provider

FieldHub soll mit **StumpfWorks Identity** zusammenarbeiten, aber nicht davon abhängig sein. Betreiber sollen einen kompatiblen OIDC-Provider verwenden können. Die Anmeldung bestätigt die Identität; FieldHub verwaltet seine eigenen Workspace-Mitgliedschaften und fachlichen Berechtigungen.

### Offline-Nutzung

Eine spätere Offline-First-Architektur soll definierte Funktionen ohne ständige Verbindung ermöglichen. Geplant sind lokale Speicherung, nachvollziehbare Synchronisation und Konfliktbehandlung.

Offline-Zugriffe müssen zeitlich und funktional begrenzt werden, insbesondere wenn Berechtigungen zwischenzeitlich entzogen wurden. Sensible Aktionen können eine erneute Online-Prüfung erfordern.

## Beispiel: Bootstrap-Konfiguration

Die folgende Antwort ist ein **vereinfachtes Entwurfsbeispiel**, kein bereits stabil implementierter API-Vertrag:

```json
{
  "schemaVersion": 1,
  "user": {
    "id": "user-123",
    "displayName": "Example User"
  },
  "workspaces": [
    {
      "id": "ravens",
      "name": "Ravens RFC",
      "theme": {
        "primary": "#85C9D9"
      },
      "modules": [
        { "id": "recruiting", "title": "Recruiting", "enabled": true },
        { "id": "bounties", "title": "Bounties", "enabled": true }
      ]
    }
  ]
}
```

Die Konfiguration bestimmt die **Darstellung**, nicht die eigentliche Autorisierung. Der Server erzwingt die Rechte unabhängig von dieser Antwort.

## Self-Hosting

FieldHub wird mit dem Ziel entwickelt, auf eigener Infrastruktur betrieben werden zu können – etwa auf einem Linux-Server, in einer VM oder auf Proxmox mit Docker.

Für die StumpfWorks-Entwicklungsumgebung sind perspektivisch folgende Adressen vorgesehen:

- `api.fieldhub.stumpfworks.de` – API und Bootstrap-Endpunkte.
- `fieldhub.stumpfworks.de` – Weboberfläche.

Diese Domains sind **Beispiele der geplanten Projektinstallation**, keine Voraussetzung für andere Betreiber und keine Zusage, dass die Dienste bereits öffentlich erreichbar sind.

Ein fertiges Produktions-Deployment inklusive Installationsanleitung wird im Verlauf der Backend-Entwicklung ergänzt.

## Sicherheit und Datenschutz

Sicherheit und Datenschutz sind grundlegende Anforderungen, insbesondere bei personenbezogenen Daten aus Recruiting und Vereinsverwaltung.

Geplante Prinzipien:

- durchgängiges HTTPS und sichere Token-Verarbeitung,
- OIDC-basierte Anmeldung und serverseitige Rechteprüfung,
- konsequente Mandanten- und Workspace-Isolation,
- Validierung aller Eingaben und API-Anfragen,
- Audit-Protokolle für sicherheitsrelevante Vorgänge,
- Datenminimierung und rollenbezogener Zugriff,
- sichere Backups und Wiederherstellungsverfahren,
- Rate-Limits und Schutz vor Missbrauch,
- dokumentierte Datenaufbewahrung und Löschkonzepte,
- regelmäßige Sicherheitsprüfungen vor produktivem Einsatz.

Die konkrete DSGVO-konforme Konfiguration und die Pflichten der jeweiligen Betreiber müssen vor einer produktiven Verarbeitung personenbezogener Daten geprüft werden.

## Roadmap

Die Versionsplanung ist vorläufig und kann sich während der Entwicklung ändern.

| Version | Geplanter Schwerpunkt |
| --- | --- |
| **v0.4** | Dynamische Workspaces und JSON-gesteuerte Android-Navigation |
| **v0.5** | FieldHub Core API, Datenmodelle und PostgreSQL |
| **v0.6** | OIDC-Anmeldung, Rollen und serverseitige Zugriffsprüfung |
| **v0.7** | Webadministration und Konfigurationseditor |
| **v0.8** | Synchronisation und erste durchgängige Fachmodule |
| **Später** | Erweiterte Module, Integrationen, zusätzliche Clients und produktionsreifes Self-Hosting |

**Priorität:** Zuerst ein belastbarer Kern, danach schrittweise echte Anwendungsfälle. Eine hohe Versionsnummer allein bedeutet keine Produktionsreife.

## Repository-Struktur (Zielbild)

```text
fieldhub/
├── apps/
│   ├── android/        # Nativer Android-Client
│   └── web/            # Weboberfläche (geplant)
├── services/
│   └── api/            # FieldHub Backend (geplant)
├── packages/
│   └── contracts/      # Gemeinsame API-/Konfigurationsschemata (geplant)
├── deploy/
│   └── docker/         # Self-Hosting-Konfiguration (geplant)
├── docs/               # Architektur und Dokumentation
└── README.md
```

Diese Struktur ist ein Vorschlag. Verzeichnisse sollten erst angelegt werden, wenn die entsprechenden Komponenten entstehen.

## Mitmachen

FieldHub befindet sich in einer frühen Entwicklungsphase. Ideen, konstruktives Feedback, Fehlerberichte und spätere Beiträge sind willkommen. Mit der Veröffentlichung erster nutzbarer Komponenten werden Entwicklungsanleitungen, Issue-Vorlagen und Beitragsregeln ergänzt.

## Lizenz

FieldHub ist als **Open-Source-Projekt** geplant. **Apache License 2.0** ist derzeit der bevorzugte Lizenzkandidat; die endgültige Lizenzentscheidung und eine entsprechende `LICENSE`-Datei stehen noch aus.

**Bis eine Lizenzdatei veröffentlicht wurde, werden durch die bloße öffentliche Bereitstellung des Repositorys keine allgemeinen Nutzungs-, Änderungs- oder Weiterverbreitungsrechte eingeräumt.**

---

**StumpfWorks FieldHub** — *One platform. Many workspaces.*
