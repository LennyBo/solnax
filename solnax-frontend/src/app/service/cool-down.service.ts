import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CoolDownStatus } from '../model/cool-down-status';
import { ChargeControlMode, ChargeControlModeName } from '../model/charge-control-mode';

@Injectable({
  providedIn: 'root'
})
export class CoolDownService {

  private readonly baseUrl = '/api/cool-down';

  constructor(private http: HttpClient) {}

  /** Currently active charge control mode (NORMAL / ECO_PLUS / MANUAL) */
  getMode(): Observable<ChargeControlMode> {
    return this.http.get<ChargeControlMode>(`${this.baseUrl}/mode`);
  }

  /** Activate a mode until tomorrow morning. Modes are mutually exclusive. */
  activateMode(mode: ChargeControlModeName): Observable<ChargeControlMode> {
    return this.http.post<ChargeControlMode>(`${this.baseUrl}/mode/${mode}`, {});
  }

  /** Back to NORMAL — only removes the mode, vehicle cooldowns stay untouched. */
  clearMode(): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/mode`);
  }

  /** Clear every active cooldown, including the current mode */
  clearAll(): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/manual`);
  }

  /** Get all active cooldowns with details */
  getStatus(): Observable<CoolDownStatus[]> {
    return this.http.get<CoolDownStatus[]>(`${this.baseUrl}/status`);
  }
}
