import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../core/auth.interceptor';
import { AuthService } from '../core/auth.service';
import { ROLE_LABEL } from '../core/labels';

interface Enrollment {
  secret: string;
  otpauthUrl: string;
  qrCode: string;
}

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2>Mon compte</h2>
    @if (auth.user(); as u) {
      <div class="card"><p><strong>{{ u.name }}</strong> — {{ u.email }} — {{ roleLabel[u.role] }}</p></div>

      <div class="card form">
        <h3>Double authentification (TOTP)</h3>
        @if (message()) { <p class="success">{{ message() }}</p> }
        @if (error()) { <p class="error">{{ error() }}</p> }

        @if (u.twoFactorEnabled) {
          <p>La double authentification est <strong>active</strong>. Saisissez un code pour la désactiver.</p>
          <div class="row">
            <input name="code" maxlength="6" placeholder="123456" [(ngModel)]="code" />
            <button class="danger" (click)="confirm('disable')">Désactiver</button>
          </div>
        } @else {
          @if (enrollment(); as e) {
            <p>Scannez ce QR code avec Google Authenticator, Microsoft Authenticator ou FreeOTP, puis saisissez le code affiché.</p>
            <img [src]="e.qrCode" alt="QR code de double authentification" width="200" height="200" />
            <p class="muted">Clé manuelle : <code>{{ e.secret }}</code></p>
            <div class="row">
              <input name="code" maxlength="6" placeholder="123456" [(ngModel)]="code" />
              <button (click)="confirm('enable')">Activer</button>
            </div>
          } @else {
            <button (click)="start()">Configurer la double authentification</button>
          }
        }
      </div>
    }
  `,
})
export class ProfileComponent {
  protected readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  protected readonly roleLabel = ROLE_LABEL;

  readonly enrollment = signal<Enrollment | null>(null);
  readonly message = signal('');
  readonly error = signal('');
  code = '';

  async start(): Promise<void> {
    this.error.set('');
    try {
      this.enrollment.set(await firstValueFrom(this.http.post<Enrollment>('/api/auth/2fa/setup', {})));
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }

  async confirm(action: 'enable' | 'disable'): Promise<void> {
    this.error.set('');
    try {
      await firstValueFrom(this.http.post(`/api/auth/2fa/${action}`, { code: this.code }));
      this.message.set(action === 'enable' ? 'Double authentification activée.' : 'Double authentification désactivée.');
      this.enrollment.set(null);
      this.code = '';
      await this.auth.restore();
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }
}
