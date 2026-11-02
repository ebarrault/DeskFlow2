# Architecture

```mermaid
flowchart LR
  B[Navigateur<br/>Angular 18] -- /api --> N[Nginx]
  N -- proxy --> S[Spring Boot 3<br/>Java 21]
  S -- JPA / Hibernate --> P[(PostgreSQL 16)]
  F[Flyway] -- migrations --> P
  S -- SMTP --> M[Mailpit / SMTP]
  S -- fichiers --> U[(Volume uploads)]
  C["@Scheduled<br/>toutes les 5 min"] --> S
```

## Couches du backend

| Paquet | Rôle |
|---|---|
| `domain` | Entités JPA, énumérations, dépôts Spring Data |
| `sla` | **Règles métier pures** (délais, workflow) : aucune dépendance à Spring, testées en JUnit simple |
| `security` | Filtre JWT, configuration Spring Security (sans état), limitation des tentatives |
| `auth` | Connexion, double authentification TOTP |
| `ticket` | Service applicatif, contrôleur REST, filtres dynamiques (Specification), stockage des pièces jointes, tâche planifiée des délais |
| `notification` | E-mails (un échec d'envoi ne bloque jamais l'action) |
| `stats` | Indicateurs du tableau de bord |
| `common` | Erreurs au format RFC 7807 (`ProblemDetail`) |

## Sécurité

```mermaid
sequenceDiagram
  participant A as Angular
  participant F as JwtAuthFilter
  participant S as AuthService
  A->>S: POST /api/auth/login
  S-->>A: { requires2fa, tempToken (claim purpose=2fa, 5 min) }
  A->>S: POST /api/auth/2fa/verify (tempToken, code)
  S-->>A: { accessToken (8 h), user }
  A->>F: GET /api/tickets — Authorization: Bearer …
  F->>F: signature, expiration, refus si claim "purpose", compte actif
  F-->>A: 200 ou 401
```

- Contrôle d'accès à deux niveaux : **URL** (`/api/stats/**` réservé au support) et **méthode** (`@PreAuthorize` sur la gestion des comptes).
- Cloisonnement des données dans le service : un utilisateur ne lit que ses tickets, ne voit jamais les notes internes, ne peut pas en écrire.
- Pièces jointes : liste blanche de types, 10 Mo, stockage sous un nom aléatoire, téléchargement soumis aux mêmes droits que le ticket.

## Modèle de données

Géré par **Flyway** (`backend/src/main/resources/db/migration`) ; Hibernate se contente de **valider** le schéma (`ddl-auto: validate`).

```mermaid
erDiagram
  users ||--o{ tickets : "demande"
  users ||--o{ tickets : "traite"
  tickets ||--o{ comments : ""
  tickets ||--o{ attachments : ""
  tickets ||--o{ ticket_events : "historique"
```
