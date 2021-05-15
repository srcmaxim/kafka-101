FROM postgres:12

MAINTAINER srcmaxim

COPY connector-init-db.sql \
     /docker-entrypoint-initdb.d/

# Use `gzip connector.sql` for creating `connector.sql.gz` file
# ADD connector.sql.gz /docker-entrypoint-initdb.d/
