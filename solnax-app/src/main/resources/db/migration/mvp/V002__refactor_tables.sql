alter table power_log alter column solar_in type integer using solar_in::integer;
alter table power_log alter column house_out type integer using house_out::integer;
alter table power_log alter column heat_out type integer using heat_out::integer;
alter table power_log alter column charger_out type integer using charger_out::integer;
alter table power_log alter column power_wall type integer using power_wall::integer;

DROP table tesla_auth;