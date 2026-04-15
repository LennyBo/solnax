export interface ChargeSession {
  id: string;
  vin: string;
  startedAt: string;
  endedAt: string | null;
  energyChargedKwh: number | null;
  ampsSet: number | null;
  status: 'ACTIVE' | 'COMPLETED' | 'ABORTED';
}

