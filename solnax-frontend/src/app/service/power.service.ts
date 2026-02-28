import { Injectable } from '@angular/core';
import {Observable} from "rxjs";
import {HttpClient} from "@angular/common/http";
import {InstantPower} from "../model/instant-power";
import {PowerLogs} from "../model/power-logs";

@Injectable({
  providedIn: 'root'
})
export class PowerService {

  private readonly baseUrl = '/api/power';

  constructor(private http: HttpClient) { }


  getPower(onDate: string) {
    return this.http.get<PowerLogs>(`/api/power?onDate=${onDate}`);
  }

  getInstantPower(): Observable<InstantPower>{
    return this.http.get<InstantPower>(this.baseUrl + "/current");
  }
}
