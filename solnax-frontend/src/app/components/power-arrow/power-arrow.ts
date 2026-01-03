import {Component, Input} from '@angular/core';
import {DecimalPipe} from "@angular/common";

@Component({
  selector: 'app-power-arrow',
  imports: [
    DecimalPipe
  ],
  templateUrl: './power-arrow.html',
  styleUrl: './power-arrow.scss',
  standalone: true
})
export class PowerArrow {

  @Input() value: number = 0; // Field value to determine color/direction
  @Input() manualRotation: number = 0; // Additional rotation from settings

  // Logic to determine color
  get arrowColor(): string {
    if(Math.abs(this.value) < 0.5){
      return 'gray';
    }
    return this.value > 0 ? 'green' : 'orange';
  }

  // Logic to determine direction (e.g., up if positive, down if negative)
  // We combine this with the manual rotation setting
  get transformStyle(): string {
    const baseRotation = this.value >= 0 ? 0 : 180;
    const totalRotation = baseRotation + this.manualRotation;
    return `rotate(${totalRotation}deg)`;
  }

  protected readonly Math = Math;
}
