import dotenv from 'dotenv';
dotenv.config();

import { connectDB } from '../config/db';
import { Lead } from '../models/Lead';
import {
  normalizePhone,
  normalizeEmail,
  normalizeAadhar,
  normalizeHeaders,
  validateIdentifiers
} from '../utils/leadNormalization';
import { upsertLead, findExistingLead } from '../services/leadService';

const assert = (condition: boolean, testName: string): void => {
  if (condition) {
    console.log(`✓ ${testName}`);
  } else {
    console.error(`✗ ${testName}`);
    process.exitCode = 1;
  }
};

const runTests = async (): Promise<void> => {
  console.log('Running backend tests...\n');
  
  // Test 1: Phone normalization
  console.log('--- Phone Normalization Tests ---');
  assert(
    normalizePhone('+91-9876543210') === '9876543210',
    'normalizePhone removes country code and formatting'
  );
  assert(
    normalizePhone('98765 43210') === '9876543210',
    'normalizePhone removes spaces'
  );
  assert(
    normalizePhone('919876543210') === '9876543210',
    'normalizePhone handles 91 prefix'
  );
  assert(
    normalizePhone('12345') === undefined,
    'normalizePhone rejects short numbers'
  );
  
  // Test 2: Email normalization
  console.log('\n--- Email Normalization Tests ---');
  assert(
    normalizeEmail('  TEST@EXAMPLE.COM  ') === 'test@example.com',
    'normalizeEmail converts to lowercase and trims'
  );
  assert(
    normalizeEmail('invalid-email') === undefined,
    'normalizeEmail rejects invalid format'
  );
  
  // Test 3: Aadhar normalization
  console.log('\n--- Aadhar Normalization Tests ---');
  assert(
    normalizeAadhar('1234-5678-9012') === '123456789012',
    'normalizeAadhar removes dashes'
  );
  assert(
    normalizeAadhar('1234 5678 9012') === '123456789012',
    'normalizeAadhar removes spaces'
  );
  assert(
    normalizeAadhar('12345') === undefined,
    'normalizeAadhar rejects non-12-digit numbers'
  );
  
  // Test 4: Header normalization
  console.log('\n--- Header Normalization Tests ---');
  const headers = ['Full Name', 'Phone Number', 'Email ID', 'Aadhar'];
  const normalized = normalizeHeaders(headers);
  assert(
    normalized[0] === 'name' && normalized[1] === 'phone_number',
    'normalizeHeaders maps column variations correctly'
  );
  
  // Test 5: Identifier validation
  console.log('\n--- Identifier Validation Tests ---');
  assert(
    validateIdentifiers({ phone_number: '9876543210' }).valid,
    'validateIdentifiers accepts lead with phone'
  );
  assert(
    !validateIdentifiers({}).valid,
    'validateIdentifiers rejects lead without identifiers'
  );
  
  // Database tests
  if (!process.env.MONGODB_URI) {
    console.log('\n⚠ Skipping database tests (MONGODB_URI not set)');
    return;
  }
  
  await connectDB(process.env.MONGODB_URI);
  
  // Clear test data
  await Lead.deleteMany({ lead_id: /^test-/ });
  
  console.log('\n--- Deduplication Tests ---');
  
  // Test 6: Insert new lead
  const firstInsert = await upsertLead(
    {
      name: 'Test User',
      phone_number: '9999999999',
      email: 'test@example.com'
    },
    {
      p_id: 'PL',
      source_id: 'WEB',
      rawRow: { row: 1 }
    }
  );
  assert(
    firstInsert.action === 'inserted',
    'First lead is inserted'
  );
  
  // Test 7: Merge duplicate by phone
  const secondInsert = await upsertLead(
    {
      phone_number: '9999999999',
      aadhar_number: '999999999999'
    },
    {
      p_id: 'HL',
      source_id: 'MKT',
      rawRow: { row: 2 }
    }
  );
  assert(
    secondInsert.action === 'merged',
    'Duplicate lead (by phone) is merged'
  );
  
  // Test 8: Verify merge filled missing fields
  const merged = secondInsert.lead;
  assert(
    merged.aadhar_number === '999999999999',
    'Merge filled missing aadhar_number'
  );
  assert(
    merged.sources_seen.includes('WEB') && merged.sources_seen.includes('MKT'),
    'Merge tracked both sources'
  );
  
  // Test 9: Verify only one lead exists
  const allTestLeads = await Lead.find({ phone_number: '9999999999' });
  assert(
    allTestLeads.length === 1,
    'Only one canonical lead exists after merge'
  );
  
  // Test 10: Merge by email
  const thirdInsert = await upsertLead(
    {
      name: 'Updated Name',
      email: 'test@example.com'
    },
    {
      p_id: 'CL',
      source_id: 'PART',
      rawRow: { row: 3 }
    }
  );
  assert(
    thirdInsert.action === 'merged',
    'Duplicate lead (by email) is merged'
  );
  
  // Cleanup
  await Lead.deleteMany({ lead_id: /^test-/ });
  await Lead.deleteMany({ phone_number: '9999999999' });
  
  console.log('\n✓ All tests completed!');
};

const main = async (): Promise<void> => {
  try {
    await runTests();
    process.exit(0);
  } catch (error) {
    console.error('\n✗ Test suite failed:', error);
    process.exit(1);
  }
};

main();
