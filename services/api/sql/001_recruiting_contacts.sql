-- Apply once to the FieldHub PostgreSQL database before deploying the recruiting API.
CREATE TABLE IF NOT EXISTS recruiting_contacts (
    id uuid PRIMARY KEY,
    workspace_id varchar(64) NOT NULL REFERENCES workspaces(id),
    owner_id uuid NOT NULL REFERENCES users(id),
    name varchar(160) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'new',
    notes text NOT NULL DEFAULT '',
    follow_up_at timestamptz NULL
);
CREATE INDEX IF NOT EXISTS ix_recruiting_contacts_workspace_id ON recruiting_contacts(workspace_id);
CREATE INDEX IF NOT EXISTS ix_recruiting_contacts_owner_id ON recruiting_contacts(owner_id);
