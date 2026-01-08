import { Schema, model, Document } from 'mongoose';

export interface ISource extends Document {
  source_id: string;
  source_name: string;
  p_id: string; // References Product.p_id
  createdAt: Date;
  updatedAt: Date;
}

const SourceSchema = new Schema<ISource>(
  {
    source_id: {
      type: String,
      required: [true, 'Source ID is required'],
      unique: true,
      trim: true,
      uppercase: true
    },
    source_name: {
      type: String,
      required: [true, 'Source name is required'],
      trim: true
    },
    p_id: {
      type: String,
      required: [true, 'Product ID is required'],
      trim: true,
      uppercase: true
    }
  },
  {
    timestamps: true,
    collection: 'sources'
  }
);

// Indexes for queries
SourceSchema.index({ source_id: 1 });
SourceSchema.index({ p_id: 1 });

export const Source = model<ISource>('Source', SourceSchema);




