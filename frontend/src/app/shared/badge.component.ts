import { Component, computed, input } from '@angular/core';
import { PRIORITY_LABEL, SLA_LABEL, STATUS_LABEL } from '../core/labels';

/** Pastille colorée pour un statut, une priorité ou un état de délai. */
@Component({
  selector: 'app-badge',
  standalone: true,
  template: `<span class="badge {{ kind() }}-{{ value() }}">{{ label() }}</span>`,
})
export class BadgeComponent {
  readonly kind = input.required<'status' | 'prio' | 'sla'>();
  readonly value = input.required<string>();

  readonly label = computed(() => {
    const maps: Record<string, Record<string, string>> = { status: STATUS_LABEL, prio: PRIORITY_LABEL, sla: SLA_LABEL };
    return maps[this.kind()][this.value()] ?? this.value();
  });
}
