-- Update Progress (screen 4.4): the employee status per KPI and the moment a period was submitted.
-- ddl-auto=update adds both nullable columns on startup; run by hand on databases that need it.
alter table pms.goal_progress add column if not exists status varchar(20);
alter table pms.goal_progress add column if not exists submitted_on timestamp(6);
