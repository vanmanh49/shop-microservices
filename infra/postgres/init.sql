-- One database per service: no service reads another service's tables.
CREATE DATABASE authdb;
CREATE DATABASE productdb;
CREATE DATABASE inventorydb;
CREATE DATABASE orderdb;
CREATE DATABASE notificationdb;
