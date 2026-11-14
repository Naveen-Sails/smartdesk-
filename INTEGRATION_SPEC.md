# SmartDesk Frontend-to-Backend Integration Specification

**Version:** 1.0.0  
**Target Environment:** Local / Microservices Architecture  
**Services Involved:**
- **Frontend Client:** `smartdesk-frontend-master` (Vite + React 19 @ `http://localhost:5173`)
- **API Gateway & Auth Provider:** `api-gateway` (Spring Cloud Gateway WebFlux @ `http://localhost:8080`)
- **Notification Service:** `notification-service` (Spring Boot WebMVC Async @ `http://localhost:8083`)
- **Downstream Ticket Service:** `ticket-service` (Spring Boot REST Service @ `http://localhost:8082`)

---

## 1. Architectural Topology & Network Contract

```
┌────────────────────────────────────────────────────────┐
│      Client Browser (smartdesk-frontend-master)        │
│                http://localhost:5173                   │
└──────────────────────────┬─────────────────────────────┘
                           │
                 HTTP REST with Bearer JWT
                           ▼
┌────────────────────────────────────────────────────────┐
│           API Gateway (api-gateway: 8080)              │
│       CORS Allowed Origin: http://localhost:5173       │
│                                                        │
│  ├── /api/v1/auth/** ────────► Internal OtpAuthService │
│  │                                 │                   │
│  │                                 ▼                   │
│  │                     Async HTTP (WebClient)          │
│  │                                 ▼                   │
│  │                 Notification Service (:8083)        │
│  │                 (/api/v1/notifications/otp)         │
│  │                                                     │
│  └── /api/v1/tickets/** ───────────────────────────────┼──┐
│         (JwtAuthenticationFilter validates JWT &       │  │
│          injects X-User-Id, X-User-Email, X-User-Role) │  │
└────────────────────────────────────────────────────────┘  │
                                                            │
                                  Reverse Proxy Routing     ▼
                               ┌───────────────────────────────┐
                               │ Ticket Service (:8082)        │
                               │ /api/v1/tickets/**            │
                               └───────────────────────────────┘
```

---

## 2. API Gateway Configuration Specs (`api-gateway`)

> **Guiding Principle:** Preserves all existing database schemas (`users`, `otp_verifications`, `role_audit_logs`), BCrypt OTP hashing, 60s rate-limiting, and token generation logic.

### 2.1 CORS Policy Specification
The API Gateway must allow cross-origin communication from the Vite development server.

#### Specification Requirements:
- **Allowed Origins:** `http://localhost:5173`, `http://127.0.0.1:5173`
- **Allowed Methods:** `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`
- **Allowed Headers:** `Authorization`, `Content-Type`, `Accept`, `X-Requested-With`, `Origin`, `Access-Control-Request-Method`, `Access-Control-Request-Headers`
- **Exposed Headers:** `Authorization`, `X-User-Id`, `X-User-Role`, `X-User-Email`
- **Allow Credentials:** `true`
- **Max Age:** `3600` seconds

#### Target Spring Cloud Gateway Implementation Shape:
```yaml
# In application.yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins:
              - "http://localhost:5173"
              - "http://127.0.0.1:5173"
            allowed-methods:
              - GET
              - POST
              - PUT
              - PATCH
              - DELETE
              - OPTIONS
            allowed-headers: "*"
            allow-credentials: true
            max-age: 3600
```
Or via a `CorsWebFilter` bean inside `SecurityConfig.java`.

---

## 3. Authentication & Authorization API Contract

### 3.1 Endpoint: Request OTP
Triggers the generation of a 6-digit numeric OTP and asynchronous dispatch via email.

- **URL:** `/api/v1/auth/otp/request`
- **Method:** `POST`
- **Authentication:** Public (No Bearer token required)
- **Content-Type:** `application/json`

#### Request Payload Schema:
```json
{
  "email": "employee@smartdesk.com"
}
```
| Field | Type | Validation | Description |
| :--- | :--- | :--- | :--- |
| `email` | `string` | Required, Valid Email format | The registered user's email address |

#### Response: 200 OK (Generic Anti-Enumeration Message)
```json
{
  "message": "If an account exists with this email, a verification code has been sent.",
  "status": 200,
  "timestamp": "2026-11-13T16:00:00"
}
```

