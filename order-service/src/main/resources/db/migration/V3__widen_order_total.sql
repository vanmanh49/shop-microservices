-- 50 lines of up to 10,000 units at up to 10 integer digits each overflowed numeric(14, 2).
alter table orders alter column total set data type numeric(19, 2);
