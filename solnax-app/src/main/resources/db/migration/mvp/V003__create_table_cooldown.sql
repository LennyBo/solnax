CREATE TABLE charge_point_cool_down_reason(
    time timestamp primary key default current_timestamp,
    target varchar not null,
    ends_at timestamp not null,
    reason varchar not null
)