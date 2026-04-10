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

Ensure the following ports are open on your VPS firewall (e.g., UFW, IPTable) AND your Cloud Provider's Security Group (Hetzner, AWS, etc.):

- `80/tcp` (HTTP - redirected to HTTPS)
- `443/tcp` (HTTPS)
- `22/tcp` (SSH)
- `3478/udp` & `3478/tcp` (Coturn TURN/STUN)
- `40000-40300/udp` (Mediasoup WebRTC Media)
- `49152-49200/udp` (Coturn Relay Ports)

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

# Mediasoup / WebRTC
MEDIASOUP_ANNOUNCED_IP=your_public_vps_ip
MEDIASOUP_AUTH_TOKEN=your_random_secret_here

# TURN Server (Optional but HIGHLY recommended for Firefox/Mobile)
# If left empty, docker-compose will use MEDIASOUP_ANNOUNCED_IP and default credentials.
TURN_SERVER_URL=turn:your-domain.com:3478
TURN_USERNAME=turnuser
TURN_CREDENTIAL=your_turn_password
TURN_EXTERNAL_IP=your_public_vps_ip

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
