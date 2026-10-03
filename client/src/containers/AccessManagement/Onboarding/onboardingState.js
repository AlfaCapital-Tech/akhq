// Bump when the tour content changes: users who saw the previous version get the announcement again.
export const TOUR_VERSION = 1;

const TOUR_KEY = 'akhq.onboarding.tour';
const MASCOT_KEY = 'akhq.onboarding.mascot';
const SHOWN_PREFIX = 'akhq.onboarding.shown.';

// Storage access can throw (private mode, blocked site data): then the mascot simply never pops up by itself.
const read = (storage, key) => {
  try {
    return window[storage].getItem(key);
  } catch {
    return null;
  }
};

const write = (storage, key, value) => {
  try {
    window[storage].setItem(key, value);
    return true;
  } catch {
    return false;
  }
};

export const isAccessManagementEnabled = () => {
  try {
    return JSON.parse(read('sessionStorage', 'auths') || '{}').accessManagementEnabled === true;
  } catch {
    return false;
  }
};

export const isTourSeen = () => Number(read('localStorage', TOUR_KEY)) >= TOUR_VERSION;

export const markTourSeen = () => write('localStorage', TOUR_KEY, String(TOUR_VERSION));

export const isMascotOff = () => read('localStorage', MASCOT_KEY) === 'off';

export const turnMascotOff = () => write('localStorage', MASCOT_KEY, 'off');

// Lets the mascot pop up at most once per place and browser session.
export const claimMascotSlot = place =>
  !isMascotOff() &&
  read('sessionStorage', SHOWN_PREFIX + place) === null &&
  write('sessionStorage', SHOWN_PREFIX + place, '1');
