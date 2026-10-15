-- PMS schema (V4): the employee's comment when acknowledging goals is kept.
-- ddl-auto=update adds the column on startup; this is for databases managed by hand.
alter table pms.goal_assignment add column if not exists acknowledge_comment varchar(500);
