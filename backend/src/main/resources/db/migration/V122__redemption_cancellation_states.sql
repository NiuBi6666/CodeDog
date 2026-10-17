ALTER TABLE ranking_reward_redemptions DROP CHECK chk_ranking_redemptions_status;
ALTER TABLE ranking_reward_redemptions ADD CONSTRAINT chk_ranking_redemptions_status CHECK (status IN ('PENDING','FULFILLED','CANCEL_REQUESTED','CANCELLED'));
