# DeskFlow 2 — Helpdesk Spring Boot + Angular

[![CI](https://github.com/ebarrault/DeskFlow2/actions/workflows/ci.yml/badge.svg)](https://github.com/ebarrault/DeskFlow2/actions/workflows/ci.yml)

Outil de gestion de tickets pour un service informatique : les utilisateurs ouvrent des demandes, les techniciens les traitent dans des **délais fixés par priorité**, les administrateurs pilotent l'activité.

C'est la version **Java / Angular** du [helpdesk NestJS + React](https://github.com/ebarrault/DeskFlow) : même fonctionnel, autre stack, pour montrer que je sais livrer sur les deux écosystèmes les plus demandés en ESN.

**Stack** : Java 21 · Spring Boot 3.3 (Web, Data JPA, Security, Validation, Mail, Actuator) · PostgreSQL 16 · Flyway · JWT (jjwt) · TOTP · springdoc-openapi · Angular 18 (composants autonomes, signals) · Chart.js · JUnit 5 · Mockito · AssertJ · Testcontainers · Jasmine/Karma · Docker Compose · GitHub Actions

## Fonctionnalités

| Domaine | Détail |
|---|---|
| Rôles | **Utilisateur** (ses demandes), **Technicien** (tous les tickets, traitement, notes internes), **Admin** (en plus : comptes) |
| Tickets | Catégorie, priorité, statut, affectation, recherche, filtres, pagination |
| Délais (SLA) | Échéances de prise en charge et de résolution par priorité, état *dans les délais / bientôt en retard / en retard*, contrôle planifié toutes les 5 min avec alerte mail |
| Workflow | Transitions de statut contrôlées côté serveur ; un ticket fermé est définitif |
| Historique | Chaque action est tracée (création, statut, priorité, affectation, commentaire, pièce jointe, dépassement) |
| Échanges | Réponses publiques et **notes internes** invisibles pour le demandeur |
| Pièces jointes | Upload filtré (types, 10 Mo), téléchargement contrôlé |
| Notifications | Création, affectation, changement de statut, réponse, dépassement de délai |
| Tableau de bord | Tickets actifs et en retard, % de délais respectés, temps moyen de résolution, courbe sur 30 jours, charge par technicien |
| Sécurité | JWT sans état, **double authentification TOTP** avec QR code, BCrypt, blocage après 5 échecs, CORS, erreurs RFC 7807 |

| Priorité | Prise en charge | Résolution |
|---|---|---|
| Critique | 1 h | 4 h |
| Haute | 2 h | 8 h |
| Moyenne | 4 h | 24 h |
| Basse | 8 h | 72 h |

## Démarrage

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Application Angular | http://localhost:8083 |
| API + Swagger UI | http://localhost:8080/swagger-ui.html |
| Santé (Actuator) | http://localhost:8080/actuator/health |
| Boîte mail de test | http://localhost:8026 |

| Rôle | E-mail | Mot de passe |
|---|---|---|
| Admin | admin@helpdesk.local | Admin123! |
| Technicien | tech@helpdesk.local | Tech123! |
| Utilisateur | user@helpdesk.local | User123! |

### En développement

```bash
docker compose up -d db mailpit
cd backend && SEED=true mvn spring-boot:run
cd frontend && npm install && npm start            # http://localhost:4200 (proxy vers :8080)
```

## Tests

```bash
cd backend && mvn verify          # unitaires + intégration (Docker requis pour Testcontainers)
cd frontend && npm run test:ci    # Karma + Chrome sans interface
```

| Niveau | Ce qui est vérifié |
|---|---|
| **Unitaires purs** (`SlaPolicyTest`, `TicketWorkflowTest`, `LoginAttemptServiceTest`) | calcul des délais et de leur état, transitions autorisées, blocage puis déblocage après une minute |
| **Service avec Mockito** (`TicketServiceTest`, `AuthServiceTest`) | cloisonnement des tickets, notes internes masquées et non falsifiables, transitions interdites, recalcul des échéances, affectation à un technicien uniquement, arrêt du chrono à la première réponse, parcours 2FA complet avec un vrai code TOTP, force brute |
| **Intégration** (`HelpdeskApiIT`, Testcontainers) | vraie base PostgreSQL, migrations Flyway, sécurité HTTP : 401 sans jeton, 403 entre utilisateurs, cycle de vie complet d'un ticket, message d'erreur métier, statistiques |
| **Angular** (Jasmine) | service d'authentification (avec et sans 2FA, restauration de session), intercepteur (jeton, 401), composant pastille |

La CI GitHub Actions lance `mvn verify` (avec PostgreSQL en conteneur et rapport de couverture JaCoCo), les tests et le build Angular, puis la construction des images Docker.

## Structure

```
backend/src/main/java/fr/portfolio/helpdesk/
  domain/        entités JPA, énumérations, dépôts
  sla/           règles métier pures (délais, workflow)
  security/      JWT, Spring Security, anti force brute
  auth/          connexion et 2FA
  ticket/        service, contrôleur, filtres, pièces jointes, tâche planifiée
  user/ stats/ notification/ common/ config/
backend/src/main/resources/db/migration/   scripts Flyway
frontend/src/app/
  core/          modèles, services, intercepteur, gardes de routes
  pages/         connexion, liste, détail, création, tableau de bord, utilisateurs, profil
  shared/        composants réutilisables
docs/architecture.md   schémas (architecture, sécurité, données)
```

## Choix techniques

- **Règles métier hors du framework** (`sla/`) : testables en millisecondes, réutilisables, lisibles par un non-développeur.
- **Flyway + `ddl-auto: validate`** : le schéma est versionné et revu comme du code ; Hibernate ne modifie jamais la base.
- **Testcontainers** plutôt que H2 : les tests d'intégration tournent sur le même PostgreSQL qu'en production.
- **Angular moderne** : composants autonomes, `signal`/`computed`/`effect`, nouveau control flow `@if`/`@for`, `input()` liés aux paramètres de route, intercepteur fonctionnel.
- **Horloge injectée** (`Clock`) : les tests figent le temps pour vérifier les délais de façon déterministe.
