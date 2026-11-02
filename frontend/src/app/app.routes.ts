import { Routes } from '@angular/router';
import { adminGuard, authGuard, staffGuard } from './core/guards';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login.component').then((m) => m.LoginComponent) },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', canActivate: [staffGuard], loadComponent: () => import('./pages/dashboard.component').then((m) => m.DashboardComponent) },
      { path: 'tickets', loadComponent: () => import('./pages/ticket-list.component').then((m) => m.TicketListComponent) },
      { path: 'tickets/new', loadComponent: () => import('./pages/new-ticket.component').then((m) => m.NewTicketComponent) },
      { path: 'tickets/:id', loadComponent: () => import('./pages/ticket-detail.component').then((m) => m.TicketDetailComponent) },
      { path: 'users', canActivate: [adminGuard], loadComponent: () => import('./pages/users.component').then((m) => m.UsersComponent) },
      { path: 'profile', loadComponent: () => import('./pages/profile.component').then((m) => m.ProfileComponent) },
      { path: '', pathMatch: 'full', redirectTo: 'tickets' },
    ],
  },
  { path: '**', redirectTo: '' },
];
