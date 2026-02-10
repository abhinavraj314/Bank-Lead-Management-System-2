import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-ranking-config',
  imports: [CommonModule],
  templateUrl: './ranking-config.html',
  styleUrl: './ranking-config.css',
})
export class RankingConfigPage {
  private readonly apiService = inject(ApiService);

  isAdmin(): boolean {
    return this.apiService.isAdmin();
  }
}
