import { Component } from '@angular/core';
import {PowerArrow} from "../power-arrow/power-arrow";
import {ArrowPoint} from "../../model/arrow-point";
import {NgForOf, NgOptimizedImage} from "@angular/common";

@Component({
  selector: 'app-house-graphic',
  imports: [
    PowerArrow,
    NgForOf,
    NgOptimizedImage
  ],
  templateUrl: './house-graphic.html',
  styleUrl: './house-graphic.scss',
  standalone: true
})
export class HouseGraphic {
  points: ArrowPoint[] = [
    { x: 52, y: 25, value: 80, rotation: 0 },
    { x: 25, y: 42, value: -80, rotation: -90 },
    { x: 25, y: 77,  value: -80, rotation: -90 },
    { x: 74, y: 62,  value: -80, rotation: 90 },
  ];

}
