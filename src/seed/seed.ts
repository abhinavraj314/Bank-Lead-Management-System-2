import dotenv from 'dotenv';
dotenv.config();

import { connectDB } from '../config/db';
import { Product } from '../models/Product';
import { Source } from '../models/Source';
import { Lead } from '../models/Lead';

const MONGO_URI = process.env.MONGODB_URI;

if (!MONGO_URI) {
  console.error('✗ MONGODB_URI not defined');
  process.exit(1);
}

const seedData = async (): Promise<void> => {
  try {
    console.log('Starting seed process...');
    
    // Clear existing data
    await Promise.all([
      Product.deleteMany({}),
      Source.deleteMany({}),
      Lead.deleteMany({})
    ]);
    console.log('✓ Cleared existing data');
    
    // Create products
    const products = await Product.insertMany([
      { p_id: 'PL', p_name: 'Personal Loan' },
      { p_id: 'HL', p_name: 'Home Loan' },
      { p_id: 'CL', p_name: 'Car Loan' },
      { p_id: 'CC', p_name: 'Credit Card' }
    ]);
    console.log(`✓ Created ${products.length} products`);
    
    // Create sources
    const sources = await Source.insertMany([
      { source_id: 'WEB', source_name: 'Bank Website', p_id: 'PL' },
      { source_id: 'MKT', source_name: 'Marketing Campaign', p_id: 'HL' },
      { source_id: 'PART', source_name: 'Partner Platform', p_id: 'CL' },
      { source_id: 'AGG', source_name: 'Third-party Aggregator', p_id: 'CC' }
    ]);
    console.log(`✓ Created ${sources.length} sources`);
    
    // Create sample leads
    const sampleLeads = await Lead.insertMany([
      {
        lead_id: 'lead-001',
        name: 'Rajesh Kumar',
        phone_number: '9876543210',
        email: 'rajesh.kumar@example.com',
        aadhar_number: '123456789012',
        source_id: 'WEB',
        p_id: 'PL',
        created_at: new Date('2026-01-01'),
        merged_from: [],
        sources_seen: ['WEB'],
        products_seen: ['PL'],
        lead_score: null,
        score_reason: null
      },
      {
        lead_id: 'lead-002',
        name: 'Priya Sharma',
        phone_number: '9876543211',
        email: 'priya.sharma@example.com',
        source_id: 'MKT',
        p_id: 'HL',
        created_at: new Date('2026-01-02'),
        merged_from: [],
        sources_seen: ['MKT'],
        products_seen: ['HL'],
        lead_score: null,
        score_reason: null
      },
      {
        lead_id: 'lead-003',
        name: 'Amit Patel',
        phone_number: '9876543212',
        email: 'amit.patel@example.com',
        aadhar_number: '234567890123',
        source_id: 'PART',
        p_id: 'CL',
        created_at: new Date('2026-01-03'),
        merged_from: [],
        sources_seen: ['PART'],
        products_seen: ['CL'],
        lead_score: null,
        score_reason: null
      }
    ]);
    console.log(`✓ Created ${sampleLeads.length} sample leads`);
    
    console.log('\n✓ Seed completed successfully!');
    console.log('\nCreated:');
    console.log('- Products:', products.map(p => p.p_id).join(', '));
    console.log('- Sources:', sources.map(s => s.source_id).join(', '));
    console.log('- Sample Leads:', sampleLeads.length);
  } catch (error) {
    console.error('✗ Seed failed:', error);
    throw error;
  }
};

const run = async (): Promise<void> => {
  await connectDB(MONGO_URI!);
  await seedData();
  process.exit(0);
};

run().catch((error) => {
  console.error('Fatal error:', error);
  process.exit(1);
});
