# Bank Lead Management System - Backend API

A Node.js + TypeScript Express API backend for managing bank leads, products, and sources.

## Features

- RESTful API with Express.js
- MongoDB database with Mongoose ODM
- TypeScript with strict mode enabled
- Hot reload development with ts-node-dev
- Security middleware (Helmet, CORS)
- Request logging with Morgan
- Environment-based configuration
- CRUD operations for Products, Sources, and Leads

## Project Structure

```
src/
├── config/          # Configuration files (database, environment)
├── models/          # Mongoose models (Product, Source, Lead)
├── routes/          # API route definitions
├── controllers/     # Request handlers
├── services/        # Business logic layer
├── utils/           # Utility functions and helpers
├── tests/           # Test files
├── seed/            # Database seeding scripts
├── app.ts           # Express app configuration
└── server.ts        # Server entry point
```

## Prerequisites

- Node.js (v16 or higher)
- MongoDB (local or remote instance)
- npm or yarn

## Installation

1. Clone the repository
2. Install dependencies:
   ```bash
   npm install
   ```

3. Create a `.env` file in the root directory:
   ```env
   PORT=3000
   NODE_ENV=development
   MONGODB_URI=mongodb://localhost:27017/bank-leads
   API_VERSION=v1
   ```

## Running the Application

### Development Mode (with hot reload)
```bash
npm run dev
```

### Production Mode
```bash
npm run build
npm start
```

### Seed Database
```bash
npm run seed
```

## API Endpoints

### Products
- `GET /api/products` - Get all products
- `GET /api/products/:id` - Get product by ID
- `POST /api/products` - Create a new product
- `PUT /api/products/:id` - Update a product
- `DELETE /api/products/:id` - Delete a product

### Sources
- `GET /api/sources` - Get all sources
- `GET /api/sources/:id` - Get source by ID
- `POST /api/sources` - Create a new source
- `PUT /api/sources/:id` - Update a source
- `DELETE /api/sources/:id` - Delete a source

### Leads
- `GET /api/leads` - Get all leads (supports query params: status, product, source)
- `GET /api/leads/:id` - Get lead by ID
- `POST /api/leads` - Create a new lead
- `PUT /api/leads/:id` - Update a lead
- `DELETE /api/leads/:id` - Delete a lead

### Health Check
- `GET /health` - Server health check

## Technologies Used

- **Express.js** - Web framework
- **Mongoose** - MongoDB ODM
- **TypeScript** - Type-safe JavaScript
- **ts-node-dev** - Development server with hot reload
- **Helmet** - Security middleware
- **CORS** - Cross-origin resource sharing
- **Morgan** - HTTP request logger
- **dotenv** - Environment variable management

## License

ISC





