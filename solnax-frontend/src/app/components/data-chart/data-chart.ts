import { Component, DestroyRef, OnInit, signal } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';
import { PowerService } from '../../service/power.service';
import { switchMap, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PowerLogs } from '../../model/power-logs';

@Component({
  selector: 'app-data-chart',
  standalone: true,
  imports: [BaseChartDirective],
  templateUrl: './data-chart.html',
  styleUrl: './data-chart.scss',
})
export class DataChart implements OnInit {

  constructor(
    private powerService: PowerService,
    private destroyRef: DestroyRef
  ) {}

  chartData = signal<ChartData<'line'>>({
    labels: [],
    datasets: [
      {
        data: [],
        label: 'Solar Production',
        borderColor: '#4caf50',
        tension: 0.3,
        fill: 'origin',
        backgroundColor: 'rgba(76, 175, 80, 0.1)',
        stack: 'solar'
      },
      {
        data: [],
        label: 'House Consumption',
        borderColor: '#f44336',
        tension: 0.3,
        stack: 'load'
      },
      {
        data: [],
        label: 'Charger',
        borderColor: '#38d9b9',
        tension: 0.3,
        stack: 'load'
      }
    ]
  });

  chartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    elements: { point: { radius: 0 } },
    scales: {
      x: { stacked: true },
      y: {
        stacked: true,
        title: { display: true, text: 'Watts (W)' }
      }
    },
    plugins: {
      tooltip: { mode: 'index', intersect: false }
    }
  };

  ngOnInit() {
    timer(0, 30000).pipe(
      switchMap(() => this.powerService.getPower()),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(data => {
      this.updateChart(data);
    });
  }

  private updateChart(newData: PowerLogs) {
    this.chartData.set({
      labels: newData.times,
      datasets: [
        { ...this.chartData().datasets[0], data: newData.solarIn },
        { ...this.chartData().datasets[1], data: newData.house },
        { ...this.chartData().datasets[2], data: newData.charger }
      ]
    });
  }
}
