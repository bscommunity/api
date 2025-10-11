# Authentication Guide

This guide explains the different authentication methods available in the BSCM API.

## Authentication Providers

### 1. `auth-bearer` - JWT Only
Requires a valid JWT token in the Authorization header.

**Use case:** Dashboard operations that require user authentication

**Headers:**
```
Authorization: Bearer <jwt_token>
```

**Example routes:**
- `POST /charts` - Create chart
- `PUT /charts/{id}` - Update chart
- `DELETE /charts/{id}` - Delete chart

---

### 2. `auth-hmac` - HMAC Only
Requires HMAC signature headers to verify the request comes from the mobile app.

**Use case:** Mobile app operations that don't need user context

**Headers:**
```
X-App-Timestamp: <timestamp_in_milliseconds>
X-App-Signature: <hmac_signature>
```

**Example routes:**
- `POST /charts/analytics/{id}` - Track analytics
- `GET /charts/suggestions` - Get search suggestions

---

### 3. `auth-combined` - JWT AND HMAC (Both Required)
Requires both JWT token and HMAC signature. Both authentications must be valid.

**Use case:** Mobile app operations that require both app verification and user context

**Headers:**
```
Authorization: Bearer <jwt_token>
X-App-Timestamp: <timestamp_in_milliseconds>
X-App-Signature: <hmac_signature>
```

**Example routes:**
- `GET /charts/mobile` - Get charts with user-specific data (mobile app only)

**Route implementation:**
```kotlin
authenticate("auth-combined") {
    get("/mobile") {
        val combined = call.principal<CombinedPrincipal>()!!
        
        // Access JWT data
        val userId = UUID.fromString(combined.jwtPrincipal.subject)
        val username = combined.jwtPrincipal.payload.getClaim("username").asString()
        
        // Access HMAC data
        val appId = combined.hmacPrincipal.appId
        val timestamp = combined.hmacPrincipal.timestamp
        
        // Your logic here...
    }
}
```

---

### 4. `auth-flexible` - JWT OR HMAC (At Least One Required)
Accepts either JWT, HMAC, or both. At least one valid authentication method must be present.

**Use case:** Routes that need to support both mobile app and dashboard clients

**Headers (choose one or both):**
```
# Option 1: JWT only (dashboard)
Authorization: Bearer <jwt_token>

# Option 2: HMAC only (mobile app, no user context)
X-App-Timestamp: <timestamp_in_milliseconds>
X-App-Signature: <hmac_signature>

# Option 3: Both JWT and HMAC (mobile app with user context)
Authorization: Bearer <jwt_token>
X-App-Timestamp: <timestamp_in_milliseconds>
X-App-Signature: <hmac_signature>
```

**Example routes:**
- `GET /charts` - Get all charts (works for both mobile app and dashboard)

**Route implementation:**
```kotlin
authenticate("auth-flexible") {
    get {
        val jwtPrincipal = call.principal<JWTPrincipal>()
        val hmacPrincipal = call.principal<HMACPrincipal>()
        val combinedPrincipal = call.principal<CombinedPrincipal>()

        when {
            // Both JWT and HMAC provided
            combinedPrincipal != null -> {
                val userId = UUID.fromString(combinedPrincipal.jwtPrincipal.subject)
                // Handle mobile app with user context
            }
            // Only HMAC provided
            hmacPrincipal != null -> {
                // Handle mobile app without user context
            }
            // Only JWT provided
            jwtPrincipal != null -> {
                val userId = UUID.fromString(jwtPrincipal.subject)
                // Handle dashboard user
            }
        }
    }
}
```

---

## Configuration

The authentication providers are configured in `Security.kt`:

```kotlin
install(Authentication) {
    // JWT only
    jwt("auth-bearer") { ... }
    
    // HMAC only
    hmac("auth-hmac") { ... }
    
    // JWT + HMAC (both required)
    combinedAuth("auth-combined") {
        jwtRequired = true
        hmacRequired = true
    }
    
    // JWT OR HMAC (at least one required)
    combinedAuth("auth-flexible") {
        jwtRequired = false
        hmacRequired = false
    }
}
```

---

## HMAC Signature Calculation

The HMAC signature is calculated using the following payload:
```
payload = "<timestamp>:"
signature = HMAC-SHA256(payload, secret)
```

The signature must be base64 encoded.

**Timestamp validation:**
- Must be within 5 minutes of current server time
- Prevents replay attacks

---

## Summary Table

| Provider | JWT Required | HMAC Required | Use Case |
|----------|--------------|---------------|----------|
| `auth-bearer` | ✅ | ❌ | Dashboard operations |
| `auth-hmac` | ❌ | ✅ | Mobile app (no user context) |
| `auth-combined` | ✅ | ✅ | Mobile app with user context |
| `auth-flexible` | Optional | Optional | Shared routes (both clients) |

---

## Migration Notes

The new `auth-flexible` provider replaces the previous pattern of:
```kotlin
authenticate("auth-bearer", "auth-hmac", optional = true) { ... }
```

This provides better type safety and clearer intent about which principals are available.

