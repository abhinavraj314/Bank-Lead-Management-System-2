import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CanonicalFieldService } from '../../services/canonical-field.service';
import { CanonicalField } from '../../models/lead.models';

@Component({
  selector: 'app-canonical-fields',
  imports: [CommonModule, FormsModule],
  templateUrl: './canonical-fields.html',
  styleUrl: './canonical-fields.css'
})
export class CanonicalFieldsPage implements OnInit {
  private readonly canonicalFieldService = inject(CanonicalFieldService);
  
  protected readonly fields = signal<CanonicalField[]>([]);
  protected readonly showCreateModal = signal<boolean>(false);
  
  // Form fields (using regular properties for ngModel binding)
  protected newField: Partial<CanonicalField> = {
    field_name: '',
    display_name: '',
    type: 'String',
    version: 1,
    status: 'Active'
  };

  ngOnInit(): void {
    this.loadFields();
  }

  loadFields(): void {
    this.canonicalFieldService.getCanonicalFields().subscribe(fields => {
      this.fields.set(fields);
    });
  }

  openCreateModal(): void {
    this.showCreateModal.set(true);
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
    this.newField = {
      field_name: '',
      display_name: '',
      type: 'String',
      version: 1,
      status: 'Active'
    };
  }

  onCreateField(): void {
    // Mock: Add to local state (no backend call)
    const field: CanonicalField = {
      field_id: `field_${Date.now()}`,
      field_name: this.newField.field_name || '',
      display_name: this.newField.display_name || '',
      type: this.newField.type || 'String',
      version: this.newField.version || 1,
      status: this.newField.status || 'Active'
    };
    
    this.fields.set([...this.fields(), field]);
    this.closeCreateModal();
  }
}
