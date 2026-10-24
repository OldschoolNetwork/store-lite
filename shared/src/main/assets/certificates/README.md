# ON certificate bundle

Mozilla root certificates, exported by curl on 20 October 2026.

Source: https://curl.se/ca/cacert.pem
Published checksum: https://curl.se/ca/cacert.pem.sha256
SHA-256: `796f1389bfea13c0ed78f663b765baaff54a508e5483786bd6ece73c5e3e3cbe`
License: MPL 2.0; see MPL-2.0.txt.

The installers verify this checksum before parsing the bundle. Certificates are installed only after an explicit selection and root confirmation. Existing device certificates are retained. A root-owned recovery script is saved before any trust-store changes. Android 2.2–3.x uses its own BKS provider to preserve the device's store format. Android 4.0–13 uses OpenSSL subject-hash PEM files. Android 14+ system stores are not supported by this installer.

Root certificates extend HTTPS trust; they do not replace TLS, JavaScript, CSS or a browser engine. Applications with their own trust stores may ignore system changes. The PEM export does not preserve all Firefox-specific CA constraints; see https://curl.se/docs/caextract.html.

Do not refresh this bundle or publish Lite application updates automatically. Review and package new versions only when requested.
