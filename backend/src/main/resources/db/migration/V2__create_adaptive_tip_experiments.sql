CREATE TABLE adaptive_tip_experiments (
                                          id UUID NOT NULL,
                                          confidence_tier VARCHAR(255),
                                          created_at TIMESTAMP(6),
                                          evaluated_at TIMESTAMP(6),
                                          experiment_group VARCHAR(255),
                                          momentum DOUBLE PRECISION NOT NULL,
                                          previous_floor DOUBLE PRECISION NOT NULL,
                                          revenue_after24h DOUBLE PRECISION,
                                          revenue_after7d DOUBLE PRECISION,
                                          risk_score INTEGER NOT NULL,
                                          success BOOLEAN,
                                          suggested_floor DOUBLE PRECISION NOT NULL,
                                          creator_id BIGINT NOT NULL,
                                          baseline_revenue DOUBLE PRECISION,
                                          new_risk_score INTEGER,
                                          revenue_lift DOUBLE PRECISION,
                                          risk_delta DOUBLE PRECISION,
                                          CONSTRAINT adaptive_tip_experiments_pkey PRIMARY KEY (id),
                                          CONSTRAINT fk_adaptive_tip_experiments_creator
                                              FOREIGN KEY (creator_id)
                                                  REFERENCES users(id)
);