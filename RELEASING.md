# Releasing to Maven Central

The build produces the main jar, a sources jar, and a javadoc jar. Publishing is handled by the
[Sonatype Central Publisher Portal](https://central.sonatype.com/) through
`central-publishing-maven-plugin`, with artifacts signed by `maven-gpg-plugin` under the `release`
profile.

## One-time setup

1. **Create a Central Portal account.** Sign in at <https://central.sonatype.com/> with GitHub.
2. **Verify the `io.github.thordata2026` namespace.** Sign in with the `ThorData2026` GitHub account
   and register `io.github.thordata2026` under *Namespaces → Add namespace*. The portal verifies
   GitHub-based namespaces automatically through the account, so no DNS access is required.
3. **Generate a user token.** In the portal, open *Account → Generate User Token*. The generated
   username and password are the Maven credentials (not your GitHub login).
4. **Create a GPG key** and publish the public half:

   ```bash
   gpg --full-generate-key
   gpg --list-secret-keys --keyid-format=long
   gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   gpg --armor --export-secret-keys <KEY_ID> > private-key.asc
   ```

5. **Configure the repository.** In *Settings → Secrets and variables → Actions*, add:

   | Secret | Value |
   | --- | --- |
   | `MAVEN_CENTRAL_USERNAME` | User token username from step 3 |
   | `MAVEN_CENTRAL_PASSWORD` | User token password from step 3 |
   | `MAVEN_GPG_PRIVATE_KEY` | Contents of `private-key.asc` |
   | `MAVEN_GPG_PASSPHRASE` | Passphrase of the GPG key |

   Optionally create an environment named `maven-central` (*Settings → Environments*) to require
   approval before publishing.

## Releasing a version

1. Update `<version>` in `pom.xml` and add the matching entry to `CHANGELOG.md`. The version must
   not end in `-SNAPSHOT` and must not have been published before.
2. Commit, then create and push a tag:

   ```bash
   git tag v0.1.0
   git push origin main --tags
   ```

3. Create a GitHub Release for the tag. The `Publish` workflow runs `mvn verify` and then
   `mvn deploy -Prelease`, uploading to the Central Portal with `autoPublish` enabled.
4. Confirm the release in the Central Portal, then check
   <https://central.sonatype.com/artifact/io.github.thordata2026/thordata-java> and
   <https://search.maven.org/artifact/io.github.thordata2026/thordata-java>. Indexing usually takes
   15–30 minutes.

## Publishing from a workstation

Use a `~/.m2/settings.xml` with a `central` server entry and run:

```bash
mvn -B -ntp deploy -Prelease
```

`MAVEN_GPG_PASSPHRASE` (or the `gpg.passphrase` property) must be set for the signing step.