#### Error Response: 429 Too Many Requests (Rate Limiting Triggered)
Triggered when requesting an OTP less than 60 seconds after the last request for this account.
```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Please wait 42 seconds before requesting another OTP.",
  "timestamp": "2026-11-13T16:00:18"
}
```

#### Error Response: 400 Bad Request (Validation Error)
```json
{
  "status": 400,
  "error": "Bad Request",
  "details": {
    "email": "Must be a valid email address"
  },
  "timestamp": "2026-11-13T16:00:00"
}
```

---

### 3.2 Endpoint: Verify OTP & Issue Token
Validates the submitted 6-digit numeric OTP against the active BCrypt hash in `otp_verifications`.

- **URL:** `/api/v1/auth/otp/verify`
- **Method:** `POST`
- **Authentication:** Public (No Bearer token required)
- **Content-Type:** `application/json`

#### Request Payload Schema:
```json
{
  "email": "employee@smartdesk.com",
  "otp": "481920"
}
```
| Field | Type | Validation | Description |
| :--- | :--- | :--- | :--- |
| `email` | `string` | Required, Valid Email format | The registered user's email address |
| `otp` | `string` | Required, Regex `^\d{6}$` | 6-digit numeric verification code |

#### Response: 200 OK (Success with JWT)
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJlbXBsb3llZUBzbWFydGRlc2suY29tIiwidXNlcklkIjoiZjRhYTEyMzQt...",
  "tokenType": "Bearer",
  "user": {
    "id": "f4aa1234-5678-90ab-cdef-1234567890ab",
    "fullName": "Maya Chen",
    "email": "employee@smartdesk.com",
    "role": "ROLE_EMPLOYEE",
    "department": "IT",
    "isRootAdmin": false,
    "isActive": true,
    "createdAt": "2026-11-13T10:00:00"
  }
}
```

#### Error Response: 401 Unauthorized (Invalid / Expired / Locked OTP)
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid OTP. Attempts remaining: 2",
  "timestamp": "2026-11-13T16:01:00"
}
```
*Note: After 3 failed attempts, the code is locked and responds: `"Maximum verification attempts exceeded. Please request a new OTP."`*

---

### 3.3 Endpoint: Request Registration OTP (New User)
Creates an unactivated user record (`isActive = false`) and dispatches a 6-digit numeric activation OTP.

- **URL:** `/api/v1/auth/register/request`
- **Method:** `POST`
- **Authentication:** Public
- **Content-Type:** `application/json`

#### Request Payload Schema:
```json
{
  "fullName": "New Employee",
  "email": "new.employee@smartdesk.com",
  "role": "ROLE_EMPLOYEE",
  "department": "IT"
}
```

#### Response: 200 OK
```json
{
  "message": "Verification code sent to your email.",
  "status": 200,
  "timestamp": "2026-11-13T16:00:00"
}
```

#### Error: 400 Bad Request (Already Registered Active User)
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "An active account already exists with this email address. Please sign in instead.",
  "timestamp": "2026-11-13T16:00:00"
}
```

---

### 3.4 Endpoint: Verify Registration OTP & Activate User
Validates the 6-digit OTP, sets `isActive = true`, and issues a signed 24-hour Bearer JWT.

- **URL:** `/api/v1/auth/register/verify`
- **Method:** `POST`
- **Authentication:** Public
- **Content-Type:** `application/json`

#### Request Payload Schema:
```json
{
  "email": "new.employee@smartdesk.com",
  "otp": "654321"
}
```

#### Response: 200 OK (User Activated with JWT)
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "user": {
    "id": "11223344-5566-7788-99aa-bbccddeeff00",
    "fullName": "New Employee",
    "email": "new.employee@smartdesk.com",
    "role": "ROLE_EMPLOYEE",
    "department": "IT",
    "isRootAdmin": false,
    "isActive": true,
    "createdAt": "2026-11-13T16:00:00"
  }
}
```

---

## 4. Frontend Client Specification (`smartdesk-frontend-master`)

### 4.1 Role Identifier Mapping
The backend uses Spring Security authority naming (`ROLE_*`), while the frontend uses clean role keys.

