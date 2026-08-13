export type ChargeControlModeName = 'NORMAL' | 'ECO_PLUS' | 'MANUAL';

export interface ChargeControlMode {
  mode: ChargeControlModeName;
  endsAt: string | null;
  minutesRemaining: number;
}
