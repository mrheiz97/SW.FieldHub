BEGIN;
INSERT INTO authz.permissions(key,description) VALUES
('organizations.read','Read organization'),('organizations.settings.update','Update organization settings'),
('members.read','Read memberships'),('members.invite','Invite members'),('members.roles.assign','Assign allowed roles'),
('teams.read','Read teams'),('teams.manage','Manage teams'),('modules.manage','Configure modules'),
('recruiting.contacts.create','Create contact'),('recruiting.contacts.read','Read contact'),('recruiting.contacts.update','Update contact'),
('recruiting.leads.create','Create lead'),('recruiting.leads.read','Read lead'),('recruiting.leads.update','Update lead'),('recruiting.leads.assign','Assign lead'),
('sales.visits.create','Record visit'),('sales.deals.manage','Manage deals'),('analytics.reports.read','Read reports'),('audit.events.read','Read audit events') ON CONFLICT DO NOTHING;
INSERT INTO core.modules(key,name) VALUES ('recruiting','Recruiting'),('sales','Sales'),('field_operations','Field Operations'),('analytics','Analytics') ON CONFLICT DO NOTHING;
COMMIT;
-- Seed per-organization roles transactionally during approval using role templates in docs/IMPLEMENTATION.md.
