export interface RawRow {
    [key: string]: any;
  }
  
  export interface NormalizedLeadInput {
    name?: string;
    phone_number?: string;
    email?: string;
    aadhar_number?: string;
  }
  
  // Maps different column names to standard names
  const headerMap: Record<string, keyof NormalizedLeadInput> = {
    // Name variations
    'name': 'name',
    'full_name': 'name',
    'fullname': 'name',
    'customer_name': 'name',
    'customername': 'name',
    'client_name': 'name',
    
    // Phone variations
    'phone': 'phone_number',
    'phone_number': 'phone_number',
    'phonenumber': 'phone_number',
    'mobile': 'phone_number',
    'mobile_number': 'phone_number',
    'contact': 'phone_number',
    'contact_number': 'phone_number',
    
    // Email variations
    'email': 'email',
    'email_id': 'email',
    'emailid': 'email',
    'mail': 'email',
    'e_mail': 'email',
    'email_address': 'email',
    
    // Aadhar variations
    'aadhar': 'aadhar_number',
    'aadhaar': 'aadhar_number',
    'aadhar_number': 'aadhar_number',
    'aadhaar_number': 'aadhar_number',
    'aadhar_no': 'aadhar_number'
  };
  
  // Convert messy column names to standard names
  export const normalizeHeaders = (headers: string[]): (keyof NormalizedLeadInput | null)[] => {
    return headers.map((header) => {
      const normalized = header.trim().toLowerCase().replace(/\s+/g, '_');
      return headerMap[normalized] || null;
    });
  };
  
  // Clean phone number (remove spaces, dashes, country code)
  export const normalizePhone = (value?: string | number | null): string | undefined => {
    if (!value) return undefined;
    
    const strValue = String(value).trim();
    if (!strValue) return undefined;
    
    // Extract only digits
    const digits = strValue.replace(/\D/g, '');
    if (!digits) return undefined;
    
    // Handle Indian country code (+91)
    if (digits.length > 10) {
      if (digits.startsWith('91') && digits.length === 12) {
        return digits.slice(2); // Remove 91 prefix
      }
      return digits.slice(-10); // Take last 10 digits
    }
    
    if (digits.length < 10) return undefined;
    
    return digits;
  };
  
  // Clean email (lowercase and trim)
  export const normalizeEmail = (value?: string | null): string | undefined => {
    if (!value) return undefined;
    
    const email = String(value).trim().toLowerCase();
    if (!email) return undefined;
    
    // Basic email validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) return undefined;
    
    return email;
  };
  
  // Clean Aadhar (digits only, must be 12 digits)
  export const normalizeAadhar = (value?: string | number | null): string | undefined => {
    if (!value) return undefined;
    
    const strValue = String(value).trim();
    if (!strValue) return undefined;
    
    const digits = strValue.replace(/\D/g, '');
    if (!digits) return undefined;
    
    // Aadhar must be exactly 12 digits
    if (digits.length !== 12) return undefined;
    
    return digits;
  };
  
  // Process one row of data
  export const normalizeRowValues = (
    row: any,
    headerMapping: (keyof NormalizedLeadInput | null)[]
  ): NormalizedLeadInput => {
    const result: NormalizedLeadInput = {};
    
    // Handle object rows (from CSV/Excel parsing)
    const rowKeys = Object.keys(row);
    
    rowKeys.forEach((originalKey, index) => {
      const mappedKey = headerMapping[index];
      
      // Skip if no mapping or if mapped to null
      if (!mappedKey) return;
      
      const rawValue = row[originalKey];
      
      // Skip empty values
      if (rawValue === undefined || rawValue === null || rawValue === '') return;
      
      // Apply normalization based on field type
      switch (mappedKey) {
        case 'phone_number':
          const phone = normalizePhone(rawValue);
          if (phone) result.phone_number = phone;
          break;
          
        case 'email':
          const email = normalizeEmail(rawValue);
          if (email) result.email = email;
          break;
          
        case 'aadhar_number':
          const aadhar = normalizeAadhar(rawValue);
          if (aadhar) result.aadhar_number = aadhar;
          break;
          
        case 'name':
          const name = String(rawValue).trim();
          if (name) result.name = name;
          break;
      }
    });
    
    return result;
  };
  
  export interface ValidationResult {
    valid: boolean;
    reason?: string;
  }
  
  // Check if lead has at least one identifier
  export const validateIdentifiers = (lead: NormalizedLeadInput): ValidationResult => {
    const hasPhone = !!lead.phone_number;
    const hasEmail = !!lead.email;
    const hasAadhar = !!lead.aadhar_number;
    
    if (hasPhone || hasEmail || hasAadhar) {
      return { valid: true };
    }
    
    return {
      valid: false,
      reason: 'At least one identifier (phone_number, email, or aadhar_number) is required'
    };
  };
  