import { Injectable } from '@angular/core';
import {Observable} from "rxjs";
import {HttpClient} from "@angular/common/http";

@Injectable({
  providedIn: 'root'
})
export class PowerService {

  private readonly baseUrl = '/api/power';

  constructor(private http: HttpClient) { }


  getPower(): Observable<any> {
    return this.http.get(this.baseUrl);
  }
}
