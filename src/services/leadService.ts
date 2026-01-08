import { v4 as uuidv4 } from 'uuid';
import { ILead, Lead } from '../models/Lead';
import { NormalizedLeadInput } from '../utils/leadNormalization';

export interface UpsertContext {
  p_id: string;
  source_id: string;
  rawRow: any;
}

export interface UpsertResult {
  action: 'inserted' | 'merged';
  lead: ILead;
}

/**
 * Find existing lead by matching any identifier (email, phone, aadhar)
 * Implements: "Duplicate if any identifier matches (email OR phone OR aadhar)"
 */
export const findExistingLead = async (
  normalized: NormalizedLeadInput
): Promise<ILead | null> => {
  const conditions: any[] = [];
  
  if (normalized.email) {
    conditions.push({ email: normalized.email });
  }
  
  if (normalized.phone_number) {
    conditions.push({ phone_number: normalized.phone_number });
  }
  
  if (normalized.aadhar_number) {
    conditions.push({ aadhar_number: normalized.aadhar_number });
  }
  
  if (conditions.length === 0) {
    return null;
  }
  
  // Find lead matching ANY of the identifiers
  return Lead.findOne({ $or: conditions });
};

/**
 * Merge strategy: Prefer existing non-empty values, fill gaps with incoming data
 * Deterministic precedence: Existing DB values take priority
 */
const mergeLeadData = (
  existing: ILead,
  incoming: NormalizedLeadInput,
  ctx: UpsertContext
): ILead => {
  // Fill missing fields with incoming data
  if (!existing.name && incoming.name) {
    existing.name = incoming.name;
  }
  
  if (!existing.phone_number && incoming.phone_number) {
    existing.phone_number = incoming.phone_number;
  }
  
  if (!existing.email && incoming.email) {
    existing.email = incoming.email;
  }
  
  if (!existing.aadhar_number && incoming.aadhar_number) {
    existing.aadhar_number = incoming.aadhar_number;
  }
  
  // Track source and product history
  if (!existing.sources_seen.includes(ctx.source_id)) {
    existing.sources_seen.push(ctx.source_id);
  }
  
  if (!existing.products_seen.includes(ctx.p_id)) {
    existing.products_seen.push(ctx.p_id);
  }
  
  // Store raw row for audit trail
  existing.merged_from.push({
    timestamp: new Date(),
    source_id: ctx.source_id,
    p_id: ctx.p_id,
    data: ctx.rawRow
  });
  
  return existing;
};

/**
 * Upsert lead: Insert new or merge with existing duplicate
 */
export const upsertLead = async (
  normalized: NormalizedLeadInput,
  ctx: UpsertContext
): Promise<UpsertResult> => {
  // Check for existing lead
  const existingLead = await findExistingLead(normalized);
  
  if (existingLead) {
    // Merge with existing lead
    const mergedLead = mergeLeadData(existingLead, normalized, ctx);
    await mergedLead.save();
    
    return {
      action: 'merged',
      lead: mergedLead
    };
  }
  
  // Create new lead
  const newLead = new Lead({
    lead_id: uuidv4(),
    name: normalized.name,
    phone_number: normalized.phone_number,
    email: normalized.email,
    aadhar_number: normalized.aadhar_number,
    source_id: ctx.source_id,
    p_id: ctx.p_id,
    created_at: new Date(),
    merged_from: [{
      timestamp: new Date(),
      source_id: ctx.source_id,
      p_id: ctx.p_id,
      data: ctx.rawRow
    }],
    sources_seen: [ctx.source_id],
    products_seen: [ctx.p_id],
    lead_score: null,
    score_reason: null
  });
  
  await newLead.save();
  
  return {
    action: 'inserted',
    lead: newLead
  };
};
