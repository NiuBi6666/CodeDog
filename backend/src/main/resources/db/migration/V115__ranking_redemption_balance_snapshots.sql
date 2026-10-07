ALTER TABLE ranking_reward_redemptions
  ADD COLUMN balance_before INT NULL AFTER points_spent,
  ADD COLUMN balance_after INT NULL AFTER balance_before;

UPDATE ranking_reward_redemptions target
JOIN (
  SELECT materialized.id,
         GREATEST(materialized.earned_points - materialized.spent_before, materialized.points_spent) AS balance_before
  FROM (
    SELECT redemption.id,
           redemption.points_spent,
           COALESCE(earned.earned_points, 0) AS earned_points,
           COALESCE(SUM(redemption.points_spent) OVER (
             PARTITION BY redemption.owner_username, redemption.student_id
             ORDER BY redemption.redeemed_at, redemption.id
             ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
           ), 0) AS spent_before
    FROM ranking_reward_redemptions redemption
    LEFT JOIN (
      SELECT owner_username, student_id, SUM(total_points) AS earned_points
      FROM ranking_lesson_results
      GROUP BY owner_username, student_id
    ) earned
      ON earned.owner_username = redemption.owner_username
     AND earned.student_id = redemption.student_id
  ) materialized
) snapshot ON snapshot.id = target.id
SET target.balance_before = snapshot.balance_before,
    target.balance_after = GREATEST(snapshot.balance_before - target.points_spent, 0);

ALTER TABLE ranking_reward_redemptions
  MODIFY COLUMN balance_before INT NOT NULL,
  MODIFY COLUMN balance_after INT NOT NULL,
  ADD CONSTRAINT chk_ranking_redemptions_balance_before CHECK (balance_before >= points_spent),
  ADD CONSTRAINT chk_ranking_redemptions_balance_after CHECK (balance_after = balance_before - points_spent);
