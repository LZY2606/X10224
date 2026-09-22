-- v1 -> v2：判定依据列与历史快照表
ALTER TABLE events ADD COLUMN judge_code TEXT;
CREATE TABLE snapshots (
  event_seq INTEGER NOT NULL,
  branch TEXT NOT NULL,
  label TEXT,
  json TEXT NOT NULL,
  PRIMARY KEY (event_seq, branch)
);