| Backend Role (`User.role`) | Frontend Context Key (`ROLES`) | Frontend Landing Route |
| :--- | :--- | :--- |
| `ROLE_EMPLOYEE` | `EMPLOYEE` | `/employee` |
| `ROLE_AGENT` | `AGENT` | `/agent` |
| `ROLE_MANAGER` | `MANAGER` | `/manager` |
| `ROLE_ADMIN` | `ADMIN` | `/admin` |

**Adapter Requirement:** Upon receiving the `AuthResponse`, the frontend must normalize the role:
```javascript
const frontendRole = backendRole.replace(/^ROLE_/, '');
```

---

### 4.2 HTTP Client Specification (`src/services/api.js`)
Axios must be configured to target the API Gateway (`http://localhost:8080`) and automatically attach the JWT token.

#### Contract Requirements:
1. **Base URL:**
   - In Development: `http://localhost:8080` (or `/api` if using Vite proxy)
2. **Request Interceptor:**
   - Reads `smartdesk_token` from browser storage.
   - If present, injects header: `Authorization: Bearer <token>`.
3. **Response Interceptor:**
   - Intercepts HTTP `401 Unauthorized`.
   - Clears `smartdesk_token` and `smartdesk_session`.
   - Emits session expiration event or redirects to `/`.

---

### 4.3 Authentication UI State Machine (`src/pages/auth/AuthFormPage.jsx`)
The authentication form transitions from a single-screen password form to a **two-step OTP state machine**.

```
   ┌──────────────────────────────────────────────┐
   │             STEP 1: EMAIL INPUT              │
   │  - User selects Workspace (Role)             │
   │  - Inputs registered email address           │
   │  - Submits to /api/v1/auth/otp/request       │
   └──────────────────────┬───────────────────────┘
                          │ (HTTP 200)
                          ▼
   ┌──────────────────────────────────────────────┐
   │             STEP 2: OTP VERIFY               │
   │  - 6-digit numeric input with auto-focus     │
   │  - 60s cooldown timer for "Resend Code"      │
   │  - Shows remaining attempts on error (401)   │
   │  - Submits to /api/v1/auth/otp/verify        │
   └──────────────────────┬───────────────────────┘
                          │ (HTTP 200 + JWT)
                          ▼
   ┌──────────────────────────────────────────────┐
   │           AUTHENTICATED SESSION              │
   │  - Save JWT & user profile                   │
   │  - Route to corresponding workspace home     │
   └──────────────────────────────────────────────┘
```

#### State Transition Definitions:
- **`step`**: `'EMAIL' | 'OTP'`
- **`email`**: User-entered string
- **`otp`**: 6-digit string
- **`cooldownSeconds`**: Initialized to `60` on OTP send, decremented by 1 every second. "Resend" button is disabled until `cooldownSeconds === 0`.
- **`errorMessage`**: Displays `RateLimitException` or `InvalidOtpException` messages directly to the user.

---

### 4.4 Ticket Management State Migration (`src/features/tickets/TicketContext.jsx`)
Transition all synchronous `localStorage` array mutations to asynchronous REST requests routed via the Gateway.

#### Ticket Service Endpoint Contracts (Proxied via Gateway `/api/v1/tickets/**`):

#### 1. List Tickets
- **Route:** `GET /api/v1/tickets`
- **Headers:** `Authorization: Bearer <token>` (Gateway translates to `X-User-Id`, `X-User-Role`, `X-User-Email`)
- **Query Parameters:**
  - `status`: Optional filter (`Open`, `In progress`, `Waiting on employee`, `Resolved`, `Closed`)
  - `department`: Optional filter (`IT`, `HR`, `Facilities`, `Java`, `Dotnet`, `AI`)
- **Response: 200 OK** Array of ticket objects.

#### 2. Create Ticket
- **Route:** `POST /api/v1/tickets`
- **Headers:** `Authorization: Bearer <token>`
- **Payload Schema:**
  ```json
  {
    "title": "VPN disconnects during video calls",
    "description": "My VPN disconnects every few minutes when I join a video call from home.",
    "department": "IT",
    "category": "Network",
    "priority": "High",
    "summary": "VPN disconnects repeatedly during video calls from home."
  }
  ```
  *(Note: The client-side `classifyTicket.js` runs in the UI to pre-populate and preview the department, category, summary, and priority before sending).*

