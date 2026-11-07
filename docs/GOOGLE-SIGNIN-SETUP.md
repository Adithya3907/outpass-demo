# Switching on "Sign in with Google" (one-time, needs the college's Google Workspace admin)

Everyone except the test accounts and the break-glass admin signs in with their college Google account.
The portal only lets in e-mails that exist in the outpass database (Admin -> People) and end with @psgitech.ac.in.

1. Open https://console.cloud.google.com signed in with a college Workspace admin account and create a project ("PSGiTech Outpass").
2. APIs & Services -> OAuth consent screen:
   - User type: **Internal** (only people of the college Workspace can sign in - recommended, needs no Google review).
   - App name: PSGiTech Outpass, support e-mail, logo (optional). Scopes: openid, email, profile.
3. APIs & Services -> Credentials -> Create credentials -> OAuth client ID -> **Web application**.
   - Authorised redirect URIs (exactly):
     - `https://outpass.college.edu/login/oauth2/code/google`   (your real address)
     - `http://localhost:8080/login/oauth2/code/google`          (only for testing on your own PC)
4. Copy the Client ID and Client secret into the server settings:
   - Docker: `.env` -> `GOOGLE_CLIENT_ID=...`, `GOOGLE_CLIENT_SECRET=...`, then `docker compose up -d`.
   - Local: PowerShell `$env:GOOGLE_CLIENT_ID="..."; $env:GOOGLE_CLIENT_SECRET="..."; mvn spring-boot:run`.
5. Open the login page: the "Sign in with Google" button appears. Test with a registered college e-mail.

If the college e-mails are not Google accounts (e.g. Microsoft 365), tell the developer - another provider is needed.
Accounts without a Google account (for example a shared security-desk login) can be switched to password sign-in on their Edit page.
