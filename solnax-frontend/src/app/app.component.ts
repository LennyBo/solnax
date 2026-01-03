import {Component, OnInit} from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {PowerService} from "./service/power.service";
import {DataChart} from "./components/data-chart/data-chart";
import {PowerArrow} from "./components/power-arrow/power-arrow";
import {FormsModule} from "@angular/forms";
import {HouseGraphic} from "./components/house-graphic/house-graphic";

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, DataChart, PowerArrow, FormsModule, HouseGraphic],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {



  constructor() {
  }


}
