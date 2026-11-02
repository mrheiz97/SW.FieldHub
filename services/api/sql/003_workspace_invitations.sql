-- FieldHub v0.24: one-time, expiring workspace invitation tokens.
-- Back up database before running. Apply once in a transaction.
CREATE TABLE workspace_invitations (
 id uuid PRIMARY KEY,
 workspace_id varchar(64) NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 token_hash char(64) NOT NULL UNIQUE,
 permissions jsonb NOT NULL,
 created_by uuid NOT NULL REFERENCES users(id),
 created_at timestamptz NOT NULL DEFAULT now(),
 expires_at timestamptz NOT NULL,
 claimed_by uuid REFERENCES users(id),
 claimed_at timestamptz,
 revoked_at timestamptz,
 CONSTRAINT invitation_claim_consistent CHECK ((claimed_by IS NULL) = (claimed_at IS NULL))
);
CREATE INDEX ix_workspace_invitations_workspace ON workspace_invitations(workspace_id);
