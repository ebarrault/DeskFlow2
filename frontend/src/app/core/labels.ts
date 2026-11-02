import { Priority, Role, Sla, Status } from './models';

export const STATUS_LABEL: Record<Status, string> = {
  OPEN: 'Ouvert',
  IN_PROGRESS: 'En cours',
  WAITING: 'En attente',
  RESOLVED: 'Résolu',
  CLOSED: 'Fermé',
};

export const PRIORITY_LABEL: Record<Priority, string> = { LOW: 'Basse', MEDIUM: 'Moyenne', HIGH: 'Haute', CRITICAL: 'Critique' };

export const SLA_LABEL: Record<Sla, string> = {
  OK: 'Dans les délais',
  AT_RISK: 'Bientôt en retard',
  BREACHED: 'En retard',
  MET: 'Respecté',
  MISSED: 'Non respecté',
};

export const ROLE_LABEL: Record<Role, string> = { USER: 'Utilisateur', TECHNICIAN: 'Technicien', ADMIN: 'Administrateur' };

export const EVENT_LABEL: Record<string, string> = {
  CREATED: 'a créé le ticket',
  STATUS_CHANGED: 'a changé le statut',
  PRIORITY_CHANGED: 'a changé la priorité',
  ASSIGNED: 'a modifié l’affectation',
  COMMENTED: 'a commenté',
  ATTACHMENT_ADDED: 'a ajouté une pièce jointe',
  SLA_BREACHED: 'Délai dépassé',
};

export const isStaff = (role: Role | undefined | null): boolean => role === 'TECHNICIAN' || role === 'ADMIN';

/** Libellé lisible d'une valeur d'historique (statut ou priorité), sinon la valeur brute. */
export function describeValue(v: string | null): string {
  if (!v) return '';
  return (STATUS_LABEL as Record<string, string>)[v] ?? (PRIORITY_LABEL as Record<string, string>)[v] ?? v;
}