#### 3. Update Status / Assignee
- **Route:** `PATCH /api/v1/tickets/{ticketId}`
- **Headers:** `Authorization: Bearer <token>`
- **Payload Schema:**
  ```json
  {
    "status": "In progress",
    "assignee": "Jordan Lee"
  }
  ```

#### 4. Add Comment
- **Route:** `POST /api/v1/tickets/{ticketId}/comments`
- **Headers:** `Authorization: Bearer <token>`
- **Payload Schema:**
  ```json
  {
    "message": "Could you share the laptop model and serial number?"
  }
  ```

#### 5. Acknowledge & Close (Employee Confirmation)
- **Route:** `POST /api/v1/tickets/{ticketId}/acknowledge`
- **Headers:** `Authorization: Bearer <token>`
- **Payload Schema:** `{}`
- **Action:** Changes status from `Resolved` to `Closed` with employee acknowledgement timestamp.

---

## 5. Notification Service Contract & In-App Synchronization

### 5.1 Existing Backend Flow (Preserved)
- **Trigger:** Gateway `OtpAuthServiceImpl` sends OTP notification.
- **Protocol:** `POST http://localhost:8083/api/v1/notifications/otp`
- **Execution:** Asynchronous `@Async` email delivery via Gmail SMTP.
- **Persistence:** Logged in `smartdesk_notification_db.notification_logs`.

### 5.2 Proposed In-App Notification Contract (For Bell Icon `NotificationPanel.jsx`)
To feed the top-bar notification bell in the frontend without using local mock state:
1. **Notification Service Query Endpoint:**
   - `GET /api/v1/notifications/user`
   - Reads `X-User-Email` header (injected by API Gateway).
   - Returns recent notifications from `notification_logs` for that email.
2. **Gateway Route Addition (`application.yaml`):**
   ```yaml
   - id: notification-service-user-route
     uri: ${NOTIFICATION_SERVICE_URL:http://localhost:8083}
     predicates:
       - Path=/api/v1/notifications/user/**
   ```

---

## 6. HTTP Status & Error Handling Matrix

| HTTP Status | Trigger Condition | Frontend UI Reaction |
| :--- | :--- | :--- |
| **200 OK** | Successful OTP verification or Ticket query | Renders data / navigates to workspace dashboard |
| **202 Accepted** | Async OTP dispatch accepted | Displays "Verification code sent to your email" |
| **400 Bad Request** | Malformed email or non-6-digit OTP | Highlights input field with inline validation text |
| **401 Unauthorized** | Missing/expired JWT, or incorrect OTP entered | Displays remaining attempt count; redirects to login if token expired |
| **404 Not Found** | Ticket ID does not exist or user lacks access | Renders `<EmptyState>` or "Ticket not found" banner |
| **429 Too Many Requests** | OTP requested within 60-second cooldown | Disables "Send" button; displays countdown timer |
| **500 Server Error** | Downstream service timeout or database outage | Displays Sonner toast error: "System temporarily unavailable" |

---

## 7. Migration Implementation Roadmap (Non-Destructive)

1. **Step 1: Gateway Configuration**
   - Enable CORS in `api-gateway` for `http://localhost:5173`.
2. **Step 2: Frontend API Client Setup**
   - Update `smartdesk-frontend-master/src/services/api.js` to point to `http://localhost:8080`.
   - Add Bearer token request interceptor and 401 response interceptor.
3. **Step 3: Frontend Authentication Transition**
   - Update `authService.js` and `AuthFormPage.jsx` to execute the 2-step OTP request/verify flow against `/api/v1/auth/otp/*`.
   - Map `ROLE_*` authority strings to frontend workspace keys.
4. **Step 4: Ticket Service Wiring**
   - Wire `TicketContext.jsx` functions to call `/api/v1/tickets/**` through the API Gateway.
   - Verify that the Gateway's `JwtAuthenticationFilter` correctly propagates `X-User-Id`, `X-User-Email`, and `X-User-Role` to the downstream service.
