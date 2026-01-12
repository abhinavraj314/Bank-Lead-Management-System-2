import { Lead, ILead } from '../models/Lead';

export interface MergeRecord {
  lead_id: string;
  merged_at: Date;
  source_id?: string;
  p_id?: string;
}

export interface DeduplicationStats {
  totalLeads: number;
  duplicatesFound: number;
  mergedCount: number;
  finalCount: number;
  mergeDetails: Array<{
    keptLeadId: string;
    mergedLeadIds: string[];
    identifiers: {
      email?: string;
      phone?: string;
      aadhar?: string;
    };
  }>;
}

export interface DeduplicationConfig {
  useEmail: boolean;
  usePhone: boolean;
  useAadhar: boolean;
}

// Default deduplication configuration
let deduplicationConfig: DeduplicationConfig = {
  useEmail: true,
  usePhone: true,
  useAadhar: true
};

/**
 * Get current deduplication configuration
 */
export const getDeduplicationConfig = (): DeduplicationConfig => {
  return { ...deduplicationConfig };
};

/**
 * Update deduplication configuration
 */
export const updateDeduplicationConfig = (config: Partial<DeduplicationConfig>): DeduplicationConfig => {
  deduplicationConfig = { ...deduplicationConfig, ...config };
  return getDeduplicationConfig();
};

/**
 * Find duplicate leads based on normalized fields
 */
const findDuplicateGroups = async (config: DeduplicationConfig): Promise<ILead[][]> => {
  const duplicateGroups: ILead[][] = [];
  const processed = new Set<string>();

  // Get all leads
  const allLeads = await Lead.find({});

  for (const lead of allLeads) {
    if (processed.has(lead.lead_id)) {
      continue;
    }

    // Build query conditions based on config
    const conditions: any[] = [];
    
    if (config.useEmail && lead.email) {
      conditions.push({ email: lead.email, _id: { $ne: lead._id } });
    }
    
    if (config.usePhone && lead.phone_number) {
      conditions.push({ phone_number: lead.phone_number, _id: { $ne: lead._id } });
    }
    
    if (config.useAadhar && lead.aadhar_number) {
      conditions.push({ aadhar_number: lead.aadhar_number, _id: { $ne: lead._id } });
    }

    if (conditions.length === 0) {
      continue;
    }

    // Find duplicates using OR condition
    const duplicates = await Lead.find({ $or: conditions });

    if (duplicates.length > 0) {
      // Group: existing lead + all duplicates
      const group = [lead, ...duplicates];
      
      // Sort by created_at (oldest first - keep oldest)
      group.sort((a, b) => 
        new Date(a.created_at).getTime() - new Date(b.created_at).getTime()
      );

      duplicateGroups.push(group);

      // Mark all as processed
      group.forEach(l => processed.add(l.lead_id));
    }
  }

  return duplicateGroups;
};

/**
 * Merge duplicate leads into the oldest lead
 */
