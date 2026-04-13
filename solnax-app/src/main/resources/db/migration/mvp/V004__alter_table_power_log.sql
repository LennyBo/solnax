
alter table power_log drop column power_wall;

alter table power_log add column kitchen integer;

alter table power_log rename column heat_out TO heater;
alter table power_log rename column charger_out TO charger;
alter table power_log rename column solar_in TO solar;
alter table power_log rename column house_out TO house;
