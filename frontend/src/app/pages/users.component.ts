import { HttpClient } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../core/auth.interceptor';
import { ROLE_LABEL } from '../core/labels';
import { Role, User } from '../core/models';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <h2>Utilisateurs</h2>
    @if (error()) { <p class="error">{{ error() }}</p> }
    <table class="table">
      <thead><tr><th>Nom</th><th>E-mail</th><th>Rôle</th><th>2FA</th><th>Actif</th><th></th></tr></thead>
      <tbody>
        @for (u of users(); track u.id) {
          <tr [class.inactive]="!u.active">
            <td>{{ u.name }}</td>
            <td>{{ u.email }}</td>
            <td>
              <select [value]="u.role" (change)="update(u, { role: $any($event.target).value })">
                @for (r of roles; track r[0]) { <option [value]="r[0]">{{ r[1] }}</option> }
              </select>
            </td>
            <td>{{ u.twoFactorEnabled ? '✔' : '—' }}</td>
            <td><input type="checkbox" [checked]="u.active" (change)="update(u, { active: $any($event.target).checked })" /></td>
            <td>
              @if (u.twoFactorEnabled) { <button class="link" (click)="reset2fa(u)">Réinitialiser la 2FA</button> }
            </td>
          </tr>
        }
      </tbody>
    </table>

    <form class="card form" [formGroup]="form" (ngSubmit)="create()">
      <h3>Ajouter un utilisateur</h3>
      <div class="row">
        <label>Nom <input formControlName="name" /></label>
        <label>E-mail <input type="email" formControlName="email" /></label>
      </div>
      <div class="row">
        <label>Rôle
          <select formControlName="role">
            @for (r of roles; track r[0]) { <option [value]="r[0]">{{ r[1] }}</option> }
          </select>
        </label>
        <label>Mot de passe initial <input type="password" formControlName="password" /></label>
      </div>
      <button type="submit" [disabled]="form.invalid">Créer</button>
    </form>
  `,
})
export class UsersComponent implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly roles = Object.entries(ROLE_LABEL);
  readonly users = signal<User[]>([]);
  readonly error = signal('');

  readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    role: ['USER' as Role],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.http.get<User[]>('/api/users').subscribe({ next: (u) => this.users.set(u), error: (e) => this.error.set(errorMessage(e)) });
  }

  private async run(action: Promise<unknown>): Promise<void> {
    this.error.set('');
    try {
      await action;
    } catch (e) {
      this.error.set(errorMessage(e));
    }
    this.load();
  }

  update(u: User, body: Partial<Pick<User, 'role' | 'active'>>): void {
    this.run(firstValueFrom(this.http.patch(`/api/users/${u.id}`, body)));
  }

  reset2fa(u: User): void {
    this.run(firstValueFrom(this.http.post(`/api/users/${u.id}/reset-2fa`, {})));
  }

  create(): void {
    this.run(firstValueFrom(this.http.post('/api/users', this.form.getRawValue())).then(() => this.form.reset()));
  }
}
