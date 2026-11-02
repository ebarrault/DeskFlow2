import { describeValue, isStaff, PRIORITY_LABEL, STATUS_LABEL } from './labels';

describe('labels', () => {
  it('reconnaît les rôles du support', () => {
    expect(isStaff('TECHNICIAN')).toBeTrue();
    expect(isStaff('ADMIN')).toBeTrue();
    expect(isStaff('USER')).toBeFalse();
    expect(isStaff(null)).toBeFalse();
  });

  it('traduit les valeurs de l’historique', () => {
    expect(describeValue('IN_PROGRESS')).toBe(STATUS_LABEL.IN_PROGRESS);
    expect(describeValue('CRITICAL')).toBe(PRIORITY_LABEL.CRITICAL);
    expect(describeValue('Thomas Technicien')).toBe('Thomas Technicien');
    expect(describeValue(null)).toBe('');
  });
});
