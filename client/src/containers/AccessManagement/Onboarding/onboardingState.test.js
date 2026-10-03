import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  TOUR_VERSION,
  claimMascotSlot,
  isTourSeen,
  markTourSeen,
  turnMascotOff
} from './onboardingState';

describe('onboarding state', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('remembers the seen tour version and shows a newer one again', () => {
    expect(isTourSeen()).toBe(false);
    markTourSeen();
    expect(isTourSeen()).toBe(true);
    localStorage.setItem('akhq.onboarding.tour', String(TOUR_VERSION - 1));
    expect(isTourSeen()).toBe(false);
  });

  it('lets the mascot pop up once per place and session', () => {
    expect(claimMascotSlot('topic')).toBe(true);
    expect(claimMascotSlot('topic')).toBe(false);
    expect(claimMascotSlot('announce')).toBe(true);
  });

  it('never pops up after "do not show again"', () => {
    turnMascotOff();
    expect(claimMascotSlot('topic')).toBe(false);
  });

  it('stays silent when storage throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(isTourSeen()).toBe(false);
    expect(claimMascotSlot('topic')).toBe(false);
  });
});
