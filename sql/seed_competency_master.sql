-- Sample competencies for organisation 6ac478152175d02217c58ee1: the 14 standard behavioural ones
-- (4 rated by HR, 10 by the manager) and four others. Safe to re-run: skipped when the name already exists.

insert into pms.competency_master
    (id, organisation_id, name, category, competency_type, status, is_active,
     created_date, modified_date, created_by, modified_by)
select gen_random_uuid(), '6ac478152175d02217c58ee1', v.name, v.category, v.ctype,
       'ACTIVE', true, now(), now(), 'seed', 'seed'
  from (values
    ('Attendance and Punctuality',                'Behavioural', 'HR'),
    ('Discipline',                                'Behavioural', 'HR'),
    ('Attitude towards work',                     'Behavioural', 'MANAGER'),
    ('Attitude towards people',                   'Behavioural', 'MANAGER'),
    ('Dependability',                             'Behavioural', 'MANAGER'),
    ('Listening and communication skills',        'Behavioural', 'MANAGER'),
    ('Honesty and Integrity',                     'Behavioural', 'HR'),
    ('Co-employee relations at work (Teamwork)',  'Behavioural', 'HR'),
    ('Takes initiative at work',                  'Behavioural', 'MANAGER'),
    ('Commitment at work',                        'Behavioural', 'MANAGER'),
    ('Flexibility at work',                       'Behavioural', 'MANAGER'),
    ('Cost consciousness',                        'Behavioural', 'MANAGER'),
    ('Consistency at work',                       'Behavioural', 'MANAGER'),
    ('Enthusiasm & Creativity at work',           'Behavioural', 'MANAGER'),
    ('Technical Knowledge',                       'Technical',   'MANAGER'),
    ('Problem Solving',                           'Technical',   'MANAGER'),
    ('Team Leadership',                           'Leadership',  'MANAGER'),
    ('Planning and Organising',                   'Functional',  'MANAGER')
  ) as v(name, category, ctype)
 where not exists (
        select 1 from pms.competency_master c
         where c.organisation_id = '6ac478152175d02217c58ee1'
           and lower(c.name) = lower(v.name) and c.is_active = true);
