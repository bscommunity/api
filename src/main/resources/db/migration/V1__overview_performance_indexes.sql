-- Composite indexes for /me/overview query performance
-- These cover the most frequent WHERE/GROUP BY patterns used by OverviewRepository

-- getPublishedCounts, getPublishedTrend: WHERE author_id = ? AND status = ? [AND created_at >= ?]
CREATE INDEX catalog_items_author_status_created ON catalog_items (author_id, status, created_at);

-- getVersionUpdateCounts: JOIN on catalog_item_id, filter by created_at
CREATE INDEX catalog_item_versions_item_created ON catalog_item_versions (catalog_item_id, created_at);

-- getContributedCounts: WHERE user_id = ? (then join on catalog_item_id)
CREATE INDEX contributors_user_catalog ON contributors (user_id, catalog_item_id);

-- getTotalLikes, getTotalBookmarks: WHERE user_id = ? AND kind = ?
CREATE INDEX collections_user_kind ON collections (user_id, kind);
