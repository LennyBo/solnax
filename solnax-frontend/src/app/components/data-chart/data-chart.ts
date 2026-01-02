import {Component, signal} from '@angular/core';
import {BaseChartDirective} from "ng2-charts";
import {ChartConfiguration, ChartData, ChartEvent} from "chart.js";

@Component({
  selector: 'app-data-chart',
  standalone: true,
  imports: [BaseChartDirective],
  templateUrl: './data-chart.html',
  styleUrl: './data-chart.scss',
})
export class DataChart {
// Using Signals for reactive data updates
  clickedLabel = signal<string>('None');

  chartData = signal<ChartData<'line'>>({
    labels: ['Q1', 'Q2', 'Q3', 'Q4'],
    datasets: [
      {
        data: [65, 59, 80, 81],
        label: 'Sales 2026',
        backgroundColor: '#42A5F5'
      }
    ]
  });

  chartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    plugins: {
      legend: { display: true },
      tooltip: { enabled: true } // Interactive hover tooltips
    },
    scales: {
      y: { beginAtZero: true }
    }
  };

  // Interaction Handler
  onChartClick({ event, active }: { event?: ChartEvent, active?: object[] }): void {
    if (active && active.length > 0) {
      const index = (active[0] as any).index;
      const label = this.chartData().labels?.[index] as string;
      this.clickedLabel.set(label);
    }
  }
}
