-- Notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    catalog_item_id VARCHAR(10) NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_notifications_user_id_created_at ON notifications(user_id, created_at DESC);
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_user_id__id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_actor_id__id
    FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_catalog_item_id__id
    FOREIGN KEY (catalog_item_id) REFERENCES catalog_items(id) ON DELETE CASCADE;

-- Per-user contributor invite setting
ALTER TABLE users ADD COLUMN allow_contributor_invites_from VARCHAR(20) DEFAULT 'everyone' NOT NULL;
