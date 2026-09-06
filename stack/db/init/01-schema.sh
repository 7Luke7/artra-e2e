#!/bin/sh
# Builds the database from the application's own migrations.
#
# This repository used to keep its own copy of the schema, because Artra had no
# migration tool and there was otherwise no way to stand the application up from
# nothing. Artra now owns its schema in migrations/, and scripts/prepare-app.sh
# stages that directory here - so there is one definition of the schema, and
# every run of the suite applies exactly the migrations a deployment would.
#
# A migration that is broken now fails the suite rather than a release.
#
# Postgres runs everything in /docker-entrypoint-initdb.d once, in name order,
# the first time the data directory is empty. 02-seed.sql follows this.

set -e

MIGRATIONS=/migrations

if [ ! -d "$MIGRATIONS" ]; then
    echo "ERROR: $MIGRATIONS is not mounted - the schema cannot be built."
    echo "       Run ./scripts/prepare-app.sh, which stages the application's"
    echo "       migrations into .artra-build/."
    exit 1
fi

# ON_ERROR_STOP is what makes a broken migration fail the container instead of
# leaving a half-built database that the app then starts against and the tests
# then fail against, thirty assertions away from the actual cause.
for file in "$MIGRATIONS"/*.sql; do
    echo "[schema] applying $(basename "$file")"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
         --quiet --no-psqlrc --file "$file"
done

echo "[schema] applied $(ls -1 "$MIGRATIONS"/*.sql | wc -l) migration(s)"
