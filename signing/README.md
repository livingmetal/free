# Release signing identity

The one-time bootstrap has been completed and disabled. Do not generate a new signing key for this package.

Package: `com.livingmetal.salarytimer.personal`
Release: 1.3.0 (versionCode 13)
Certificate SHA-256: `db0f4ce9e54b59070c34d9aa81767b5e228bc2661db05b5fdd975af97b60496c`
Key alias: `salarytimer`

The original PKCS12 signing key and password were recovered from the owner-encrypted backup and are being handed to the repository owner privately, not stored in this public repository.

For future CI release signing, configure `SALARY_KEYSTORE_BASE64` and `SALARY_KEYSTORE_PASSWORD` as Actions secrets, using that same retained key. These secrets have NOT been configured by the assistant. Without them, CI produces an explicitly named unsigned artifact only. Local signing using the retained PKCS12 key is also supported.

Never commit the private key or password, including Base64-encoded copies, to this repository.
