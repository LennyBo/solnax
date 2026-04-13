
alter table solnax.public.power_log drop column power_wall;

alter table solnax.public.power_log add column kitchen integer;

alter table solnax.public.power_log rename column heat_out TO heater;
alter table solnax.public.power_log rename column charger_out TO charger;
alter table solnax.public.power_log rename column solar_in TO solar;
alter table solnax.public.power_log rename column house_out TO house;
