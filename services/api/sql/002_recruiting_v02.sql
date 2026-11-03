-- Recruiting v0.2: additive migration; existing contacts remain unchanged.
ALTER TABLE recruiting_contacts ADD COLUMN IF NOT EXISTS first_name varchar(80) NOT NULL DEFAULT '';
ALTER TABLE recruiting_contacts ADD COLUMN IF NOT EXISTS last_name varchar(80) NOT NULL DEFAULT '';
ALTER TABLE recruiting_contacts ADD COLUMN IF NOT EXISTS phone varchar(80);
ALTER TABLE recruiting_contacts ADD COLUMN IF NOT EXISTS email varchar(254);
-- Preserve the original full name of contacts created before this migration.
