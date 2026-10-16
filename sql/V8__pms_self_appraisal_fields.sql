-- Self appraisal (screen 4.6): the employee own actual, achievement text and competency comment.
-- ddl-auto=update adds these nullable columns on startup; run by hand on databases that need it.
alter table pms.appraisal_line add column if not exists actual numeric(14,2);
alter table pms.appraisal_line add column if not exists evidence varchar(500);
alter table pms.appraisal_line add column if not exists self_comment varchar(500);
