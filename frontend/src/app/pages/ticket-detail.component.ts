import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom, Observable } from 'rxjs';
import { errorMessage } from '../core/auth.interceptor';
import { AuthService } from '../core/auth.service';
import { describeValue, EVENT_LABEL, PRIORITY_LABEL, STATUS_LABEL } from '../core/labels';
import { Priority, Status, TicketDetail, UserRef } from '../core/models';
import { TicketService } from '../core/ticket.service';
import { BadgeComponent } from '../shared/badge.component';

@Component({
  selector: 'app-ticket-detail',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, BadgeComponent],
  template: `
    @if (ticket(); as t) {
      <header class="page-head">
        <h2>#{{ t.id }} — {{ t.title }}</h2>
        <div class="badges">
          <app-badge kind="status" [value]="t.status" />
          <app-badge kind="prio" [value]="t.priority" />
          <app-badge kind="sla" [value]="t.sla" />
        </div>
      </header>
      @if (error()) { <p class="error">{{ error() }}</p> }

      <div class="grid-2">
        <section>
          <div class="card">
            <p class="meta">Par <strong>{{ t.requester.name }}</strong> le {{ t.createdAt | date: 'short' }} · {{ t.category }}</p>
            <p class="pre">{{ t.description }}</p>
          </div>

          <h3>Échanges</h3>
          @for (c of t.comments; track c.id) {
            <div class="card comment" [class.internal]="c.internal">
              <p class="meta"><strong>{{ c.author.name }}</strong> · {{ c.createdAt | date: 'short' }}
                @if (c.internal) { <span class="badge">Note interne</span> }
              </p>
              <p class="pre">{{ c.body }}</p>
            </div>
          }
          @if (t.status !== 'CLOSED') {
            <form class="card form" (ngSubmit)="sendComment()">
              <textarea rows="4" name="body" placeholder="Votre réponse…" [(ngModel)]="commentBody"></textarea>
              @if (auth.isStaff()) {
                <label class="check"><input type="checkbox" name="internal" [(ngModel)]="internal" /> Note interne (invisible pour le demandeur)</label>
              }
              <button type="submit" [disabled]="!commentBody.trim()">Envoyer</button>
            </form>
          }
        </section>

        <aside>
          @if (auth.isStaff()) {
            <div class="card form">
              <h3>Traitement</h3>
              <label>Statut
                <select [ngModel]="t.status" (ngModelChange)="patch({ status: $event })" [disabled]="t.status === 'CLOSED'">
                  <option [value]="t.status">{{ statusLabel[t.status] }}</option>
                  @for (s of t.nextStatuses; track s) { <option [value]="s">{{ statusLabel[s] }}</option> }
                </select>
              </label>
              <label>Priorité
                <select [ngModel]="t.priority" (ngModelChange)="patch({ priority: $event })">
                  @for (p of priorities; track p[0]) { <option [value]="p[0]">{{ p[1] }}</option> }
                </select>
              </label>
              <label>Technicien
                <select [ngModel]="t.assignee?.id ?? ''" (ngModelChange)="assign($event)">
                  <option value="">— Non affecté —</option>
                  @for (s of staff(); track s.id) { <option [value]="s.id">{{ s.name }}</option> }
                </select>
              </label>
              @if (t.assignee?.id !== auth.user()?.id) {
                <button type="button" (click)="assign(auth.user()!.id)">Me l'affecter</button>
              }
            </div>
          }

          <div class="card">
            <h3>Délais</h3>
            <dl>
              <dt>Prise en charge avant</dt><dd>{{ t.responseDueAt | date: 'short' }}</dd>
              <dt>Prise en charge le</dt><dd>{{ (t.firstResponseAt | date: 'short') ?? '—' }}</dd>
              <dt>Résolution avant</dt><dd>{{ t.resolutionDueAt | date: 'short' }}</dd>
              <dt>Résolu le</dt><dd>{{ (t.resolvedAt | date: 'short') ?? '—' }}</dd>
            </dl>
          </div>

          <div class="card">
            <h3>Pièces jointes</h3>
            <ul class="files">
              @for (a of t.attachments; track a.id) {
                <li><button class="link" (click)="download(a.id, a.originalName)">{{ a.originalName }}</button>
                  <small> {{ a.size / 1024 | number: '1.0-0' }} Ko</small></li>
              }
            </ul>
            @if (t.status !== 'CLOSED') { <input type="file" (change)="upload($event)" /> }
          </div>

          <div class="card">
            <h3>Historique</h3>
            <ol class="timeline">
              @for (e of t.events; track e.id) {
                <li>
                  <small>{{ e.createdAt | date: 'short' }}</small>
                  <div>{{ e.actor?.name ?? 'Système' }} {{ eventLabel[e.type] ?? e.type }}
                    @if (e.fromValue) { : {{ describe(e.fromValue) }} → {{ describe(e.toValue) }} }
                    @else if (e.toValue) { : {{ describe(e.toValue) }} }
                  </div>
                </li>
              }
            </ol>
          </div>
        </aside>
      </div>
    } @else {
      <p>{{ error() || 'Chargement…' }}</p>
    }
  `,
})
export class TicketDetailComponent implements OnInit {
  /** Paramètre de route :id (withComponentInputBinding) */
  readonly id = input.required<string>();

  protected readonly auth = inject(AuthService);
  private readonly tickets = inject(TicketService);

  protected readonly statusLabel = STATUS_LABEL;
  protected readonly eventLabel = EVENT_LABEL;
  protected readonly priorities = Object.entries(PRIORITY_LABEL);
  protected readonly describe = describeValue;

  readonly ticket = signal<TicketDetail | null>(null);
  readonly staff = signal<UserRef[]>([]);
  readonly error = signal('');
  commentBody = '';
  internal = false;

  ngOnInit(): void {
    this.load();
    if (this.auth.isStaff()) this.tickets.staff().subscribe((s) => this.staff.set(s));
  }

  private get ticketId(): number {
    return Number(this.id());
  }

  load(): void {
    this.tickets.get(this.ticketId).subscribe({
      next: (t) => this.ticket.set(t),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  private async run(action: Observable<unknown>): Promise<void> {
    this.error.set('');
    try {
      await firstValueFrom(action);
      this.load();
    } catch (e) {
      this.error.set(errorMessage(e));
      this.load(); // remet les listes déroulantes dans l'état réel
    }
  }

  patch(body: { status?: Status; priority?: Priority }): void {
    this.run(this.tickets.update(this.ticketId, body));
  }

  assign(value: number | string): void {
    const id = Number(value);
    this.run(this.tickets.update(this.ticketId, id ? { assigneeId: id } : { unassign: true }));
  }

  async sendComment(): Promise<void> {
    await this.run(this.tickets.comment(this.ticketId, this.commentBody, this.internal));
    this.commentBody = '';
    this.internal = false;
  }

  upload(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.run(this.tickets.upload(this.ticketId, file));
  }

  download(attachmentId: number, name: string): void {
    this.tickets.download(this.ticketId, attachmentId).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = name;
      a.click();
      URL.revokeObjectURL(url);
    });
  }
}
