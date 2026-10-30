# Band signing keys (not included)

Generate your own debug/release certs before building the watch quick app:

```bash
mkdir -p debug release
openssl req -newkey rsa:2048 -nodes -keyout debug/private.pem -x509 -days 3650 -out debug/certificate.pem -subj '/CN=beat-debug'
openssl req -newkey rsa:2048 -nodes -keyout release/private.pem -x509 -days 3650 -out release/certificate.pem -subj '/CN=beat-release'
```

`*.pem` is gitignored. For phone↔band interconnect, the Android app and the `.rpk` must share the same signing identity you configure locally (do not commit private keys).
