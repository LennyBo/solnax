import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class CoolDownService {

  private readonly baseUrl = '/api/cool-down/manual';

  constructor(private http: HttpClient) {}

  /** Check if manual cooldown is active */
  isActive(): Observable<boolean> {
    return this.http.get<boolean>(this.baseUrl);
  }

  /** Create cooldown until tomorrow */
  create(): Observable<boolean> {
    return this.http.post<boolean>(this.baseUrl, {});
  }

  /** Clear all cooldowns */
  clear(): Observable<void> {
    return this.http.delete<void>(this.baseUrl);
  }
}
