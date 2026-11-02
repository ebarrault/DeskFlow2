import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../core/auth.interceptor';
import { PRIORITY_LABEL } from '../core/labels';
import { Priority } from '../core/models';
import { TicketService } from '../core/ticket.service';

@Component({
  selector: 'app-new-ticket',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <h2>Nouvelle demande</h2>
    <form class="card form" [formGroup]="form" (ngSubmit)="submit()">
      <label>Titre
        <input formControlName="title" />
        @if (form.controls.title.touched && form.controls.title.invalid) { <small class="error">5 à 150 caractères</small> }
      </label>
      <div class="row">
        <label>Catégorie
          <select formControlName="category">
            @for (c of categories; track c) { <option>{{ c }}</option> }
          </select>
        </label>
        <label>Priorité
          <select formControlName="priority">
            @for (p of priorities; track p[0]) { <option [value]="p[0]">{{ p[1] }}</option> }
          </select>
        </label>
      </div>
      <label>Description
        <textarea rows="8" formControlName="description"></textarea>
        @if (form.controls.description.touched && form.controls.description.invalid) { <small class="error">10 caractères minimum</small> }
      </label>
      <label>Pièce jointe (facultatif) <input type="file" (change)="onFile($event)" /></label>
      @if (error()) { <p class="error">{{ error() }}</p> }
      <button type="submit" [disabled]="form.invalid || busy()">Envoyer</button>
    </form>
  `,
})
export class NewTicketComponent {
  private readonly tickets = inject(TicketService);
  private readonly router = inject(Router);

  protected readonly categories = ['Matériel', 'Logiciel', 'Réseau', 'Comptes', 'Messagerie', 'Autre'];
  protected readonly priorities = Object.entries(PRIORITY_LABEL);

  readonly form = inject(FormBuilder).nonNullable.group({
    title: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(150)]],
    description: ['', [Validators.required, Validators.minLength(10)]],
    priority: ['MEDIUM' as Priority],
    category: ['Matériel'],
  });
  readonly error = signal('');
  readonly busy = signal(false);
  private file: File | null = null;

  onFile(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  async submit(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const t = await firstValueFrom(this.tickets.create(this.form.getRawValue()));
      if (this.file) await firstValueFrom(this.tickets.upload(t.id, this.file));
      await this.router.navigate(['/tickets', t.id]);
    } catch (e) {
      this.error.set(errorMessage(e));
      this.busy.set(false);
    }
  }
}
