import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : inject(Router).parseUrl('/login');
};

export const staffGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isStaff() ? true : inject(Router).parseUrl('/tickets');
};

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAdmin() ? true : inject(Router).parseUrl('/tickets');
};
