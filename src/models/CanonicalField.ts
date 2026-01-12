import { Schema, model, Document } from 'mongoose';

export interface ICanonicalField extends Document {
  field_name: string;
  display_name: string;
  field_type: 'String' | 'Number' | 'Date' | 'Boolean' | 'Email' | 'Phone';
  is_active: boolean;
  is_required: boolean;
  version: string;
  createdAt: Date;
  updatedAt: Date;
}

const CanonicalFieldSchema = new Schema<ICanonicalField>(
  {
    field_name: {
      type: String,
      required: [true, 'Field name is required'],
      unique: true,
      trim: true,
      lowercase: true,
      match: [/^[a-z][a-z0-9_]*$/, 'Field name must start with a letter and contain only lowercase letters, numbers, and underscores']
    },
    display_name: {
      type: String,
      required: [true, 'Display name is required'],
      trim: true
    },
    field_type: {
      type: String,
      enum: ['String', 'Number', 'Date', 'Boolean', 'Email', 'Phone'],
      required: [true, 'Field type is required']
    },
    is_active: {
      type: Boolean,
      default: true
    },
    is_required: {
      type: Boolean,
      default: false
    },
    version: {
      type: String,
      default: 'v1'
    }
  },
  {
    timestamps: true,
    collection: 'canonical_fields'
  }
);

// Index for faster queries
CanonicalFieldSchema.index({ field_name: 1 });
CanonicalFieldSchema.index({ is_active: 1 });

export const CanonicalField = model<ICanonicalField>('CanonicalField', CanonicalFieldSchema);
