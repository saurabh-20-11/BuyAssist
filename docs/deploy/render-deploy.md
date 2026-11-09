# Deploying BuyAssist to Render (free tier)

This deploys both services from your GitHub repo using the `render.yaml`
blueprint at the repo root. You get a public HTTPS URL for the web console.

## What gets deployed

- **buyassist-ap2** — the Python AP2 service (FastAPI + official AP2 SDK), Docker.
- **buyassist-backend** — the Spring Boot backend + web console, Docker. It
  reaches the AP2 service over Render's private network at
  `http://buyassist-ap2:8081`.

## One-time setup

1. Push the repo to GitHub (already connected).
2. Go to <https://render.com>, sign up (free, no card), and connect your GitHub.
3. Click **New → Blueprint**, pick the **BuyAssist** repo. Render reads
   `render.yaml` and proposes both services. Click **Apply**.
4. When prompted (or afterward, per service → **Environment**), set the secrets:
   - On **buyassist-backend**:
     - `GEMINI_API_KEY` = your Gemini key (required for the AI layers)
     - *(optional, only if you want live eBay data)* set `CATALOG_SOURCE=ebay`
       and add `EBAY_CLIENT_ID` + `EBAY_CLIENT_SECRET`
   - These are marked `sync: false` in the blueprint, so Render asks for them and
     never stores them in the repo.
5. Render builds both Docker images and deploys. First build takes a few minutes
   (the AP2 image compiles crypto deps). When done, open the
   **buyassist-backend** URL — that's your live console.

## Free-tier behavior (important for demos)

- Services **sleep after ~15 minutes idle**; the first request **cold-starts in
  ~30–60s**. Open the URL a minute before showing a recruiter so it's warm.
- Gemini's free tier occasionally returns 503; the app degrades gracefully
  (the AI panel shows "unavailable", deterministic results still work). Re-click.
- For a guaranteed-smooth recruiter moment, keep a **recorded demo video** as a
  backup alongside the live link.

## Updating

Every push to your `main` branch triggers an automatic rebuild + redeploy on
Render (default behavior). No manual step needed.

## Local vs. hosted

- **Local** (fastest, most reliable): `run-demo.ps1` — see the repo root.
- **Hosted** (shareable link): the Render URL from this guide.

## Troubleshooting

- **Backend can't reach AP2**: confirm `AP2_SERVICE_URL=http://buyassist-ap2:8081`
  on the backend service (set by the blueprint). Both services must be in the
  same Render account/region.
- **Build fails on the AP2 image**: it installs the official AP2 SDK from GitHub;
  a transient network blip can fail it — click **Manual Deploy → Clear build
  cache & deploy**.
- **"Application failed to respond"**: usually a cold start; wait ~60s and retry.
