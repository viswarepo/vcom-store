-- Run once against your own PostgreSQL (as a superuser):  psql -U postgres -f infra/local-postgres-setup.sql
-- Skip the CREATE USER line if you set DB_USER/DB_PASSWORD to an existing user.
CREATE USER shop WITH PASSWORD 'shop';
CREATE DATABASE authdb      OWNER shop;
CREATE DATABASE productdb   OWNER shop;
CREATE DATABASE storedb     OWNER shop;
CREATE DATABASE orderdb     OWNER shop;
CREATE DATABASE inventorydb OWNER shop;
CREATE DATABASE paymentdb   OWNER shop;
