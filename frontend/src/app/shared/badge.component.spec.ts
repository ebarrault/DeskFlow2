import { TestBed } from '@angular/core/testing';
import { BadgeComponent } from './badge.component';

describe('BadgeComponent', () => {
  it('affiche le libellé et la classe de couleur', () => {
    const fixture = TestBed.createComponent(BadgeComponent);
    fixture.componentRef.setInput('kind', 'sla');
    fixture.componentRef.setInput('value', 'BREACHED');
    fixture.detectChanges();
    const span: HTMLElement = fixture.nativeElement.querySelector('span');
    expect(span.textContent).toBe('En retard');
    expect(span.classList).toContain('sla-BREACHED');
  });
});
