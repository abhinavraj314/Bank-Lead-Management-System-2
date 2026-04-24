import { Component, OnInit, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BaseChartDirective } from 'ng2-charts';
import { Chart, ChartConfiguration, ChartType, registerables } from 'chart.js';
import { SourceService } from '../../services/source.service';
import { ProductService } from '../../services/product.service';
import {
  LeadSourceReportResponse,
  ReportsService,
  TrendBucket,
} from '../../services/reports.service';
import { Product, Source } from '../../models/lead.models';

Chart.register(...registerables);

@Component({
  selector: 'app-reports',
  imports: [CommonModule, FormsModule, BaseChartDirective],
  templateUrl: './reports.html',
  styleUrl: './reports.css',
})
export class ReportsPage implements OnInit {
  private readonly sourceService = inject(SourceService);
  private readonly productService = inject(ProductService);
  private readonly reportsService = inject(ReportsService);
  private readonly platformId = inject(PLATFORM_ID);

  protected readonly sources = signal<Source[]>([]);
  protected readonly products = signal<Product[]>([]);
  protected readonly selectedProductId = signal<string>('ALL');
  protected readonly selectedSourceId = signal<string>('ALL');
  protected readonly chartType = signal<ChartType>('bar');
  protected readonly trendBucket = signal<TrendBucket>('day');
  protected readonly isLoading = signal<boolean>(false);
  protected readonly errorMessage = signal<string>('');
  protected readonly reportData = signal<LeadSourceReportResponse | null>(null);
  protected readonly isBrowser = signal<boolean>(false);
  private readonly statePalette = ['#4f46e5', '#22c55e', '#f59e0b', '#ef4444', '#0ea5e9', '#8b5cf6'];

  protected readonly sourceChartData = computed<ChartConfiguration['data']>(() => {
    const report = this.reportData();
    const breakdown = report?.sourceBreakdown || [];
    const labels = breakdown.map((item) => item.sourceName || item.sourceId);
    const converted = breakdown.map((item) => item.convertedLeads || 0);
    const notConverted = breakdown.map((item) => {
      const fallback = Math.max(0, (item.totalLeads || 0) - (item.convertedLeads || 0));
      return item.notConvertedLeads ?? fallback;
    });
    return {
      labels,
      datasets: [
        {
          data: converted,
          label: 'Converted Leads',
          backgroundColor: '#16a34a',
          borderColor: '#15803d',
          borderWidth: 1,
          stack: 'leads',
        },
        {
          data: notConverted,
          label: 'Not Converted Leads',
          backgroundColor: '#93c5fd',
          borderColor: '#3b82f6',
          borderWidth: 1,
          stack: 'leads',
        },
      ],
    };
  });

  protected readonly filteredSources = computed<Source[]>(() => {
    const productId = this.selectedProductId();
    const allSources = this.sources();
    if (productId === 'ALL') {
      return allSources;
    }
    const normalizedProductId = productId.toUpperCase();
    return allSources.filter((source) => source.product_id?.toUpperCase() === normalizedProductId);
  });

  protected readonly stateChartData = computed<ChartConfiguration['data']>(() => {
    const report = this.reportData();
    const labels = (report?.stateDistribution || []).map((item) => item.state);
    const counts = (report?.stateDistribution || []).map((item) => item.count);
    const colors = labels.map((_, index) => this.statePalette[index % this.statePalette.length]);
    return {
      labels,
      datasets: [
        {
          data: counts,
          label: 'Leads by State',
          backgroundColor: colors,
          borderColor: '#312e81',
          pointBackgroundColor: colors,
          pointBorderColor: colors,
          borderWidth: 1,
        },
      ],
    };
  });

  protected readonly trendChartData = computed<ChartConfiguration<'line' | 'bar'>['data']>(() => {
    const report = this.reportData();
    const labels = (report?.trend || []).map((item) => item.period);
    const totalLeads = (report?.trend || []).map((item) => item.totalLeads);
    const convertedLeads = (report?.trend || []).map((item) => item.convertedLeads);
    return {
      labels,
      datasets: [
        {
          data: totalLeads,
          label: 'Total Leads',
          borderColor: '#2563eb',
          backgroundColor: 'rgba(37, 99, 235, 0.35)',
          fill: false,
          tension: 0.25,
        },
        {
          data: convertedLeads,
          label: 'Converted Leads',
          borderColor: '#16a34a',
          backgroundColor: 'rgba(22, 163, 74, 0.35)',
          fill: false,
          tension: 0.25,
        },
      ],
    };
  });

