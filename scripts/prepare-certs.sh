#!/bin/sh
# Issues the TLS certificate the stack serves Artra on.
#
# Why not Caddy's built-in `tls internal`: the application itself fetches its
# own API over the public URL during server rendering, so the Node process has
# to trust the certificate too. Caddy's internal CA is created lazily, inside
# Caddy, after it starts - which is after the application has already read
# NODE_EXTRA_CA_CERTS. Generating the CA up front removes that ordering problem
# entirely and makes the trust chain something you can look at.
#
# Two files matter downstream:
#   ca.crt      mounted into the application container as NODE_EXTRA_CA_CERTS
#   server.crt  served by Caddy, and accepted by the browsers via
#               acceptInsecureCerts (they are never asked to trust the CA)
#
# The key never leaves the machine and the directory is gitignored. It protects
# a hostname reserved for testing (RFC 6761's .test) that resolves only inside
# this Docker network.

set -e

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
CERT_DIR="$ROOT/stack/caddy/certs"
HOSTNAME=${CERT_HOSTNAME:-artra.test}

if [ -f "$CERT_DIR/server.crt" ] && [ -f "$CERT_DIR/ca.crt" ]; then
    # Still valid for at least a day? Then keep it: regenerating invalidates
    # nothing, but it does churn the containers that mount it.
    if openssl x509 -checkend 86400 -noout -in "$CERT_DIR/server.crt" > /dev/null 2>&1; then
        exit 0
    fi
    echo "The existing certificate has expired - reissuing..."
fi

echo "Issuing a TLS certificate for $HOSTNAME..."
mkdir -p "$CERT_DIR"

# openssl on the host if it is there, otherwise a throwaway container - so this
# works on a machine that has nothing installed but Docker.
run_openssl() {
    if command -v openssl > /dev/null 2>&1; then
        openssl "$@"
    else
        docker run --rm -v "$CERT_DIR:/certs" -w /certs alpine/openssl "$@"
    fi
}

if command -v openssl > /dev/null 2>&1; then
    WORK="$CERT_DIR"
else
    WORK="/certs"
fi

cat > "$CERT_DIR/openssl.cnf" << EOF
[req]
distinguished_name = dn
x509_extensions    = v3_ca
prompt             = no

[dn]
CN = artra-e2e local CA

[v3_ca]
basicConstraints = critical, CA:TRUE
keyUsage         = critical, keyCertSign, cRLSign

[server]
basicConstraints       = CA:FALSE
keyUsage               = critical, digitalSignature, keyEncipherment
extendedKeyUsage       = serverAuth
subjectAltName         = DNS:$HOSTNAME, DNS:localhost, IP:127.0.0.1
EOF

# Root CA, 10 years - long enough that nobody ever has to think about it again.
run_openssl req -x509 -newkey rsa:2048 -sha256 -days 3650 -nodes \
    -keyout "$WORK/ca.key" -out "$WORK/ca.crt" \
    -config "$WORK/openssl.cnf" > /dev/null 2>&1

# Leaf, 2 years, signed by that CA.
run_openssl req -new -newkey rsa:2048 -nodes \
    -keyout "$WORK/server.key" -out "$WORK/server.csr" \
    -subj "/CN=$HOSTNAME" > /dev/null 2>&1

run_openssl x509 -req -in "$WORK/server.csr" -sha256 -days 730 \
    -CA "$WORK/ca.crt" -CAkey "$WORK/ca.key" -CAcreateserial \
    -out "$WORK/server.crt" \
    -extfile "$WORK/openssl.cnf" -extensions server > /dev/null 2>&1

rm -f "$CERT_DIR/server.csr" "$CERT_DIR/ca.srl"
chmod 644 "$CERT_DIR"/*.crt
chmod 600 "$CERT_DIR"/*.key

echo " - wrote $CERT_DIR/{ca.crt,server.crt,server.key}"
