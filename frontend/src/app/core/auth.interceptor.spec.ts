import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor, errorMessage } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'login', children: [] }]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  it('ajoute le jeton aux appels de l’API uniquement', () => {
    localStorage.setItem('helpdesk.token', 'abc');
    http.get('/api/tickets').subscribe();
    http.get('https://exemple.com/data').subscribe();
    expect(backend.expectOne('/api/tickets').request.headers.get('Authorization')).toBe('Bearer abc');
    expect(backend.expectOne('https://exemple.com/data').request.headers.has('Authorization')).toBeFalse();
  });

  it('efface la session sur une réponse 401', () => {
    localStorage.setItem('helpdesk.token', 'abc');
    http.get('/api/tickets').subscribe({ error: () => undefined });
    backend.expectOne('/api/tickets').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(localStorage.getItem('helpdesk.token')).toBeNull();
  });

  it('extrait le message d’erreur de l’API', () => {
    const err = new HttpErrorResponse({ status: 400, error: { message: 'Transition interdite : CLOSED → OPEN' } });
    expect(errorMessage(err)).toBe('Transition interdite : CLOSED → OPEN');
    expect(errorMessage(new HttpErrorResponse({ status: 503 }))).toBe('Erreur 503');
  });
});
