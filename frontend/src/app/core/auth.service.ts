import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { isStaff } from './labels';
import { LoginResponse, User } from './models';

const TOKEN_KEY = 'helpdesk.token';

/** Session : jeton JWT en localStorage, utilisateur courant en signal. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly user = signal<User | null>(null);
  readonly isLoggedIn = computed(() => this.user() !== null);
  readonly isStaff = computed(() => isStaff(this.user()?.role));
  readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  /** Au démarrage : recharge l'utilisateur si un jeton est présent. */
  async restore(): Promise<void> {
    if (!this.token) return;
    try {
      this.user.set(await firstValueFrom(this.http.get<User>('/api/auth/me')));
    } catch {
      this.logout();
    }
  }

  /** Étape 1. Renvoie le jeton temporaire si la double authentification est requise. */
  async login(email: string, password: string): Promise<string | null> {
    const r = await firstValueFrom(this.http.post<LoginResponse>('/api/auth/login', { email, password }));
    if (r.requires2fa && r.tempToken) return r.tempToken;
    this.finish(r);
    return null;
  }

  /** Étape 2 : code TOTP. */
  async verify2fa(tempToken: string, code: string): Promise<void> {
    this.finish(await firstValueFrom(this.http.post<LoginResponse>('/api/auth/2fa/verify', { tempToken, code })));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.user.set(null);
  }

  private finish(r: LoginResponse): void {
    if (!r.accessToken || !r.user) throw new Error('Réponse de connexion inattendue');
    localStorage.setItem(TOKEN_KEY, r.accessToken);
    this.user.set(r.user);
  }
}
