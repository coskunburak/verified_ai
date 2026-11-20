CREATE TABLE billing_offer_catalog (
    offer_code varchar(48) PRIMARY KEY CHECK (offer_code ~ '^[a-z][a-z0-9_]{0,47}$'),
    product_id varchar(255) UNIQUE CHECK (product_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,254}$'),
    family varchar(24) NOT NULL CHECK (family IN ('PRO','COACH_PRO','YKS_SEASON_PASS')),
    billing_period varchar(4) NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    starts_at timestamptz,
    ends_at timestamptz,
    training_eligible boolean NOT NULL DEFAULT false CHECK (NOT training_eligible),
    CHECK (NOT enabled OR product_id IS NOT NULL),
    CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at),
    CHECK ((family = 'YKS_SEASON_PASS' AND billing_period = 'P6M')
        OR (family <> 'YKS_SEASON_PASS' AND billing_period IN ('P1M','P1Y')))
);

INSERT INTO billing_offer_catalog (offer_code, family, billing_period) VALUES
    ('pro_monthly', 'PRO', 'P1M'), ('pro_annual', 'PRO', 'P1Y'),
    ('coach_monthly', 'COACH_PRO', 'P1M'), ('coach_annual', 'COACH_PRO', 'P1Y'),
    ('yks_season', 'YKS_SEASON_PASS', 'P6M');

ALTER TABLE app_store_transactions ADD COLUMN offer_family varchar(24)
    CHECK (offer_family IS NULL OR (offer_family = 'YKS_SEASON_PASS'
        AND purchase_date IS NOT NULL AND expires_date IS NOT NULL AND expires_date > purchase_date));

CREATE FUNCTION billing_preserve_season_term() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.offer_family = 'YKS_SEASON_PASS' AND
       ROW(NEW.offer_family, NEW.product_id, NEW.purchase_date, NEW.expires_date)
       IS DISTINCT FROM ROW(OLD.offer_family, OLD.product_id, OLD.purchase_date, OLD.expires_date) THEN
        RAISE EXCEPTION 'Season transaction identity and validity are immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER billing_season_term_immutable BEFORE UPDATE ON app_store_transactions
    FOR EACH ROW WHEN (OLD.offer_family = 'YKS_SEASON_PASS') EXECUTE FUNCTION billing_preserve_season_term();

CREATE FUNCTION billing_preserve_catalog_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(OLD.offer_code, OLD.family, OLD.billing_period) IS DISTINCT FROM
       ROW(NEW.offer_code, NEW.family, NEW.billing_period)
       OR (OLD.product_id IS NOT NULL AND NEW.product_id IS DISTINCT FROM OLD.product_id) THEN
        RAISE EXCEPTION 'Assigned billing offer identity is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER billing_catalog_identity BEFORE UPDATE ON billing_offer_catalog
    FOR EACH ROW EXECUTE FUNCTION billing_preserve_catalog_identity();
