import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { ROLE_LABEL } from './core/labels';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    @if (auth.user(); as user) {
      <div class="layout">
        <aside class="sidebar">
          <h1>Helpdesk</h1>
          <nav>
            @if (auth.isStaff()) {
              <a routerLink="/dashboard" routerLinkActive="active">Tableau de bord</a>
            }
            <a routerLink="/tickets" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">
              {{ auth.isStaff() ? 'Tickets' : 'Mes demandes' }}
            </a>
            <a routerLink="/tickets/new" routerLinkActive="active">Nouvelle demande</a>
            @if (auth.isAdmin()) {
              <a routerLink="/users" routerLinkActive="active">Utilisateurs</a>
            }
            <a routerLink="/profile" routerLinkActive="active">Mon compte</a>
          </nav>
          <div class="me">
            <strong>{{ user.name }}</strong>
            <span>{{ roleLabel[user.role] }}</span>
            <button class="link" (click)="logout()">Se déconnecter</button>
          </div>
        </aside>
        <main class="content"><router-outlet /></main>
      </div>
    } @else {
      <router-outlet />
    }
  `,
})
export class AppComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly roleLabel = ROLE_LABEL;

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
