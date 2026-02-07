#!/bin/bash
# Generate a self-signed SSL certificate for development/testing
# For production, use a proper CA-signed certificate (e.g., Let's Encrypt)

KEYSTORE_FILE="src/main/resources/keystore.p12"
ALIAS="cqlplatform"
VALIDITY=365
PASSWORD="changeit"

echo "Generating self-signed SSL certificate..."

keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore "$KEYSTORE_FILE" \
  -validity "$VALIDITY" \
  -storepass "$PASSWORD" \
  -dname "CN=localhost, OU=CQL Platform, O=Development, L=Local, S=Dev, C=US"

echo ""
echo "Certificate generated: $KEYSTORE_FILE"
echo "Password: $PASSWORD"
echo ""
echo "To enable HTTPS, uncomment the server.ssl section in application.yml"
echo "and set SSL_KEYSTORE_PASSWORD=$PASSWORD"
echo ""
echo "WARNING: For production, use a CA-signed certificate (e.g., Let's Encrypt)"
