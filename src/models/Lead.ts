import { Schema, model, Document } from 'mongoose';

export interface ILead extends Document {
  lead_id: string;
  name?: string;
  phone_number?: string;
  email?: string;
  aadhar_number?: string;
  source_id?: string;
  p_id?: string;
  created_at: Date;
  
  // Merge metadata
  merged_from: any[];
  sources_seen: string[];
  products_seen: string[];
  
  // AI scoring stub fields
  lead_score: number | null;
  score_reason: string | null;
  
  createdAt: Date;
  updatedAt: Date;
}

const LeadSchema = new Schema<ILead>(
  {
    lead_id: {
      type: String,
      required: true,
      unique: true,
      trim: true
    },
    name: {
      type: String,
      trim: true
    },
    phone_number: {
      type: String,
      trim: true,
      index: true
    },
    email: {
      type: String,
      trim: true,
      lowercase: true,
      index: true
    },
    aadhar_number: {
      type: String,
      trim: true,
      index: true
    },
    source_id: {
      type: String,
      trim: true
    },
    p_id: {
      type: String,
      trim: true
    },
    created_at: {
      type: Date,
      required: true
    },
    
    // Metadata for tracking merges
    merged_from: {
      type: [Array],
      default: []
    },
    sources_seen: {
      type: [String],
      default: []
    },
    products_seen: {
      type: [String],
      default: []
    },
    
    // AI scoring placeholder
    lead_score: {
      type: Number,
      default: null
    },
    score_reason: {
      type: String,
      default: null
    }
  },
  {
    timestamps: true,
    collection: 'leads'
  }
);

// Compound index for deduplication queries
LeadSchema.index(
  { email: 1, phone_number: 1, aadhar_number: 1 },
  { name: 'lead_identifiers_index' }
);

// Index for dashboard queries
LeadSchema.index({ p_id: 1, source_id: 1, created_at: -1 });

export const Lead = model<ILead>('Lead', LeadSchema);




