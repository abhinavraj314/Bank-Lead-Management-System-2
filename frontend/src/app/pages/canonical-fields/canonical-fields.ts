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
  protected readonly isCreating = signal<boolean>(false);
  protected readonly errorMessage = signal<string>('');
  
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
    this.errorMessage.set('');
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
    this.errorMessage.set('');
    this.isCreating.set(false);
    this.newField = {
      field_name: '',
      display_name: '',
      type: 'String',
      version: 1,
      status: 'Active'
    };
  }

  onCreateField(): void {
    // Validation
    if (!this.newField.field_name?.trim()) {
      this.errorMessage.set('Field Name is required');
      return;
    }
    if (!this.newField.display_name?.trim()) {
      this.errorMessage.set('Display Name is required');
      return;
    }
    if (!this.newField.type) {
      this.errorMessage.set('Type is required');
      return;
    }

    // Check if field name already exists (client-side check)
    const existingField = this.fields().find(
      f => f.field_name.toLowerCase() === this.newField.field_name!.toLowerCase()
    );
    if (existingField) {
      this.errorMessage.set(`Field '${this.newField.field_name}' already exists`);
      return;
    }

    // Only allow String, Number, Date, Boolean (remove Email and Phone)
    const allowedTypes: string[] = ['String', 'Number', 'Date', 'Boolean'];
    if (!allowedTypes.includes(this.newField.type)) {
      this.errorMessage.set('Type must be String, Number, Date, or Boolean');
      return;
    }

    this.isCreating.set(true);
    this.errorMessage.set('');

    this.canonicalFieldService.createCanonicalField({
      field_name: this.newField.field_name.trim(),
      display_name: this.newField.display_name.trim(),
      field_type: this.newField.type as 'String' | 'Number' | 'Date' | 'Boolean',
      is_active: this.newField.status === 'Active',
      version: 'v1'
    }).subscribe({
      next: () => {
        this.loadFields(); // Reload from backend
        this.closeCreateModal();
      },
      error: (error) => {
        this.isCreating.set(false);
        this.errorMessage.set(error.message || 'Failed to create canonical field');
      }
    });
  }
}
