CREATE TABLE IF NOT EXISTS tour_pass_streaming_links (
    tour_pass_id BIGINT NOT NULL,
    streaming_link_id UUID NOT NULL,
    PRIMARY KEY (tour_pass_id, streaming_link_id),
    CONSTRAINT fk_tour_pass_streaming_links_tour_pass
        FOREIGN KEY (tour_pass_id)
        REFERENCES tour_passes(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_tour_pass_streaming_links_streaming_link
        FOREIGN KEY (streaming_link_id)
        REFERENCES streaming_links(id)
        ON DELETE CASCADE
);

ALTER TABLE tour_passes DROP COLUMN IF EXISTS playlist_urls;


