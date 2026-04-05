ALTER TABLE tour_pass_charts ADD COLUMN position INT;

WITH ranked AS (
    SELECT
        tour_pass_id,
        chart_id,
        ROW_NUMBER() OVER (PARTITION BY tour_pass_id ORDER BY chart_id) - 1 AS pos
    FROM tour_pass_charts
)
UPDATE tour_pass_charts tpc
SET position = ranked.pos
FROM ranked
WHERE tpc.tour_pass_id = ranked.tour_pass_id
  AND tpc.chart_id = ranked.chart_id;

ALTER TABLE tour_pass_charts ALTER COLUMN position SET NOT NULL;
ALTER TABLE tour_pass_charts ALTER COLUMN position SET DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_tour_pass_charts_tour_pass_id_position
    ON tour_pass_charts (tour_pass_id, position);

ALTER TABLE tour_pass_charts
    ADD CONSTRAINT uq_tour_pass_charts_tour_pass_position UNIQUE (tour_pass_id, position);

