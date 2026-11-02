export type Role = 'USER' | 'TECHNICIAN' | 'ADMIN';
export type Status = 'OPEN' | 'IN_PROGRESS' | 'WAITING' | 'RESOLVED' | 'CLOSED';
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type Sla = 'OK' | 'AT_RISK' | 'BREACHED' | 'MET' | 'MISSED';

export interface User {
  id: number;
  email: string;
  name: string;
  role: Role;
  active: boolean;
  twoFactorEnabled: boolean;
}

export interface UserRef {
  id: number;
  name: string;
  email?: string;
}

export interface TicketSummary {
  id: number;
  title: string;
  category: string | null;
  status: Status;
  priority: Priority;
  requester: UserRef;
  assignee: UserRef | null;
  responseDueAt: string;
  resolutionDueAt: string;
  firstResponseAt: string | null;
  resolvedAt: string | null;
  createdAt: string;
  sla: Sla;
}

export interface TicketDetail extends TicketSummary {
  description: string;
  closedAt: string | null;
  nextStatuses: Status[];
  comments: { id: number; body: string; internal: boolean; createdAt: string; author: UserRef; authorRole: Role }[];
  attachments: { id: number; originalName: string; mimeType: string; size: number; createdAt: string; uploader: UserRef }[];
  events: { id: number; type: string; fromValue: string | null; toValue: string | null; createdAt: string; actor: UserRef | null }[];
}

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface LoginResponse {
  requires2fa?: boolean;
  tempToken?: string;
  accessToken?: string;
  user?: User;
}

export interface Stats {
  activeTickets: number;
  breachedTickets: number;
  slaCompliance: number | null;
  avgResolutionHours: number | null;
  byStatus: { key: Status; count: number }[];
  byPriority: { key: Priority; count: number }[];
  perDay: { date: string; created: number; resolved: number }[];
  workload: { key: string; count: number }[];
}
