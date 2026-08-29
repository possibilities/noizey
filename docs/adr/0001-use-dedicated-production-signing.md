# 0001: Use dedicated production signing

Google Play will hold the production app-signing key and Noizey releases will use a separate upload key kept outside the repository. The legacy Android debug key remains development-only, so the existing debug-signed phone installation must migrate through a settings backup before it is replaced by a Play-signed installation.
