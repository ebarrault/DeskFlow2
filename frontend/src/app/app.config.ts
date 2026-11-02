import { APP_INITIALIZER, ApplicationConfig, inject, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { AuthService } from './core/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    // Recharge la session avant le premier routage (les gardes en ont besoin)
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => {
        const auth = inject(AuthService);
        return () => auth.restore();
      },
    },
  ],
};
