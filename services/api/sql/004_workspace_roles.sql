-- FieldHub v0.25: workspace-scoped custom roles and assignments.
-- Apply once after 003_workspace_invitations.sql; back up the database first.
BEGIN;
ALTER TABLE memberships ADD CONSTRAINT memberships_workspace_user_unique UNIQUE (workspace_id, user_id);
CREATE TABLE workspace_roles (
 id uuid PRIMARY KEY,
 workspace_id varchar(64) NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
 name varchar(100) NOT NULL,
 description varchar(500) NOT NULL DEFAULT '',
 permissions jsonb NOT NULL DEFAULT '[]'::jsonb,
 created_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT workspace_roles_name_unique UNIQUE (workspace_id, name),
 CONSTRAINT workspace_roles_permissions_array CHECK (jsonb_typeof(permissions) = 'array'),
 CONSTRAINT workspace_roles_workspace_id_unique UNIQUE (workspace_id, id)
);
CREATE TABLE workspace_role_assignments (
 workspace_id varchar(64) NOT NULL,
 role_id uuid NOT NULL,
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 PRIMARY KEY (role_id, user_id),
 FOREIGN KEY (workspace_id, role_id) REFERENCES workspace_roles(workspace_id, id) ON DELETE CASCADE,
 FOREIGN KEY (workspace_id, user_id) REFERENCES memberships(workspace_id, user_id) ON DELETE CASCADE
);
CREATE INDEX ix_workspace_role_assignments_user ON workspace_role_assignments(workspace_id, user_id);
-- Preserve existing direct permissions. Role grants are additive.
-- Ensure memberships has a unique (workspace_id,user_id) key for scoped assignments.
COMMIT;
