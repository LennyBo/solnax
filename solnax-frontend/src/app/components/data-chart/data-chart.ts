import { Component, DestroyRef, OnInit, signal, computed } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';
import { PowerService } from '../../service/power.service';
import { switchMap } from 'rxjs';
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

  /** STATE */
  selectedDate = signal<Date>(new Date());
  loading = signal(false);

  /** CHART DATA */
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

  /** COMPUTED */
  selectedDateLabel = computed(() =>
    this.selectedDate().toLocaleDateString(undefined, {
      weekday: 'short',
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    })
  );

  isToday = computed(() => {
    const today = new Date();
    const d = this.selectedDate();
    return d.toDateString() === today.toDateString();
  });

  /** LIFECYCLE */
  ngOnInit() {
    this.loadData();
  }

  /** NAVIGATION */
  previousDay() {
    const d = new Date(this.selectedDate());
    d.setDate(d.getDate() - 1);
    this.selectedDate.set(d);
    this.loadData();
  }

  nextDay() {
    if (this.isToday()) return;
    const d = new Date(this.selectedDate());
    d.setDate(d.getDate() + 1);
    this.selectedDate.set(d);
    this.loadData();
  }

  /** DATA LOADING */
  private loadData() {
    this.loading.set(true);

    const dateParam = this.selectedDate().toISOString().split('T')[0];

    this.powerService.getPower(dateParam).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: data => {
        this.updateChart(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
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
