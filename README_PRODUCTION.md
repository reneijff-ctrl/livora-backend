# Livora Production Deployment Guide

This guide covers the deployment of the Livora backend to a production VPS using Docker and Nginx.

## Prerequisites

- VPS (Ubuntu 22.04+ recommended)
- Docker and Docker Compose installed
- Domain name (e.g., `api.joinlivora.com`)
- Stripe account (Live keys)
- SSL Certificate (handled via Certbot in this guide)

## VPS Requirements

- Minimum 2GB RAM (4GB recommended for smooth operation)
- 2 vCPUs
- 20GB SSD

## Firewall Rules

Ensure the following ports are open:
- `80/tcp` (HTTP - redirected to HTTPS)
- `443/tcp` (HTTPS)
- `22/tcp` (SSH)
- `40000-49999/udp` (Mediasoup WebRTC Media)
- `40000-49999/tcp` (Mediasoup WebRTC Media Fallback)
- `4000/tcp` (Mediasoup Signaling API - if not proxied)

## Project Structure on VPS

```text
/home/user/livora/
├── backend/
│   └── Dockerfile
├── nginx/
│   └── conf.d/
│       └── default.conf
├── docker-compose.yml
└── .env
```

## Environment Variables Checklist

Create a `.env` file in the project root on your VPS:

```bash
# General
SPRING_PROFILES_ACTIVE=prod
FRONTEND_URL=https://joinlivora.com

# Database
DB_PASSWORD=your_secure_db_password
DB_URL=jdbc:postgresql://postgres:5432/joinlivora

# Security
JWT_SECRET=your_long_random_jwt_secret_at_least_32_chars

# Stripe (Live Keys)
STRIPE_SECRET_KEY=sk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PREMIUM_PLAN_ID=price_...

# Nginx / SSL
DOMAIN=api.joinlivora.com
```

## Deployment Steps

1. **Clone the repository** (or copy the files) to the VPS.
2. **Configure Nginx**:
   Edit `nginx/conf.d/default.conf` and ensure the `server_name` matches your domain.
3. **Initial SSL Setup**:
   Before running with SSL enabled in Nginx, you might need to comment out the SSL part of the Nginx config to get the initial certificate from Let's Encrypt using the webroot challenge.
   Alternatively, use a script to automate this.
4. **Build and Start**:
   ```bash
   docker-compose up -d --build
   ```
5. **Verify**:
   - Check logs: `docker-compose logs -f backend`
   - Check health: `curl https://api.joinlivora.com/actuator/health`

## Stripe Webhook Setup

1. Go to the Stripe Dashboard -> Developers -> Webhooks.
2. Add an endpoint: `https://api.joinlivora.com/webhooks/stripe`
3. Select events:
   - `checkout.session.completed`
   - `customer.subscription.deleted`
   - `customer.subscription.updated`
   - `invoice.payment_succeeded`
   - `invoice.payment_failed`
4. Copy the Signing Secret to your `.env` file as `STRIPE_WEBHOOK_SECRET`.

## Security Notes

- The backend container runs as a non-root user.
- Database port 5432 is NOT exposed to the public internet (only internal network).
- Nginx provides TLS termination and secure headers.
- Health checks are configured to monitor both the application and the database.
