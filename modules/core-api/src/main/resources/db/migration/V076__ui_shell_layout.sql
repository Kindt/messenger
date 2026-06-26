-- Spec 028: shell layout variant on branding tables.
ALTER TABLE platform_ui_branding
    ADD COLUMN shell_layout VARCHAR(32) NOT NULL DEFAULT 'default';

ALTER TABLE org_ui_branding
    ADD COLUMN shell_layout VARCHAR(32);
