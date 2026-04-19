package com.joinlivora.backend.payout;

public enum EarningSource {
    SUBSCRIPTION,
    TIP,
    PPV,
    HIGHLIGHTED_CHAT,
    CHAT,
    PRIVATE_SHOW,
    CHARGEBACK,
    ORPHAN_REVERSAL,  // Reversal received for a Stripe charge with no matching earning row
    ADJUSTMENT        // Manual or system balance adjustment (hold release, admin correction)
}
