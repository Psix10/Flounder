ALTER TABLE organization_registration_requests
    ADD COLUMN organization_id uuid REFERENCES organizations(id);

CREATE UNIQUE INDEX uq_org_registration_created_organization
    ON organization_registration_requests (organization_id)
    WHERE organization_id IS NOT NULL;

COMMENT ON COLUMN organization_registration_requests.organization_id IS
    'Organization created by approval. NULL for pending, rejected and legacy requests without a recorded link.';