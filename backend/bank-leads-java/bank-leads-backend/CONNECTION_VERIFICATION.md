# Database Connection Verification Guide

## ✅ Fixed Issues

1. **Removed invalid `application.properties`** - Had YAML syntax instead of properties format
2. **Moved `application.yml` to standard location** - `src/main/resources/application.yml`
3. **Added CORS to all controllers** - LeadController, CanonicalFieldController, DeduplicationController
4. **Verified MongoDB URI** - Points to `lead_management` database

## 🔍 Current Configuration

### MongoDB Connection
- **URI**: `mongodb+srv://sheeryndsouza2204_db_user:sheeshaa2207@cluster0.ud4xnhv.mongodb.net/lead_management?retryWrites=true&w=majority`
- **Database Name**: `lead_management`
- **Collections**:
  - `leads`
  - `sources`
  - `products`
  - `canonical_fields`

### Spring Boot
- **Port**: `4000`
- **CORS**: Enabled for `http://localhost:4200`

### Frontend
- **API URL**: `http://localhost:4000/api`
- **Dev Server**: `http://localhost:4200`

## 🧪 Testing Steps

### 1. Start Spring Boot Backend
```bash
cd backend/bank-leads-java/bank-leads-backend
mvn spring-boot:run
```

**Expected Output:**
- Should see MongoDB connection logs
- Server starts on port 4000
- No connection errors

### 2. Test Health Endpoint
```bash
curl http://localhost:4000/health
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "status": "ok",
    "timestamp": "..."
  }
}
```

### 3. Test API Endpoints
```bash
# Test Products
curl http://localhost:4000/api/products

# Test Sources
curl http://localhost:4000/api/sources

# Test Canonical Fields
curl http://localhost:4000/api/canonical-fields

# Test Leads
curl http://localhost:4000/api/leads
```

### 4. Start Angular Frontend
```bash
cd frontend
npm start
# or
ng serve
```

**Expected:**
- Frontend loads at `http://localhost:4200`
- Can see data from backend
- No CORS errors in browser console

## 🐛 Troubleshooting

### If MongoDB connection fails:

1. **Check MongoDB Atlas Network Access**
   - Go to MongoDB Atlas → Network Access
   - Ensure your IP is whitelisted (or allow `0.0.0.0/0` for testing)

2. **Verify Database Credentials**
   - Check username/password in `application.yml`
   - Ensure database user has read/write permissions

3. **Check Database Name**
   - Verify `lead_management` database exists in Atlas
   - Collections will be created automatically on first use

4. **Check Connection String**
   - URI should include database name: `...mongodb.net/lead_management?...`
   - Ensure `retryWrites=true&w=majority` parameters are present

### If Frontend can't connect to Backend:

1. **Check Backend is Running**
   - Verify Spring Boot is running on port 4000
   - Test `http://localhost:4000/health`

2. **Check CORS**
   - All controllers now have `@CrossOrigin(origins = "*")`
   - WebConfig also has CORS for `/api/**`

3. **Check Browser Console**
   - Look for CORS errors
   - Check Network tab for failed requests

4. **Verify Environment Config**
   - `frontend/src/environments/environment.ts` should have:
     ```typescript
     apiUrl: 'http://localhost:4000/api'
     ```

## 📝 Quick Verification Commands

```bash
# Check if Spring Boot compiles
cd backend/bank-leads-java/bank-leads-backend
mvn clean compile

# Check if application.yml is in correct location
ls src/main/resources/application.yml

# Verify no old Node.js backend is running
# (Should only have Spring Boot on port 4000)
netstat -ano | findstr :4000
```
