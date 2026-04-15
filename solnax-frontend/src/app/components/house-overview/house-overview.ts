import { Component } from '@angular/core';
import {FormsModule} from "@angular/forms";
import {PowerArrow} from "../power-arrow/power-arrow";
import {HouseGraphic} from "../house-graphic/house-graphic";
import {CoolDownControl} from "../cool-down-control/cool-down-control";
import {ChargeSessionList} from "../charge-session-list/charge-session-list";

@Component({
  selector: 'app-house-overview',
  imports: [
    FormsModule,
    PowerArrow,
    HouseGraphic,
    CoolDownControl,
    ChargeSessionList
  ],
  templateUrl: './house-overview.html',
  styleUrl: './house-overview.scss',
  standalone: true
})
export class HouseOverview {
  public arrowVal = 60;
}
