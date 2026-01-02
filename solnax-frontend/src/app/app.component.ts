import {Component, OnInit} from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {PowerService} from "./service/power.service";
import {DataChart} from "./components/data-chart/data-chart";

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, DataChart],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit{
  title = 'solnax-frontend';

  constructor(private powerService:PowerService) {
  }

  ngOnInit(): void {
    console.log(this.powerService.getPower());
  }

  test(){
    this.powerService.getPower().subscribe({
      next:power =>  {
        console.log(power);
      }
    })
  }

}