const mergeLeads = async (group: ILead[]): Promise<{
  keptLeadId: string;
  mergedLeadIds: string[];
}> => {
  if (group.length < 2) {
    throw new Error('Group must have at least 2 leads to merge');
  }

  // Oldest lead (first in sorted group) is kept
  const keptLead = group[0];
  const toMerge = group.slice(1);

  // Merge data from newer leads into oldest
  for (const lead of toMerge) {
    // Fill missing fields
    if (!keptLead.name && lead.name) {
      keptLead.name = lead.name;
    }

    if (!keptLead.phone_number && lead.phone_number) {
      keptLead.phone_number = lead.phone_number;
    }

    if (!keptLead.email && lead.email) {
      keptLead.email = lead.email;
    }

    if (!keptLead.aadhar_number && lead.aadhar_number) {
      keptLead.aadhar_number = lead.aadhar_number;
    }

    // Merge sources_seen (unique values)
    lead.sources_seen.forEach((sourceId) => {
      if (!keptLead.sources_seen.includes(sourceId)) {
        keptLead.sources_seen.push(sourceId);
      }
    });

    // Merge products_seen (unique values)
    lead.products_seen.forEach((productId) => {
      if (!keptLead.products_seen.includes(productId)) {
        keptLead.products_seen.push(productId);
      }
    });

    // Update source_id and p_id if kept lead doesn't have them
    if (!keptLead.source_id && lead.source_id) {
      keptLead.source_id = lead.source_id;
    }

    if (!keptLead.p_id && lead.p_id) {
      keptLead.p_id = lead.p_id;
    }

    // Add to merged_from array
    keptLead.merged_from.push({
      lead_id: lead.lead_id,
      merged_at: new Date(),
      source_id: lead.source_id,
      p_id: lead.p_id
    } as MergeRecord);
  }

  // Save the merged lead
  await keptLead.save();

  // Delete merged leads
  const mergedLeadIds = toMerge.map(l => l.lead_id);
  await Lead.deleteMany({
    lead_id: { $in: mergedLeadIds }
  });

  return {
    keptLeadId: keptLead.lead_id,
    mergedLeadIds
  };
};

/**
 * Execute deduplication on all leads
 */
export const executeDeduplication = async (
  config?: Partial<DeduplicationConfig>
): Promise<DeduplicationStats> => {
  const activeConfig = config 
    ? { ...deduplicationConfig, ...config }
    : deduplicationConfig;

  // Get total leads before deduplication
  const totalLeads = await Lead.countDocuments();

  // Find duplicate groups
  const duplicateGroups = await findDuplicateGroups(activeConfig);
  
  const mergeDetails: DeduplicationStats['mergeDetails'] = [];
  let mergedCount = 0;

  // Process each duplicate group
  for (const group of duplicateGroups) {
    const result = await mergeLeads(group);
    
    mergeDetails.push({
      keptLeadId: result.keptLeadId,
      mergedLeadIds: result.mergedLeadIds,
      identifiers: {
        email: group[0].email || undefined,
        phone: group[0].phone_number || undefined,
        aadhar: group[0].aadhar_number || undefined
      }
    });

    mergedCount += result.mergedLeadIds.length;
  }

  // Get final count
  const finalCount = await Lead.countDocuments();

  return {
    totalLeads,
    duplicatesFound: duplicateGroups.reduce((sum, group) => sum + group.length - 1, 0),
    mergedCount,
    finalCount,
    mergeDetails
  };
};

/**
 * Get deduplication statistics without executing
 */
export const getDeduplicationStats = async (): Promise<{
  totalLeads: number;
  potentialDuplicates: number;
  config: DeduplicationConfig;
}> => {
  const totalLeads = await Lead.countDocuments();

  // Count potential duplicates (simplified - just check for any matches)
  const emailDuplicates = await Lead.aggregate([
    { $match: { email: { $exists: true, $ne: null } } },
    { $group: { _id: '$email', count: { $sum: 1 } } },
    { $match: { count: { $gt: 1 } } },
    { $count: 'duplicates' }
  ]);

  const phoneDuplicates = await Lead.aggregate([
    { $match: { phone_number: { $exists: true, $ne: null } } },
    { $group: { _id: '$phone_number', count: { $sum: 1 } } },
    { $match: { count: { $gt: 1 } } },
    { $count: 'duplicates' }
  ]);

  const aadharDuplicates = await Lead.aggregate([
    { $match: { aadhar_number: { $exists: true, $ne: null } } },
    { $group: { _id: '$aadhar_number', count: { $sum: 1 } } },
    { $match: { count: { $gt: 1 } } },
    { $count: 'duplicates' }
  ]);

  const potentialDuplicates = 
    (emailDuplicates[0]?.duplicates || 0) +
    (phoneDuplicates[0]?.duplicates || 0) +
    (aadharDuplicates[0]?.duplicates || 0);

  return {
    totalLeads,
    potentialDuplicates,
    config: getDeduplicationConfig()
  };
};
