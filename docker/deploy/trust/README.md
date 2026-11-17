# Public MQTT trust roots

`isrg-roots.pem` contains public ISRG Root X1 and X2, retrieved from the official
Let's Encrypt certificate pages on 2026-11-17. It contains no private keys or
instance-specific CA. The same roots are shipped by universal Agent firmware.

Sources:
- https://letsencrypt.org/certs/isrgrootx1.pem
- https://letsencrypt.org/certs/isrg-root-x2.pem

DER SHA-256 fingerprints:
- X1: `96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6`
- X2: `69729b8e15a86efc177a57afb7171dfc64add28c2fca8cf1507e34453ccb1470`

Updating this set is a reviewed deployment/firmware compatibility change.
Do not replace it with a server leaf, intermediate or private installation CA.
