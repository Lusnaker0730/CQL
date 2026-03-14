# Password Reset Feature - Deployment Guide

## Overview

This document covers the step-by-step deployment of the **Password Reset** feature, which includes:

- **Self-service email reset**: Users request a reset link via email
- **Admin manual reset**: Admins generate temporary passwords for users
- **Force password change**: Users with admin-reset passwords must change on next login

---

## Prerequisites

- Java 21, Maven 3.9+
- Node.js 20+, npm
- PostgreSQL 16 (production) or H2 (development)
- SMTP mail server (production) or local dev SMTP (e.g., MailHog/MailPit)
- Existing CQL Platform deployment (commit `f232795` or later)

---

## Step 1: Pull Latest Code

```bash
git pull origin appmod/java-upgrade-20260209020310
```

Verify commit `46a048a` (or later) is present:

```bash
git log --oneline -1
# Expected: 46a048a Add password reset feature with self-service email flow and admin reset
```

---

## Step 2: Configure Environment Variables

### 2.1 Production (Docker / Environment)

Add these **new** environment variables to your `.env` file or deployment configuration:

```env
# --- SMTP Mail Server (REQUIRED for email reset) ---
MAIL_HOST=smtp.your-provider.com
MAIL_PORT=587
MAIL_USERNAME=noreply@your-domain.com
MAIL_PASSWORD=your-smtp-password
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
```

> **Note**: If using Gmail SMTP, use an App Password (not your account password).
> If using AWS SES, configure the IAM credentials accordingly.

### 2.2 Docker Compose

Add the mail variables to the `backend` service in `docker/docker-compose.yml`:

```yaml
backend:
  environment:
    # ... existing variables ...
    - MAIL_HOST=${MAIL_HOST}
    - MAIL_PORT=${MAIL_PORT}
    - MAIL_USERNAME=${MAIL_USERNAME}
    - MAIL_PASSWORD=${MAIL_PASSWORD}
    - MAIL_SMTP_AUTH=${MAIL_SMTP_AUTH:-true}
    - MAIL_SMTP_STARTTLS=${MAIL_SMTP_STARTTLS:-true}
```

And add to your `docker/.env`:

```env
MAIL_HOST=smtp.your-provider.com
MAIL_PORT=587
MAIL_USERNAME=noreply@your-domain.com
MAIL_PASSWORD=your-smtp-password
```

### 2.3 Development (Local)

