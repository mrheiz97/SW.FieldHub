-- Initial schema for a NEW development database only.
-- Subsequent schema changes require versioned migrations, not edits to this file.
CREATE TABLE workspaces (
    id varchar(64) PRIMARY KEY,
    name varchar(160) NOT NULL
);
CREATE TABLE memberships (
    id serial PRIMARY KEY,
    workspace_id varchar(64) NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    subject varchar(255) NOT NULL,
    permissions json NOT NULL DEFAULT '[]',
    CONSTRAINT uq_membership UNIQUE (workspace_id, subject)
);
CREATE INDEX ix_memberships_workspace_id ON memberships(workspace_id);
CREATE INDEX ix_memberships_subject ON memberships(subject);
CREATE TABLE forms (
    id serial PRIMARY KEY,
    workspace_id varchar(64) NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    form_id varchar(64) NOT NULL,
    version integer NOT NULL,
    title varchar(160) NOT NULL,
    schema json NOT NULL,
    published boolean NOT NULL DEFAULT false,
    CONSTRAINT uq_form_version UNIQUE (workspace_id, form_id, version)
);
CREATE INDEX ix_forms_workspace_id ON forms(workspace_id);
CREATE TABLE submissions (
    id varchar(64) PRIMARY KEY,
    workspace_id varchar(64) NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    form_id varchar(64) NOT NULL,
    form_version integer NOT NULL,
    values json NOT NULL,
    revision integer NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_submission_workspace_form UNIQUE (workspace_id, form_id, id)
);
CREATE INDEX ix_submissions_workspace_id ON submissions(workspace_id);
