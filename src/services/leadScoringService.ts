import { ILead } from '../models/Lead';

export interface ScoringResult {
  score: number;
  reason: string;
  breakdown: {
    hasEmail: { points: number; applied: boolean };
    hasPhone: { points: number; applied: boolean };
    hasAadhar: { points: number; applied: boolean };
    hasName: { points: number; applied: boolean };
    multipleSources: { points: number; applied: boolean };
    multipleProducts: { points: number; applied: boolean };
  };
}

/**
 * Calculate lead score based on available data
 * Scoring criteria:
 * - Has email: +30
 * - Has phone: +30
 * - Has aadhar: +20
 * - Has name: +10
 * - Multiple sources: +10
 * - Multiple products: +10
 * Max score: 110 (but capped at 100)
 */
export const calculateLeadScore = (lead: ILead): ScoringResult => {
  let score = 0;
  const breakdown = {
    hasEmail: { points: 30, applied: false },
    hasPhone: { points: 30, applied: false },
    hasAadhar: { points: 20, applied: false },
    hasName: { points: 10, applied: false },
    multipleSources: { points: 10, applied: false },
    multipleProducts: { points: 10, applied: false }
  };

  // Has email
  if (lead.email && lead.email.trim().length > 0) {
    score += 30;
    breakdown.hasEmail.applied = true;
  }

  // Has phone
  if (lead.phone_number && lead.phone_number.trim().length > 0) {
    score += 30;
    breakdown.hasPhone.applied = true;
  }

  // Has aadhar
  if (lead.aadhar_number && lead.aadhar_number.trim().length > 0) {
    score += 20;
    breakdown.hasAadhar.applied = true;
  }

  // Has name
  if (lead.name && lead.name.trim().length > 0) {
    score += 10;
    breakdown.hasName.applied = true;
  }

  // Multiple sources
  if (lead.sources_seen && lead.sources_seen.length > 1) {
    score += 10;
    breakdown.multipleSources.applied = true;
  }

  // Multiple products
  if (lead.products_seen && lead.products_seen.length > 1) {
    score += 10;
    breakdown.multipleProducts.applied = true;
  }

  // Cap at 100
  score = Math.min(score, 100);

  // Generate reason
  const appliedFactors = Object.entries(breakdown)
    .filter(([_, value]) => value.applied)
    .map(([key, value]) => `${key} (+${value.points})`)
    .join(', ');

  const reason = appliedFactors 
    ? `Score based on: ${appliedFactors}`
    : 'No scoring factors applied';

  return {
    score,
    reason,
    breakdown
  };
};

/**
 * Score a lead and update it in the database
 */
export const scoreLead = async (lead: ILead): Promise<ScoringResult> => {
  const result = calculateLeadScore(lead);
  
  lead.lead_score = result.score;
  lead.score_reason = result.reason;
  
  await lead.save();

  return result;
};

/**
 * Score multiple leads in batch
 */
export const scoreLeadsBatch = async (leads: ILead[]): Promise<{
  scored: number;
  failed: number;
  results: Array<{ lead_id: string; score: number; success: boolean }>;
}> => {
  const results: Array<{ lead_id: string; score: number; success: boolean }> = [];
  let scored = 0;
  let failed = 0;

  for (const lead of leads) {
    try {
      const scoringResult = await scoreLead(lead);
      results.push({
        lead_id: lead.lead_id,
        score: scoringResult.score,
        success: true
      });
      scored++;
    } catch (error) {
      results.push({
        lead_id: lead.lead_id,
        score: 0,
        success: false
      });
      failed++;
    }
  }

  return {
    scored,
    failed,
    results
  };
};
