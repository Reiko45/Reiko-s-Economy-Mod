# Releasing and publishing

## Making a release

1. Set `mod_version` in `gradle.properties` to the new version, for example `1.1.0`.
2. Add a `## 1.1.0` section at the top of `CHANGELOG.md` saying what changed. The release notes are taken from it.
3. Commit and push, then tag the commit and push the tag:

   ```sh
   git tag v1.1.0
   git push origin v1.1.0
   ```

GitHub then builds the mod and makes a release with the jar and that version's notes (see the **Actions** and
**Releases** tabs). If the tag doesn't match `mod_version`, or the changelog has no section for it, the release
stops with an error that says so; fix it, delete the tag (`git push --delete origin v1.1.0` and
`git tag -d v1.1.0`) and tag again.

## Publishing to Modrinth and CurseForge

The same release can upload the jar to Modrinth and CurseForge. It's switched off until you set it up, and you can
set up either site on its own.

### 1. Make the project pages

- **Modrinth:** [modrinth.com](https://modrinth.com) → **Create a project** → Mod. Use `docs/listing/icon.png` as
  the icon and paste `docs/listing/description.md` (everything below the comment) as the description. Add the
  screenshots from `docs/listing/screenshots/` to the gallery. Set the license to GPL-3.0, the loader to NeoForge
  and the environment to "required on both client and server".
- **CurseForge:** [authors.curseforge.com](https://authors.curseforge.com) → **Create a project** → Minecraft →
  Mods. Use the same icon, description and screenshots.

New projects are reviewed by each site before they're public, which can take a few days.

### 2. Get the project IDs

- **Modrinth:** on the project page, the **⋮** menu → **Copy ID** (8 letters and numbers).
- **CurseForge:** the **Project ID** number in the "About Project" box on the project page.

### 3. Get upload tokens

- **Modrinth:** [Settings → Personal access tokens](https://modrinth.com/settings/pats) → **Create a token** with
  the **Create versions** permission.
- **CurseForge:** [API tokens](https://authors.curseforge.com/#/settings/api-tokens) → generate a token.

Treat tokens like passwords: anyone with one can upload files to your projects.

### 4. Add them to GitHub

In the GitHub repository: **Settings → Secrets and variables → Actions**.

| Kind | Name | Value |
| --- | --- | --- |
| Variable | `MODRINTH_ID` | The Modrinth project ID |
| Secret | `MODRINTH_TOKEN` | The Modrinth token |
| Variable | `CURSEFORGE_ID` | The CurseForge project ID |
| Secret | `CURSEFORGE_TOKEN` | The CurseForge token |

Leave out a site's pair to skip that site. From the next tag on, each release is uploaded to every site that's set
up, as a release for NeoForge on Minecraft 1.21.1.

Releases made before you set this up (like 1.0.0) aren't uploaded afterwards: download the jar from the GitHub
release and upload it by hand on each site.