No configuration needed. Dev profile defaults to `localhost:1025` with auth disabled.
For local email testing, use [MailPit](https://github.com/axllent/mailpit):

```bash
# Run MailPit (captures all outgoing emails)
docker run -d -p 1025:1025 -p 8025:8025 axllent/mailpit

# Web UI: http://localhost:8025
# SMTP: localhost:1025
```

---

## Step 3: Database Migration

### 3.1 Production (Flyway - Automatic)

The migration `V16__password_reset.sql` runs automatically on startup when Flyway is enabled.

**What it does:**
1. Adds `email_hash VARCHAR(64)` column + index to `app_user`
2. Adds `force_password_change BOOLEAN DEFAULT FALSE` to `app_user`
3. Creates `password_reset_token` table with indexes

**To verify migration ran:**

```sql
SELECT * FROM flyway_schema_history WHERE version = '16';
```

### 3.2 Development (H2 - Automatic)

H2 dev profile uses `ddl-auto: update`, so Hibernate creates the columns automatically.
The `EmailHashMigration` CommandLineRunner backfills `email_hash` for existing users on startup.

### 3.3 Manual Migration (if needed)

If Flyway is disabled or you need to run manually:

```sql
-- Run against your PostgreSQL database
ALTER TABLE app_user ADD COLUMN email_hash VARCHAR(64);
CREATE INDEX idx_app_user_email_hash ON app_user(email_hash);

ALTER TABLE app_user ADD COLUMN force_password_change BOOLEAN DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS password_reset_token (
    id          BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    token_hash  VARCHAR(64)     NOT NULL,
    expires_at  TIMESTAMP       NOT NULL,
    used        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reset_token_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_reset_token_hash ON password_reset_token(token_hash);
CREATE INDEX idx_reset_token_user ON password_reset_token(user_id);
```

---

## Step 4: Build & Deploy Backend

### 4.1 Local Build

```bash
cd backend
mvn clean package -DskipTests -q
```

The new dependency `spring-boot-starter-mail` will be downloaded automatically.

### 4.2 Docker Build

```bash
cd docker
docker compose build backend
```

### 4.3 Verify Backend Startup

Check application logs for:

```
Backfilled email_hash for N existing users    # EmailHashMigration ran
```

Verify new endpoints are accessible:

```bash
# Health check
curl http://localhost:8080/actuator/health

# Forgot password (public - should return 200)
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com"}'

# Admin users list (requires ADMIN JWT)
curl http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

---

## Step 5: Build & Deploy Frontend

### 5.1 Local Build

```bash
cd frontend
npm install    # if node_modules missing
npm run build
```

### 5.2 Docker Build

```bash
cd docker
docker compose build frontend
```

### 5.3 Verify New Routes

After deployment, verify these routes are accessible:

| Route | Access | Description |
|-------|--------|-------------|
| `/forgot-password` | Public | Email input for password reset |
| `/reset-password?token=xxx` | Public | New password form |
| `/admin/users` | ADMIN only | User management table |

---

## Step 6: Full Docker Compose Deployment

```bash
cd docker

# Update .env with mail variables (see Step 2)

# Rebuild and restart
docker compose down
docker compose build
docker compose up -d

# Watch logs
docker compose logs -f backend
```

---

## Step 7: Verification Tests

### 7.1 Self-Service Email Reset Flow

1. Navigate to `/login`
2. Click **"Forgot your password?"** link
3. Enter a registered user's email address
4. Click **"Send Reset Link"**
5. Verify success message appears (regardless of email existence)
6. Check email inbox (or MailPit at `http://localhost:8025` for dev)
7. Click the reset link in the email
8. Enter and confirm new password
9. Verify redirect to login page
10. Login with the new password

### 7.2 Admin Password Reset Flow

1. Login as `admin` (or any ADMIN role user)
2. Navigate to `/admin/users` (or click **"Users"** in the navigation bar)
3. Find the target user in the table
4. Click **"Reset Password"** button
5. Copy the temporary password from the dialog
6. Logout and login as the target user with the temporary password
7. Verify the **Force Password Change** dialog appears
8. Enter the temporary password as current, set a new password
9. Verify the dialog closes and the application is usable

### 7.3 Security Tests

| Test | Expected Result |
|------|----------------|
| Forgot password with non-existent email | Same success message (no enumeration) |
| Reset with invalid token | Error: "Invalid or expired reset token" |
| Reset with expired token (>30 min) | Error: "Invalid or expired reset token" |
| Reset with already-used token | Error: "Invalid or expired reset token" |
| Access `/api/admin/users` as USER role | 403 Forbidden |
| Access `/api/admin/users` unauthenticated | 401 Unauthorized |
| Force change dialog dismiss attempt | Cannot close (no X button, no backdrop dismiss) |

---

## New API Endpoints Reference

### Public Endpoints (no auth required)

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/api/auth/forgot-password` | `{ "email": "..." }` | Request reset email |
| POST | `/api/auth/reset-password` | `{ "token": "...", "newPassword": "..." }` | Reset with token |

### Authenticated Endpoints

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/api/auth/change-password` | `{ "currentPassword": "...", "newPassword": "..." }` | Change own password |

### Admin Endpoints (ADMIN role required)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/users` | List all users |
| POST | `/api/admin/users/{userId}/reset-password` | Admin reset, returns temp password |

---

## Rollback Plan

If issues arise, rollback to the previous version:

### 1. Code Rollback

```bash
git revert 46a048a
```

### 2. Database Rollback

```sql
-- Remove new columns
ALTER TABLE app_user DROP COLUMN IF EXISTS email_hash;
ALTER TABLE app_user DROP COLUMN IF EXISTS force_password_change;

-- Drop new table
DROP TABLE IF EXISTS password_reset_token;

-- Remove Flyway history entry
DELETE FROM flyway_schema_history WHERE version = '16';
```

### 3. Redeploy

```bash
cd docker
docker compose build
docker compose up -d
```

---

## Troubleshooting

### Email not sending

1. Check SMTP credentials in environment variables
2. Verify network connectivity to SMTP server from the backend container:
   ```bash
   docker compose exec backend nc -zv $MAIL_HOST $MAIL_PORT
   ```
3. Check backend logs for `Failed to send password reset email` errors
4. For Gmail: ensure "Less secure app access" or App Passwords are configured
5. For corporate SMTP: check firewall rules for port 587/465

### email_hash not populated

The `EmailHashMigration` runs once at startup. If it didn't run:

1. Check logs for `Backfilled email_hash for N existing users`
2. Manually trigger by restarting the backend service
3. Or run SQL directly:
   ```sql
   -- Note: This requires application-level code since email is encrypted.
   -- Restart the backend service to trigger EmailHashMigration automatically.
   ```

### Force password change dialog not appearing

1. Clear browser localStorage: `localStorage.clear()`
2. Re-login to get the updated `forcePasswordChange` flag
3. Check `/api/auth/me` response includes `forcePasswordChange: true`

### Admin "Users" tab not visible

- Only visible to users with `ADMIN` role
- Verify user role: check `/api/auth/me` response for `"role": "ADMIN"`
