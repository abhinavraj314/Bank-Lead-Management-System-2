import { Component, inject, signal, OnInit, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { LeadService } from '../../services/lead.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-ranking-config',
  imports: [CommonModule],
  templateUrl: './ranking-config.html',
  styleUrl: './ranking-config.css',
})
export class RankingConfigPage implements OnInit {
  private readonly apiService = inject(ApiService);
  private readonly leadService = inject(LeadService);
  private readonly toast = inject(ToastService);
  private readonly platformId = inject(PLATFORM_ID);

  mlServiceAvailable = signal(false);
  scoringMethod = signal('Checking...');
  isScoring = signal(false);
  showScoringModal = signal(false);
  scoringResult = signal<{ totalLeads: number; scoredCount: number } | null>(null);
  scoringError = signal<string | null>(null);
  isCheckingStatus = signal(true);

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.checkMlStatus();
    }
  }

  isAdmin(): boolean {
    return this.apiService.isAdmin();
  }

  checkMlStatus() {
    this.isCheckingStatus.set(true);
    this.leadService.getMlStatus().subscribe({
      next: (status) => {
        this.mlServiceAvailable.set(status.mlServiceAvailable);
        this.scoringMethod.set(status.scoringMethod);
        this.isCheckingStatus.set(false);
      },
      error: () => {
        this.mlServiceAvailable.set(false);
        this.scoringMethod.set('Unavailable');
        this.isCheckingStatus.set(false);
        this.toast.error('ML service status check failed. Scoring will use heuristic fallback.');
      },
    });
  }

  scoreAllLeads() {
    this.isScoring.set(true);
    this.showScoringModal.set(true);
    this.scoringResult.set(null);
    this.scoringError.set(null);

    this.leadService.scoreAllLeads().subscribe({
      next: (result) => {
        this.isScoring.set(false);
        this.showScoringModal.set(false);
        this.scoringResult.set(result);
      },
      error: (err) => {
        this.isScoring.set(false);
        this.showScoringModal.set(false);
        const msg = err?.message || 'Scoring failed';
        this.scoringError.set(msg);
        this.toast.error(msg);
      },
    });
  }

  closeScoringModal() {
    this.showScoringModal.set(false);
  }
}
