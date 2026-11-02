# FieldHub Web

Eigenständiger Web-Client (erste Ausbaustufe). Das Frontend ist vom FastAPI-Entwicklungstemplate getrennt.

## Sicherheitsstatus

**Noch nicht öffentlich bereitstellen.** Der Web-Client benötigt vor einer öffentlichen Bereitstellung einen serverseitigen OIDC/BFF-Login mit sicheren HttpOnly/Secure/SameSite-Cookies, CSRF-Schutz, Session-Rotation und Logout. Der bisherige localhost-only Keycloak-Test bleibt ausschließlich in der API-Entwicklungsvorschau.

Die App ruft ausschließlich relative `/v1/`-Pfade auf. Der geplante BFF muss diese Endpunkte mit serverseitig verwalteten Zugangsdaten an die API weiterleiten. Keine Tokens im localStorage oder sessionStorage.

## Architektur

- `index.html`: semantischer App-Einstieg
- `src/app.js`: Workspace-Bootstrap, servergesteuerte Navigation, Dashboard und Listen
- `src/styles.css`: responsives Design
- `Dockerfile`: statischer Webserver für die spätere BFF-Integration

Die Bereitstellung auf Proxmox und die Domains werden erst nach vollständiger Authentifizierungs- und Sicherheitserprobung freigegeben.
