import { AfterViewInit, Component, ElementRef, inject, OnDestroy, signal, viewChild } from '@angular/core';
import { Chart, registerables } from 'chart.js';
import { errorMessage } from '../core/auth.interceptor';
import { PRIORITY_LABEL, STATUS_LABEL } from '../core/labels';
import { Stats } from '../core/models';
import { TicketService } from '../core/ticket.service';

Chart.register(...registerables);

const PRIORITY_COLORS: Record<string, string> = { LOW: '#94a3b8', MEDIUM: '#3b82f6', HIGH: '#f59e0b', CRITICAL: '#dc2626' };

@Component({
  selector: 'app-dashboard',
  standalone: true,
  template: `
    <h2>Tableau de bord</h2>
    @if (error()) { <p class="error">{{ error() }}</p> }
    @if (stats(); as s) {
      <div class="kpis">
        <div class="kpi"><span>Tickets actifs</span><strong>{{ s.activeTickets }}</strong></div>
        <div class="kpi danger"><span>En retard</span><strong>{{ s.breachedTickets }}</strong></div>
        <div class="kpi"><span>Délais respectés (30 j)</span><strong>{{ s.slaCompliance === null ? '—' : s.slaCompliance + ' %' }}</strong></div>
        <div class="kpi"><span>Temps moyen de résolution</span><strong>{{ s.avgResolutionHours === null ? '—' : s.avgResolutionHours + ' h' }}</strong></div>
      </div>
    }
    <div class="charts">
      <div class="card"><h3>Créés / résolus sur 30 jours</h3><canvas #perDay></canvas></div>
      <div class="card"><h3>Tickets actifs par priorité</h3><canvas #byPriority></canvas></div>
      <div class="card"><h3>Répartition par statut</h3><canvas #byStatus></canvas></div>
      <div class="card"><h3>Charge par technicien</h3><canvas #workload></canvas></div>
    </div>
  `,
})
export class DashboardComponent implements AfterViewInit, OnDestroy {
  private readonly tickets = inject(TicketService);
  readonly stats = signal<Stats | null>(null);
  readonly error = signal('');

  private readonly perDay = viewChild.required<ElementRef<HTMLCanvasElement>>('perDay');
  private readonly byPriority = viewChild.required<ElementRef<HTMLCanvasElement>>('byPriority');
  private readonly byStatus = viewChild.required<ElementRef<HTMLCanvasElement>>('byStatus');
  private readonly workload = viewChild.required<ElementRef<HTMLCanvasElement>>('workload');
  private charts: Chart[] = [];

  ngAfterViewInit(): void {
    this.tickets.stats().subscribe({
      next: (s) => {
        this.stats.set(s);
        this.draw(s);
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  ngOnDestroy(): void {
    this.charts.forEach((c) => c.destroy());
  }

  private draw(s: Stats): void {
    this.charts.push(
      new Chart(this.perDay().nativeElement, {
        type: 'line',
        data: {
          labels: s.perDay.map((d) => d.date.slice(5)),
          datasets: [
            { label: 'Créés', data: s.perDay.map((d) => d.created), borderColor: '#3b82f6', tension: 0.3 },
            { label: 'Résolus', data: s.perDay.map((d) => d.resolved), borderColor: '#16a34a', tension: 0.3 },
          ],
        },
        options: { scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } },
      }),
      new Chart(this.byPriority().nativeElement, {
        type: 'doughnut',
        data: {
          labels: s.byPriority.map((p) => PRIORITY_LABEL[p.key]),
          datasets: [{ data: s.byPriority.map((p) => p.count), backgroundColor: s.byPriority.map((p) => PRIORITY_COLORS[p.key]) }],
        },
      }),
      new Chart(this.byStatus().nativeElement, {
        type: 'bar',
        data: {
          labels: s.byStatus.map((b) => STATUS_LABEL[b.key]),
          datasets: [{ label: 'Tickets', data: s.byStatus.map((b) => b.count), backgroundColor: '#6366f1' }],
        },
        options: { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } },
      }),
      new Chart(this.workload().nativeElement, {
        type: 'bar',
        data: {
          labels: s.workload.map((w) => w.key),
          datasets: [{ label: 'Tickets actifs', data: s.workload.map((w) => w.count), backgroundColor: '#0ea5e9' }],
        },
        options: { indexAxis: 'y', plugins: { legend: { display: false } }, scales: { x: { beginAtZero: true, ticks: { precision: 0 } } } },
      }),
    );
  }
}
