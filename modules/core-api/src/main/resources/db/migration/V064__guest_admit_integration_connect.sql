-- Wave 5 closure: guest waiting-room admit + user marketplace connect pins

ALTER TABLE conference_guest_links
    ADD COLUMN IF NOT EXISTS admitted_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_conf_guest_waiting
    ON conference_guest_links (conference_id, waiting_room, admitted_at);

CREATE TABLE IF NOT EXISTS user_integration_connections (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plugin_instance_id UUID NOT NULL,
    connected_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, plugin_instance_id)
);

CREATE INDEX IF NOT EXISTS idx_user_integration_conn_user
    ON user_integration_connections (user_id, connected_at DESC);
