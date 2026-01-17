# Livora Backend

## Environment Variables

The following environment variables are required for the application to function correctly, especially for authentication and payment processing.

### Security
| Variable | Description | Default (Dev) |
| :--- | :--- | :--- |
| `SECURITY_JWT_SECRET` | Secret key for signing JWT tokens (min 256-bit). | `JOINLIVORA_ULTRA_SECURE_SECRET_2026_123456789` |

### Stripe Payment Integration
| Variable | Description | Required |
| :--- | :--- | :--- |
| `STRIPE_SECRET_KEY` | Your Stripe Secret API Key (starts with `sk_`). | Yes |
| `STRIPE_WEBHOOK_SECRET` | Secret used to verify Stripe webhook signatures (starts with `whsec_`). | Yes |
| `STRIPE_PREMIUM_PLAN_ID` | The Stripe Price ID for the premium subscription. | Yes |
| `FRONTEND_URL` | Base URL of the frontend application for redirection. | No (defaults to `http://localhost:3000`) |

### Frontend Redirects (Stripe)
These variables define where Stripe redirects the user after the checkout process.
- `FRONTEND_SUCCESS_URL`: Default is `${FRONTEND_URL}/payment/success`
- `FRONTEND_CANCEL_URL`: Default is `${FRONTEND_URL}/payment/cancel`

## Setup
1. Configure a PostgreSQL database named `joinlivora`.
2. Set the required environment variables.
3. Run the application using `./mvnw spring-boot:run`.
