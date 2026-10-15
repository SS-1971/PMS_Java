-- Sample KPIs for the seven KRAs of organisation 6ac478152175d02217c58ee1.
-- Safe to re-run: a KPI is only inserted when its KRA does not already have one of that name.
-- Units are the ones the KPI screens offer: TPD, kcal/kg, %, kWh/t, Count, Months, MPa.

insert into pms.kpi_master
    (id, organisation_id, kra_id, name, unit, target_type, expected_outcome, evidence_required,
     status, is_active, created_date, modified_date, created_by, modified_by)
select gen_random_uuid(), k.organisation_id, k.id, v.kpi, v.unit, v.target_type, v.outcome, v.evidence,
       'ACTIVE', true, now(), now(), 'seed', 'seed'
  from (values
    ('Production Efficiency',     'Clinker Production',                'TPD',     'COMMON',     'Meet the planned daily clinker output',                 'DCS report'),
    ('Production Efficiency',     'Kiln Run Factor',                   '%',       'COMMON',     'Keep the kiln running with minimal stoppages',          'Stoppage log'),
    ('Production Efficiency',     'Capacity Utilisation',              '%',       'COMMON',     'Use installed capacity as planned',                     'Production MIS'),
    ('Quality Control',           'Cement Strength Consistency',       'MPa',     'COMMON',     'Hold 28-day strength within the specified band',        'Lab test report'),
    ('Quality Control',           'Free Lime Compliance',              '%',       'COMMON',     'Keep clinker free lime within limits',                  'Lab test report'),
    ('Quality Control',           'Customer Complaints',               'Count',   'COMMON',     'Reduce quality-related customer complaints',            'Complaint register'),
    ('Equipment Reliability',     'Plant Availability',                '%',       'COMMON',     'Maximise equipment availability',                       'Maintenance report'),
    ('Equipment Reliability',     'Unplanned Breakdowns',              'Count',   'INDIVIDUAL', 'Reduce unplanned equipment breakdowns',                 'Breakdown log'),
    ('Equipment Reliability',     'Refractory Life',                   'Months',  'COMMON',     'Extend kiln refractory life',                           'Refractory log'),
    ('Safety & Compliance',       'Lost Time Injuries',                'Count',   'COMMON',     'Zero lost-time injuries',                               'Safety MIS'),
    ('Safety & Compliance',       'Safety Audit Compliance',           '%',       'COMMON',     'Close safety audit observations on time',               'Audit report'),
    ('Safety & Compliance',       'Near-Miss Reporting',               'Count',   'INDIVIDUAL', 'Report and close near-miss incidents',                  'Near-miss register'),
    ('Energy Efficiency',         'Specific Heat Consumption',         'kcal/kg', 'COMMON',     'Bring thermal energy use within the target',            'Energy audit'),
    ('Energy Efficiency',         'Power Consumption',                 'kWh/t',   'COMMON',     'Reduce electrical energy per tonne of cement',          'SCADA'),
    ('Energy Efficiency',         'Alternate Fuel Substitution',       '%',       'COMMON',     'Increase the share of alternate fuels',                 'Fuel consumption report'),
    ('Cost Optimization',         'Cost Variance vs Budget',           '%',       'COMMON',     'Keep conversion cost within budget',                    'Finance MIS'),
    ('Cost Optimization',         'Spares Consumption Reduction',      '%',       'INDIVIDUAL', 'Reduce spares consumption against last year',           'Stores report'),
    ('Cost Optimization',         'Fuel Cost Savings',                 '%',       'COMMON',     'Lower fuel cost through sourcing and efficiency',       'Purchase report'),
    ('Environmental Compliance',  'Emission Limit Compliance',         '%',       'COMMON',     'Stay within stack emission limits',                     'CEMS report'),
    ('Environmental Compliance',  'Dust Emission Incidents',           'Count',   'COMMON',     'No dust emission limit breaches',                       'Pollution board report'),
    ('Environmental Compliance',  'Waste Heat Recovery Utilisation',   '%',       'COMMON',     'Utilise the waste heat recovery plant fully',           'WHR log')
  ) as v(kra, kpi, unit, target_type, outcome, evidence)
  join pms.kra_master k
    on k.organisation_id = '6ac478152175d02217c58ee1'
   and k.name = v.kra
   and k.is_active = true
 where not exists (
        select 1 from pms.kpi_master p
         where p.kra_id = k.id and lower(p.name) = lower(v.kpi) and p.is_active = true);
