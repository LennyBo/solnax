import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChargeSession } from '../model/charge-session';

@Injectable({
  providedIn: 'root'
})
export class ChargeSessionService {

  private readonly baseUrl = '/api/charge-sessions';

  constructor(private http: HttpClient) {}

  getAll(): Observable<ChargeSession[]> {
    return this.http.get<ChargeSession[]>(this.baseUrl);
  }

  getRecent(): Observable<ChargeSession[]> {
    return this.http.get<ChargeSession[]>(`${this.baseUrl}/recent`);
  }

  getActive(): Observable<ChargeSession[]> {
    return this.http.get<ChargeSession[]>(`${this.baseUrl}/active`);
  }
}

