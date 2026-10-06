-- Optimistic locking: see the version field of Order.
alter table orders add column version bigint not null default 0;
