import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Ajoute le jeton à chaque appel /api et renvoie vers la connexion si la session a expiré. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token;
  const request = token && req.url.startsWith('/api') ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && token) {
        auth.logout();
        router.navigateByUrl('/login');
      }
      return throwError(() => err);
    }),
  );
};

/** Message d'erreur lisible à partir d'une réponse de l'API (ProblemDetail). */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    return err.error?.message ?? err.error?.detail ?? `Erreur ${err.status}`;
  }
  return err instanceof Error ? err.message : 'Erreur inattendue';
}
