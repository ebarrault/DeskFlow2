import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, Priority, Stats, Status, TicketDetail, TicketSummary, User, UserRef } from './models';

export interface TicketFilters {
  status?: Status | '';
  priority?: Priority | '';
  search?: string;
  assigneeId?: number;
  slaBreached?: boolean;
  page?: number;
}

@Injectable({ providedIn: 'root' })
export class TicketService {
  private readonly http = inject(HttpClient);

  list(f: TicketFilters): Observable<Page<TicketSummary>> {
    let params = new HttpParams().set('page', f.page ?? 1).set('pageSize', 20);
    if (f.status) params = params.set('status', f.status);
    if (f.priority) params = params.set('priority', f.priority);
    if (f.search) params = params.set('search', f.search);
    if (f.assigneeId) params = params.set('assigneeId', f.assigneeId);
    if (f.slaBreached) params = params.set('slaBreached', true);
    return this.http.get<Page<TicketSummary>>('/api/tickets', { params });
  }

  get(id: number): Observable<TicketDetail> {
    return this.http.get<TicketDetail>(`/api/tickets/${id}`);
  }

  create(body: { title: string; description: string; priority: Priority; category: string }): Observable<TicketSummary> {
    return this.http.post<TicketSummary>('/api/tickets', body);
  }

  update(id: number, body: { status?: Status; priority?: Priority; assigneeId?: number; unassign?: boolean }): Observable<TicketSummary> {
    return this.http.patch<TicketSummary>(`/api/tickets/${id}`, body);
  }

  comment(id: number, body: string, internal: boolean): Observable<unknown> {
    return this.http.post(`/api/tickets/${id}/comments`, { body, internal });
  }

  upload(id: number, file: File): Observable<unknown> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post(`/api/tickets/${id}/attachments`, form);
  }

  download(ticketId: number, attachmentId: number): Observable<Blob> {
    return this.http.get(`/api/tickets/${ticketId}/attachments/${attachmentId}`, { responseType: 'blob' });
  }

  staff(): Observable<UserRef[]> {
    return this.http.get<UserRef[]>('/api/users/staff');
  }

  stats(): Observable<Stats> {
    return this.http.get<Stats>('/api/stats/overview');
  }

  users(): Observable<User[]> {
    return this.http.get<User[]>('/api/users');
  }
}
