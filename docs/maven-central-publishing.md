# Publishing to Maven Central

Workflow: [Maven Central release](https://github.com/atomiteam/generic-dao-with-jdbi/actions/workflows/maven-central.yml).

## One-time setup

1. Sign in to the [Central Publisher Portal](https://central.sonatype.com).
2. Ensure your publishing account has verified access to **org.atomiteam**, the groupId in this project's POM. GitHub repository access alone does not grant that namespace. If migrating from OSSRH, check the migrated namespace in the Portal; contact Sonatype if it is missing. Do not change the groupId just to bypass verification: that changes the coordinates used by consumers.
3. Generate a Central Portal user token from your account settings. It provides a token username and token password; do not use your normal login password or an old OSSRH token.
4. Prepare a passphrase-protected OpenPGP signing key, and publish its **public** key to a supported keyserver so Central can verify signatures.
5. Add the secrets below under **Repository Settings → Secrets and variables → Actions → New repository secret**.

| Name | Type | Required | Value |
| --- | --- | --- | --- |
| MAVEN_CENTRAL_USERNAME | Secret | Yes | Username from the Central Portal user token |
| MAVEN_CENTRAL_PASSWORD | Secret | Yes | Password from the same Central Portal user token |
| MAVEN_GPG_KEY | Secret | Yes | Complete ASCII-armored private signing key export, including BEGIN/END lines and actual newlines; not base64 |
| MAVEN_GPG_PASSPHRASE | Secret | Yes | Passphrase protecting that private key |
| MAVEN_GPG_KEY_FINGERPRINT | Actions variable | Optional | Full fingerprint selecting a signing key when the export contains multiple keys; omit for a single key |

No personal GitHub access token, OSSRH URL, staging profile ID, AWS credentials, or manually maintained Maven settings.xml is needed. The workflow generates Maven server configuration with server ID `central`. It uses Java 11, matching the project's compiler release.

### Generate or export a signing key

Run locally with GnuPG installed:

```bash
gpg --full-generate-key
gpg --list-secret-keys --keyid-format LONG
```

Choose an RSA signing-capable key (for example RSA and RSA, 4096 bits), set an expiry you can maintain, and choose a passphrase. If you already have a suitable key, reuse it.

Replace `YOUR_FULL_FINGERPRINT` below with your key's full fingerprint:

```bash
gpg --armor --output maven-central-private-key.asc --export-secret-keys YOUR_FULL_FINGERPRINT
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_FULL_FINGERPRINT
```

Copy the entire private export into `MAVEN_GPG_KEY`. Keep the export outside the repository and remove the temporary copy after storing it securely. Only the public key is sent to the keyserver. If you configure the optional fingerprint variable, select the signing key's fingerprint.

## Publish a release

1. Commit the intended release version in `pom.xml` on `main`. The current version is `2.0`; choose a new version if that coordinate is already published.
2. Confirm the automatic build passes.
3. Open **Actions → Maven Central release → Run workflow**.
4. Select **main** and enter `release_version`, exactly matching the POM (for example `2.0`).
5. Run the workflow. This publishes publicly after Central validation; no additional Portal Publish click is needed.

The manual run checks the version, builds and runs JUnit 5 tests, generates sources and Javadoc JARs, signs the main JAR/POM/attachments, and uploads them through the Central Publishing Maven Plugin. It waits for the Portal to report `published`; search/index availability may lag.

Pushes and pull requests only run `mvn clean verify` without publishing credentials. Publication is available only from a manually dispatched run on `main`. The version input confirms the committed version; it does not modify the POM or create a Git tag. Publication uses the commit selected when the workflow is dispatched.

## Troubleshooting

- **401/403:** check the Central user-token pair and the account's namespace access.
- **Missing or invalid signature:** check the private key, passphrase, expiry, optional fingerprint, and public-key availability.
- **Version already exists:** Central releases cannot be overwritten. Commit a new version and run again.
- **Build, tests, or Javadoc fail:** fix the reported errors; the publish lifecycle stops before upload.
- **Timeout after upload:** inspect [Portal deployments](https://central.sonatype.com/publishing/deployments) before retrying; an upload may continue processing after the runner stops.
- **Skipped publish job:** dispatch from `main`.
- **Local build:** `mvn --batch-mode --no-transfer-progress clean verify` requires no signing key. Signing and Central deployment are enabled only by `-Pcentral-release`.

## References

- [Sonatype Maven publishing plugin](https://central.sonatype.org/publish/publish-portal-maven/)
- [Central publication requirements](https://central.sonatype.org/publish/requirements/)
- [PGP signatures and key distribution](https://central.sonatype.org/publish/requirements/gpg/)
- [Maven GPG plugin environment variables](https://maven.apache.org/plugins/maven-gpg-plugin/sign-mojo.html)
