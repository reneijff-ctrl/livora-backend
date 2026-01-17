### Authentication API Contract

This document specifies the authentication API contract between the frontend and the backend.

#### General Security Requirements

1.  **JWT Authorization**: Endpoints marked as requiring JWT must include the `Authorization: Bearer <token>` header.
2.  **CSRF Protection**:
    *   The backend uses `CookieCsrfTokenRepository.withHttpOnlyFalse()`.
    *   The frontend must read the `XSRF-TOKEN` cookie and send its value in the `X-XSRF-TOKEN` header for all state-changing requests (POST, PUT, DELETE), except those explicitly ignored.
    *   Ignored endpoints for CSRF: `/auth/login`, `/auth/register`.
3.  **Refresh Token**:
    *   Stored in a `HttpOnly`, `Secure`, `SameSite=None` cookie named `refreshToken`.
    *   The cookie path is restricted to `/auth/refresh`.

---

#### 1. POST /auth/login
Authenticates a user and returns an access token. Sets the refresh token cookie.

*   **URL**: `/auth/login`
*   **Method**: `POST`
*   **Auth Required**: No
*   **CSRF Required**: No
*   **Headers**:
    *   `Content-Type: application/json`

**Request Body**:
```json
{
  "email": "user@example.com",
  "password": "yourpassword"
}
```

**Response**:
*   **Status**: `200 OK`
*   **Headers**:
    *   `Set-Cookie`: `refreshToken=<token>; Path=/auth/refresh; HttpOnly; Secure; SameSite=None; Max-Age=604800`
*   **Body**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "message": "Login successful"
}
```

---

#### 2. POST /auth/refresh
Refreshes the access token using the refresh token stored in the cookie.

*   **URL**: `/auth/refresh`
*   **Method**: `POST`
*   **Auth Required**: No (Uses `refreshToken` cookie)
*   **CSRF Required**: Yes (`X-XSRF-TOKEN` header)
*   **Headers**:
    *   `Content-Type: application/json`

**Request Body**: None (Uses cookie)

**Response**:
*   **Status**: `200 OK`
*   **Headers**:
    *   `Set-Cookie`: `refreshToken=<new_token>; Path=/auth/refresh; HttpOnly; Secure; SameSite=None; Max-Age=604800`
*   **Body**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "message": "Token refreshed"
}
```

---

#### 3. POST /auth/logout
Invalidates the refresh token and clears the cookie.

*   **URL**: `/auth/logout`
*   **Method**: `POST`
*   **Auth Required**: No (Uses `refreshToken` cookie)
*   **CSRF Required**: Yes (`X-XSRF-TOKEN` header)

**Request Body**: None

**Response**:
*   **Status**: `200 OK`
*   **Headers**:
    *   `Set-Cookie`: `refreshToken=; Path=/auth/refresh; HttpOnly; Secure; SameSite=None; Max-Age=0`
*   **Body**:
```json
{
  "message": "Logged out successfully"
}
```

---

#### 4. GET /auth/me
Returns the current authenticated user's profile and subscription status.

*   **URL**: `/auth/me`
*   **Method**: `GET`
*   **Auth Required**: Yes (`Authorization: Bearer <accessToken>`)
*   **CSRF Required**: No (GET request)

**Response**:
*   **Status**: `200 OK`
*   **Body**:
```json
{
  "id": 123,
  "email": "user@example.com",
  "role": "USER",
  "subscription": {
    "status": "ACTIVE",
    "renewalDate": "2026-02-17T04:19:00Z",
    "cancelAtPeriodEnd": false,
    "nextInvoiceDate": "2026-02-17T04:19:00Z",
    "paymentMethodBrand": "visa",
    "last4": "4242"
  }
}
```
*Note: `subscription.status` can be `NONE`, `ACTIVE`, `PAST_DUE`, `CANCELED`, etc.*

---

#### 5. POST /auth/register
Registers a new user.

*   **URL**: `/auth/register`
*   **Method**: `POST`
*   **Auth Required**: No
*   **CSRF Required**: No
*   **Headers**:
    *   `Content-Type: application/json`

**Request Body**:
```json
{
  "email": "newuser@example.com",
  "password": "securepassword",
  "role": "USER"
}
```

**Response**:
*   **Status**: `200 OK`
*   **Body**:
```json
{
  "message": "User registered successfully"
}
```
