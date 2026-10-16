ALTER TABLE ranking_rewards ADD COLUMN stock INT NOT NULL DEFAULT 0;
ALTER TABLE ranking_rewards ADD CONSTRAINT chk_ranking_reward_stock CHECK (stock >= 0 AND stock <= 999999999);
