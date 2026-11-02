import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { User } from './models';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  const user: User = { id: 1, email: 'tech@helpdesk.local', name: 'Thomas', role: 'TECHNICIAN', active: true, twoFactorEnabled: false };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('connexion sans 2FA : enregistre le jeton et l’utilisateur', async () => {
    const promise = service.login('tech@helpdesk.local', 'Tech123!');
    http.expectOne('/api/auth/login').flush({ accessToken: 'jwt-1', user });

    expect(await promise).toBeNull();
    expect(service.token).toBe('jwt-1');
    expect(service.isStaff()).toBeTrue();
    expect(service.isAdmin()).toBeFalse();
  });

  it('connexion avec 2FA : renvoie le jeton temporaire sans ouvrir de session', async () => {
    const promise = service.login('tech@helpdesk.local', 'Tech123!');
    http.expectOne('/api/auth/login').flush({ requires2fa: true, tempToken: 'temp' });

    expect(await promise).toBe('temp');
    expect(service.isLoggedIn()).toBeFalse();

    const verify = service.verify2fa('temp', '123456');
    const req = http.expectOne('/api/auth/2fa/verify');
    expect(req.request.body).toEqual({ tempToken: 'temp', code: '123456' });
    req.flush({ accessToken: 'jwt-2', user });
    await verify;
    expect(service.isLoggedIn()).toBeTrue();
  });

  it('déconnexion : efface la session', () => {
    localStorage.setItem('helpdesk.token', 'x');
    service.user.set(user);
    service.logout();
    expect(service.token).toBeNull();
    expect(service.isLoggedIn()).toBeFalse();
  });

  it('restaure la session au démarrage, ou l’efface si le jeton est refusé', async () => {
    localStorage.setItem('helpdesk.token', 'expired');
    const promise = service.restore();
    http.expectOne('/api/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    await promise;
    expect(service.token).toBeNull();
  });
});
