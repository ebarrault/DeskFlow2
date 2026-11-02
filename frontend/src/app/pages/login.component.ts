import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { errorMessage } from '../core/auth.interceptor';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="login">
      <form class="card form" (ngSubmit)="submit()">
        <h1>Helpdesk</h1>
        @if (!tempToken()) {
          <label>E-mail <input type="email" name="email" [(ngModel)]="email" required autofocus /></label>
          <label>Mot de passe <input type="password" name="password" [(ngModel)]="password" required /></label>
        } @else {
          <p>Saisissez le code à 6 chiffres de votre application d'authentification.</p>
          <label>Code <input name="code" inputmode="numeric" maxlength="6" pattern="\\d{6}" [(ngModel)]="code" required autofocus /></label>
        }
        @if (error()) {
          <p class="error">{{ error() }}</p>
        }
        <button type="submit" [disabled]="busy()">{{ tempToken() ? 'Vérifier' : 'Se connecter' }}</button>
        @if (tempToken()) {
          <button type="button" class="link" (click)="tempToken.set(null)">Retour</button>
        }
        <p class="hint">Démo : admin&#64;helpdesk.local / Admin123! — tech&#64;helpdesk.local / Tech123! — user&#64;helpdesk.local / User123!</p>
      </form>
    </div>
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  email = '';
  password = '';
  code = '';
  readonly tempToken = signal<string | null>(null);
  readonly error = signal('');
  readonly busy = signal(false);

  async submit(): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      const temp = this.tempToken();
      if (temp) {
        await this.auth.verify2fa(temp, this.code);
      } else {
        const next = await this.auth.login(this.email, this.password);
        if (next) {
          this.tempToken.set(next);
          return;
        }
      }
      await this.router.navigateByUrl(this.auth.isStaff() ? '/dashboard' : '/tickets');
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
