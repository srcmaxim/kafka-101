CREATE ROLE connector
    WITH LOGIN PASSWORD 'connector'
    NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE DATABASE connector_db
    WITH
    OWNER = connector
    ENCODING = 'UTF-8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
GRANT ALL PRIVILEGES ON DATABASE connector_db TO root;

\connect connector_db connector
CREATE TABLE cities (
    city_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    state VARCHAR(255) NOT NULL
);
INSERT INTO cities (city_id, name, state) VALUES (1, 'Raleigh', 'NC');
INSERT INTO cities (city_id, name, state) VALUES (2, 'Mountain View', 'CA');
INSERT INTO cities (city_id, name, state) VALUES (3, 'Knoxville', 'TN');
INSERT INTO cities (city_id, name, state) VALUES (4, 'Houston', 'TX');
INSERT INTO cities (city_id, name, state) VALUES (5, 'Olympia', 'WA');
INSERT INTO cities (city_id, name, state) VALUES (6, 'Bismarck', 'ND');