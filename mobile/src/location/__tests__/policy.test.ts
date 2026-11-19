import { distanceMeters, PROFILES, powerMode, shouldSend, toPercent, type Fix } from '../policy';

describe('powerMode', () => {
  it('is normal with a healthy battery', () => {
    expect(powerMode({ level: 80, charging: false, lowPowerMode: false })).toBe('normal');
  });

  it('steps down to saver at 20% or in Low Power Mode', () => {
    expect(powerMode({ level: 20, charging: false, lowPowerMode: false })).toBe('saver');
    expect(powerMode({ level: 90, charging: false, lowPowerMode: true })).toBe('saver');
  });

  it('goes critical at 10%', () => {
    expect(powerMode({ level: 10, charging: false, lowPowerMode: true })).toBe('critical');
  });

  it('is always normal while charging', () => {
    expect(powerMode({ level: 5, charging: true, lowPowerMode: true })).toBe('normal');
  });

  it('treats an unknown level as normal unless Low Power Mode is on', () => {
    expect(powerMode({ level: null, charging: false, lowPowerMode: false })).toBe('normal');
    expect(powerMode({ level: null, charging: false, lowPowerMode: true })).toBe('saver');
  });

  it('uses coarser, rarer fixes as the battery drops', () => {
    expect(PROFILES.saver.distanceM).toBeGreaterThan(PROFILES.normal.distanceM);
    expect(PROFILES.critical.heartbeatMs).toBeGreaterThan(PROFILES.saver.heartbeatMs);
  });
});

describe('toPercent', () => {
  it('converts 0..1 to a whole percentage', () => {
    expect(toPercent(0.634)).toBe(63);
    expect(toPercent(1)).toBe(100);
  });

  it('returns null when the OS does not know', () => {
    expect(toPercent(-1)).toBeNull();
    expect(toPercent(Number.NaN)).toBeNull();
  });
});

describe('distanceMeters', () => {
  it('is about 111 km per degree of latitude', () => {
    expect(distanceMeters({ lat: 0, lng: 0 }, { lat: 1, lng: 0 })).toBeCloseTo(111_195, -2);
  });

  it('is zero for the same point', () => {
    expect(distanceMeters({ lat: 40.4, lng: -3.7 }, { lat: 40.4, lng: -3.7 })).toBe(0);
  });
});

describe('shouldSend', () => {
  const profile = PROFILES.normal; // 25 m, heartbeat 5 min
  const at = (lat: number, secondsLater: number, accuracy: number | null = 10): Fix => ({
    lat, lng: 0, accuracy, at: 1_000_000 + secondsLater * 1000,
  });
  const METER = 1 / 111_195; // degrees of latitude per meter

  it('always sends the first fix', () => {
    expect(shouldSend(null, at(0, 0, 5000), profile)).toBe(true);
  });

  it('skips jitter while standing still', () => {
    expect(shouldSend(at(0, 0), at(5 * METER, 30), profile)).toBe(false);
  });

  it('sends once you have actually moved', () => {
    expect(shouldSend(at(0, 0), at(40 * METER, 30), profile)).toBe(true);
  });

  it('sends a heartbeat when standing still for long enough', () => {
    expect(shouldSend(at(0, 0), at(0, 5 * 60), profile)).toBe(true);
  });

  it('ignores duplicate or out-of-order fixes', () => {
    expect(shouldSend(at(0, 10), at(1, 10), profile)).toBe(false);
    expect(shouldSend(at(0, 10), at(1, 5), profile)).toBe(false);
  });

  it('ignores very rough fixes once we have a position', () => {
    expect(shouldSend(at(0, 0), at(1, 600, 5000), profile)).toBe(false);
  });
});
