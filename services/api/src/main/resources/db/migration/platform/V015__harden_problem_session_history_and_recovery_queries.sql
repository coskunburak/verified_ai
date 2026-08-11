CREATE INDEX ix_problem_sessions_user_updated_id
    ON problem_sessions(user_id, updated_at DESC, id DESC);
