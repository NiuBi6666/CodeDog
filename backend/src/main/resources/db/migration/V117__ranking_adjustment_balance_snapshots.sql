ALTER TABLE ranking_point_adjustments
  ADD COLUMN balance_before INT NULL AFTER points,
  ADD COLUMN balance_after INT NULL AFTER balance_before;
