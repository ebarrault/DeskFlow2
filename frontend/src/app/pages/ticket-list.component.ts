import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { errorMessage } from '../core/auth.interceptor';
import { AuthService } from '../core/auth.service';
import { PRIORITY_LABEL, STATUS_LABEL } from '../core/labels';
import { Page, Priority, Status, TicketSummary } from '../core/models';
import { TicketService } from '../core/ticket.service';
import { BadgeComponent } from '../shared/badge.component';

@Component({
  selector: 'app-ticket-list',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, BadgeComponent],
  template: `
    <header class="page-head">
      <h2>{{ auth.isStaff() ? 'Tickets' : 'Mes demandes' }}</h2>
      <a class="btn" routerLink="/tickets/new">+ Nouvelle demande</a>
    </header>

    <div class="filters">
      <input placeholder="Rechercher…" [ngModel]="search()" (ngModelChange)="searchInput.next($event)" />
      <select [ngModel]="status()" (ngModelChange)="status.set($event); page.set(1)">
        <option value="">Tous les statuts</option>
        @for (s of statuses; track s[0]) { <option [value]="s[0]">{{ s[1] }}</option> }
      </select>
      <select [ngModel]="priority()" (ngModelChange)="priority.set($event); page.set(1)">
        <option value="">Toutes priorités</option>
        @for (p of priorities; track p[0]) { <option [value]="p[0]">{{ p[1] }}</option> }
      </select>
      @if (auth.isStaff()) {
        <label class="check"><input type="checkbox" [ngModel]="mine()" (ngModelChange)="mine.set($event); page.set(1)" /> Affectés à moi</label>
        <label class="check"><input type="checkbox" [ngModel]="late()" (ngModelChange)="late.set($event); page.set(1)" /> En retard</label>
      }
    </div>

    @if (error()) { <p class="error">{{ error() }}</p> }

    <table class="table">
      <thead>
        <tr><th>#</th><th>Titre</th><th>Statut</th><th>Priorité</th><th>Délai</th><th>Demandeur</th><th>Technicien</th><th>Créé le</th></tr>
      </thead>
      <tbody>
        @for (t of data()?.items ?? []; track t.id) {
          <tr>
            <td>{{ t.id }}</td>
            <td><a [routerLink]="['/tickets', t.id]">{{ t.title }}</a></td>
            <td><app-badge kind="status" [value]="t.status" /></td>
            <td><app-badge kind="prio" [value]="t.priority" /></td>
            <td><app-badge kind="sla" [value]="t.sla" /></td>
            <td>{{ t.requester.name }}</td>
            <td>{{ t.assignee?.name ?? 'Non affecté' }}</td>
            <td>{{ t.createdAt | date: 'short' }}</td>
          </tr>
        } @empty {
          <tr><td colspan="8" class="muted">Aucun ticket</td></tr>
        }
      </tbody>
    </table>

    <div class="pager">
      <button [disabled]="page() <= 1" (click)="page.set(page() - 1)">‹</button>
      <span>Page {{ page() }} / {{ pages() }} — {{ data()?.total ?? 0 }} ticket(s)</span>
      <button [disabled]="page() >= pages()" (click)="page.set(page() + 1)">›</button>
    </div>
  `,
})
export class TicketListComponent {
  protected readonly auth = inject(AuthService);
  private readonly tickets = inject(TicketService);

  protected readonly statuses = Object.entries(STATUS_LABEL);
  protected readonly priorities = Object.entries(PRIORITY_LABEL);

  readonly search = signal('');
  readonly status = signal<Status | ''>('');
  readonly priority = signal<Priority | ''>('');
  readonly mine = signal(false);
  readonly late = signal(false);
  readonly page = signal(1);
  readonly data = signal<Page<TicketSummary> | null>(null);
  readonly error = signal('');
  readonly searchInput = new Subject<string>();

  constructor() {
    // Recherche avec délai de frappe
    this.searchInput.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((v) => {
      this.search.set(v);
      this.page.set(1);
    });
    // Rechargement automatique dès qu'un filtre change
    effect(() => {
      this.tickets
        .list({
          search: this.search(),
          status: this.status(),
          priority: this.priority(),
          assigneeId: this.mine() ? this.auth.user()?.id : undefined,
          slaBreached: this.late(),
          page: this.page(),
        })
        .subscribe({ next: (d) => this.data.set(d), error: (e) => this.error.set(errorMessage(e)) });
    }, { allowSignalWrites: true });
  }

  pages(): number {
    const d = this.data();
    return d ? Math.max(1, Math.ceil(d.total / d.pageSize)) : 1;
  }
}
