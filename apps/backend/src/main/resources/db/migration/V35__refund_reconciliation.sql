ALTER TABLE payment_refunds
    ADD CONSTRAINT payment_refunds_positive_amount CHECK (amount > 0);

CREATE UNIQUE INDEX uq_payment_refunds_provider_ref
    ON payment_refunds(provider_ref) WHERE provider_ref IS NOT NULL;

CREATE UNIQUE INDEX uq_payment_refunds_pending_payment
    ON payment_refunds(payment_id) WHERE status = 'PENDING';
