-- One row per order reference. Reserve and release both write it first, so the database
-- makes the second wait for the first, even when a release arrives mid-reservation.
create table reservation_orders (
    order_ref varchar(64) primary key,
    released  boolean     not null
);

-- Reservations made before this table existed.
insert into reservation_orders (order_ref, released)
select distinct order_ref, false from reservations;
