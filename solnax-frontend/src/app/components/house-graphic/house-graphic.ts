import {Component, DestroyRef, OnInit, signal} from '@angular/core';
import {PowerArrow} from "../power-arrow/power-arrow";
import {ArrowPoint} from "../../model/arrow-point";
import {NgForOf, NgOptimizedImage} from "@angular/common";
import {PowerService} from "../../service/power.service";
import {switchMap, timer} from "rxjs";
import {takeUntilDestroyed} from "@angular/core/rxjs-interop";
import {InstantPower} from "../../model/instant-power";
import {DataChart} from "../data-chart/data-chart";

@Component({
  selector: 'app-house-graphic',
  imports: [
    PowerArrow,
    NgForOf,
    NgOptimizedImage,
    DataChart
  ],
  templateUrl: './house-graphic.html',
  styleUrl: './house-graphic.scss',
  standalone: true
})
export class HouseGraphic implements OnInit{
  points = signal<ArrowPoint[]> ([
    { x: 52, y: 25, value: 80, rotation: 0 },
    { x: 25, y: 42, value: -80, rotation: -90 },
    { x: 25, y: 77,  value: -80, rotation: -90 },
    { x: 74, y: 62,  value: -80, rotation: -90 },
]);

  constructor(private powerService:PowerService,private destroyRef: DestroyRef) {
  }

  ngOnInit(): void {
    timer(0, 1000).pipe(
      switchMap(() => this.powerService.getInstantPower()),
      takeUntilDestroyed(this.destroyRef) // v21 cleanup pattern
    ).subscribe(data => {
      this.updateChart(data);
    });
  }

  private updateChart(data: InstantPower) {
    this.points.update(oldPoints => {
      // Create new objects so the signal notifies listeners
      const newPoints = [...oldPoints];
      newPoints[0] = { ...newPoints[0], value: data.solar };
      newPoints[1] = { ...newPoints[1], value: data.heat };
      newPoints[2] = { ...newPoints[2], value: data.evCharger };
      newPoints[3] = { ...newPoints[3], value: data.house };
      return newPoints;
    });
  }
}
