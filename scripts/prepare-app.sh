#!/bin/sh
# Stages the application under test into .artra-build/, which is what
# docker-compose builds the app image from.
#
# Why stage rather than build the checkout in place: the Dockerfile and the
# fixture seeder belong to this repository, not to Artra's, and copying them
# into someone's application checkout would dirty a repository this project does
# not own. Staging also makes the two ways of getting the source - a local
# checkout, or a clone in CI - end at exactly the same layout.
#
#   ARTRA_PATH  path to a local checkout        (default: ../Artra)
#   ARTRA_REPO  clone URL, used when ARTRA_PATH does not exist
#   ARTRA_REF   branch or tag to clone          (default: main)

set -e

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
STAGE="$ROOT/.artra-build"

ARTRA_PATH=${ARTRA_PATH:-"$ROOT/../Artra"}
ARTRA_REPO=${ARTRA_REPO:-https://github.com/7Luke7/Artraa.git}
ARTRA_REF=${ARTRA_REF:-main}

if [ ! -d "$ARTRA_PATH" ]; then
    CLONE="$ROOT/.artra-src"
    echo "No checkout at $ARTRA_PATH - cloning $ARTRA_REPO ($ARTRA_REF)..."
    rm -rf "$CLONE"
    git clone --depth 1 --branch "$ARTRA_REF" "$ARTRA_REPO" "$CLONE"
    ARTRA_PATH="$CLONE"
fi

if [ ! -f "$ARTRA_PATH/package.json" ]; then
    echo "ERROR: $ARTRA_PATH does not look like the Artra application (no package.json)."
    echo "       Point ARTRA_PATH at the checkout, or unset it to clone a fresh copy."
    exit 1
fi

echo "Staging the application from $ARTRA_PATH..."
rm -rf "$STAGE"
mkdir -p "$STAGE"

# Only what the build needs. node_modules and .output are excluded on purpose:
# they are the host's, built against the host's platform, and copying them in
# would both bloat the context and risk a native module compiled for the wrong
# architecture ending up in the image.
for entry in package.json package-lock.json app.config.js jsconfig.json src public migrations; do
    if [ -e "$ARTRA_PATH/$entry" ]; then
        cp -R "$ARTRA_PATH/$entry" "$STAGE/"
    fi
done

cp "$ROOT/stack/app/Dockerfile" "$STAGE/Dockerfile"
cp "$ROOT/stack/app/.dockerignore" "$STAGE/.dockerignore"
cp "$ROOT/stack/app/seed-users.mjs" "$STAGE/seed-users.mjs"

# The database schema is the application's, not this repository's. The stack
# applies these on first start (see stack/db/init/01-schema.sh), which is what
# makes every run exercise the same migrations a deployment would - and what
# stops the two from drifting, which is what happened when the test repository
# kept its own copy of the schema.
if [ ! -d "$STAGE/migrations" ]; then
    echo "ERROR: $ARTRA_PATH has no migrations/ directory."
    echo "       The stack builds its database from the application's migrations."
    echo "       Update the checkout, or point ARTRA_REF at a branch that has them."
    exit 1
fi

echo " - staged $(find "$STAGE/src" -type f | wc -l) source file(s) and"
echo "   $(find "$STAGE/migrations" -name '*.sql' | wc -l) migration(s) into .artra-build/"
