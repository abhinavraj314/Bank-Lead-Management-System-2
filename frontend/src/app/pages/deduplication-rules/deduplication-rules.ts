import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CanonicalFieldService } from '../../services/canonical-field.service';
import { DeduplicationRulesService } from '../../services/deduplication-rules.service';
import { CanonicalField } from '../../models/lead.models';

@Component({
  selector: 'app-deduplication-rules',
  imports: [CommonModule, FormsModule],
  templateUrl: './deduplication-rules.html',
  styleUrl: './deduplication-rules.css'
})
export class DeduplicationRulesPage implements OnInit {
  private readonly canonicalFieldService = inject(CanonicalFieldService);
  private readonly deduplicationRulesService = inject(DeduplicationRulesService);
  
  protected readonly fields = signal<CanonicalField[]>([]);
  protected readonly selectedFields = signal<string[]>([]);
  protected readonly isSaving = signal<boolean>(false);

  ngOnInit(): void {
    this.loadFields();
    this.loadSelectedFields();
  }

  loadFields(): void {
    this.canonicalFieldService.getCanonicalFields().subscribe(fields => {
      this.fields.set(fields);
    });
  }

  loadSelectedFields(): void {
    this.deduplicationRulesService.getSelectedFields().subscribe(selected => {
      this.selectedFields.set(selected);
    });
  }

  toggleField(fieldId: string): void {
    const current = this.selectedFields();
    if (current.includes(fieldId)) {
      this.selectedFields.set(current.filter(id => id !== fieldId));
    } else {
      this.selectedFields.set([...current, fieldId]);
    }
  }

  isFieldSelected(fieldId: string): boolean {
    return this.selectedFields().includes(fieldId);
  }

  onSaveRules(): void {
    this.isSaving.set(true);
    // Mock: Save to service (no backend call)
    this.deduplicationRulesService.saveDeduplicationRules(this.selectedFields()).subscribe(() => {
      this.isSaving.set(false);
      // Could show a success message here
      alert('Deduplication rules saved successfully!');
    });
  }
}