  protected readonly summary = computed(() => {
    return (
      this.reportData()?.summary ?? {
        totalLeads: 0,
        convertedLeads: 0,
        notConvertedLeads: 0,
        conversionRate: 0,
      }
    );
  });

  protected readonly sourceChartOptions = computed<ChartConfiguration['options']>(() => ({
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
      },
    },
    scales: {
      x: { stacked: true, ticks: { color: '#334155' } },
      y: { stacked: true, beginAtZero: true, ticks: { precision: 0, color: '#334155' } },
    },
  }));

  protected readonly stateChartOptions = computed<ChartConfiguration['options']>(() => ({
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels:
          this.chartType() === 'pie'
            ? undefined
            : {
                generateLabels: (chart) => this.generateCategoryLegendLabels(chart),
              },
      },
    },
    scales:
      this.chartType() === 'pie'
        ? undefined
        : {
            x: { ticks: { color: '#334155' } },
            y: { beginAtZero: true, ticks: { precision: 0, color: '#334155' } },
          },
  }));

  protected readonly trendOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
      },
    },
    scales: {
      x: { ticks: { color: '#334155' } },
      y: { beginAtZero: true, ticks: { precision: 0, color: '#334155' } },
    },
  };

  ngOnInit(): void {
    this.isBrowser.set(isPlatformBrowser(this.platformId));
    this.loadProducts();
    this.loadSources();
    this.loadAnalytics();
  }

  protected onProductChange(productId: string): void {
    this.selectedProductId.set(productId);
    this.selectedSourceId.set('ALL');
    this.loadAnalytics();
  }

  protected onSourceChange(sourceId: string): void {
    this.selectedSourceId.set(sourceId);
    this.loadAnalytics();
  }

  protected onChartTypeChange(type: ChartType): void {
    this.chartType.set(type);
  }

  protected onTrendBucketChange(bucket: TrendBucket): void {
    this.trendBucket.set(bucket);
    this.loadAnalytics();
  }

  protected refresh(): void {
    this.loadAnalytics();
  }

  protected trendChartType(): 'line' | 'bar' {
    return this.chartType() === 'pie' ? 'line' : (this.chartType() as 'line' | 'bar');
  }

  private loadSources(): void {
    this.sourceService.getSourcesPage({ page: 1, limit: 1000 }).subscribe({
      next: ({ items }) => {
        this.sources.set(items);
      },
      error: () => this.sources.set([]),
    });
  }

  private loadProducts(): void {
    this.productService.getProductsPage({ page: 1, limit: 1000 }).subscribe({
      next: ({ items }) => this.products.set(items),
      error: () => this.products.set([]),
    });
  }

  private loadAnalytics(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');
    this.reportsService
      .getLeadSourceAnalytics({
        productId: this.selectedProductId(),
        sourceId: this.selectedSourceId(),
        bucket: this.trendBucket(),
      })
      .subscribe({
        next: (data) => {
          this.reportData.set(data);
          this.isLoading.set(false);
        },
        error: (error) => {
          this.errorMessage.set(error?.message || 'Failed to load reports');
          this.isLoading.set(false);
          this.reportData.set(null);
        },
      });
  }

  private generateCategoryLegendLabels(chart: any): any[] {
    const labels = chart?.data?.labels ?? [];
    const dataset = chart?.data?.datasets?.[0];
    const background = dataset?.backgroundColor;

    return labels.map((label: string, index: number) => {
      const fillStyle = Array.isArray(background) ? background[index] : background;
      return {
        text: label,
        fillStyle,
        strokeStyle: fillStyle,
        hidden: false,
        lineWidth: 1,
        datasetIndex: 0,
      };
    });
  }
}
