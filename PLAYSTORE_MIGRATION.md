# FloodedArea — Google Play Console migration (the human steps)

This is the **manual, account-owner side** of getting FloodedArea onto Google Play — the part
done inside the Play Console by Ardz, not automated.

- The **build/packaging** side (wrapping `https://floodedarea.com/` as a Trusted Web Activity
  with Bubblewrap, producing the signed `.aab`) lives in `../baha-ui/PLAYSTORE_PACKAGE.md`.
- The **spec of record** is `PLAYSTORE_PACKAGE_REQUIREMENTS.md` (same dir).

The flow: the build steps produce a signed `.aab` file → you take over here and do everything
in the Play Console with that file.

---

## Step 0 — Have these ready first

- A Google account (prefer a dedicated business one, not a personal day-to-day account).
- A **credit/debit card** — there is a **one-time $25 USD** registration fee (~₱1,400).
- The signed **`.aab`** file (from the Bubblewrap build).
- App art (exact sizes in Step 4).
- A **Privacy Policy URL** on floodedarea.com (needed in Step 3).

---

## Step 1 — Create your Play Console developer account

1. Go to **https://play.google.com/console** and sign in.
2. Choose account type: **Personal** (faster) or **Organization** (needs a D-U-N-S business
   number).
   - ⚠️ Personal accounts have the **12-testers / 14-day** rule (Step 8). Plan for it.
3. Pay the **$25 one-time fee**.
4. Verify identity — Google asks for **name, address, phone, and a government ID**. Takes a few
   hours to a couple of days. You cannot publish until it clears.

---

## Step 2 — Create the app

1. Play Console → **Create app**.
2. Fill in:
   - **App name:** `FloodedArea` (or "FloodedArea — Flood Watch")
   - **Default language:** English (or Filipino)
   - **App or game:** App
   - **Free or paid:** Free
3. Accept the declarations (developer policies, US export laws).

---

## Step 3 — Complete the "Set up your app" checklist

Play Console shows a checklist on the dashboard. All must be done before publishing:

- **App access** — Some features need login. Provide **test login credentials** (a demo
  email/password) so reviewers can get in, or mark which parts are public. The map is public —
  explain that.
- **Ads** — Declare whether the app shows ads (No, unless added later).
- **Content rating** — Questionnaire (violence, etc.). FloodedArea rates **Everyone**. Rating
  certificate is issued automatically.
- **Target audience** — Select age groups. Avoid "under 13" unless intended (triggers strict
  child-privacy rules).
- **Data safety** — ⚠️ Important. Declare what data the app collects. FloodedArea collects:
  **email (login), approximate/precise location (flood reports & SOS), name**. Be honest — this
  shows on the store listing. Requires a **Privacy Policy URL**.
- **Government apps** — Declare it is **not** an official government app (it is crowd-sourced).
- **News / Health apps** — Declare No unless relevant.

---

## Step 4 — Build the store listing (what users see)

Under **Store presence → Main store listing**:

- **Short description** (max 80 chars) — e.g. *"Live flood reports, evacuation centers & SOS for
  Metro Manila."*
- **Full description** (max 4000 chars).
- **App icon** — 512×512 PNG.
- **Feature graphic** — 1024×500 PNG (the banner).
- **Screenshots** — at least **2** phone screenshots (16:9 or 9:16, min 320px).
- **Category** — Maps & Navigation or Weather.
- **Contact email** (required) and website.

---

## Step 5 — Upload the app (.aab)

1. **Release → Testing → Closed testing** (start here, NOT Production — see Step 8).
2. **Create a new release.**
3. **Play App Signing:** accept it when prompted (recommended).
   - ⚠️ This is why the website's `assetlinks.json` must use the **app-signing key fingerprint**
     shown in **Play Console → Test and release → App integrity → App signing** — *not* the
     upload key.
4. **Upload the `.aab`.**
5. Add release notes, save.

---

## Step 6 — Fix Asset Links (so the app opens without a browser bar)

After upload, copy the **SHA-256 app-signing fingerprint** from **App integrity** and ensure the
live site serves it at:

```
https://floodedarea.com/.well-known/assetlinks.json
```

If it doesn't match, the app opens with a browser URL bar instead of looking like a real app.
This is the one technical loop-back between Play and the website.

---

## Step 7 — Pricing & countries

Under the release track:

- **Countries:** Philippines (and others if desired).
- **Pricing:** Free.

---

## Step 8 — ⚠️ The closed-testing requirement (personal accounts)

For **personal** accounts, before Production unlocks you must:

1. Run a **Closed test** (the track from Step 5).
2. Recruit **at least 12 testers** who **opt in** via a link/email list.
3. Keep them opted in for **14 continuous days**.

Practical order:

- Add testers (their Gmail addresses) under **Closed testing → Testers**.
- Share the opt-in link; get them to install it.
- Wait 14 days.
- Then the **Production** track unlocks.

---

## Step 9 — Submit to production & review

1. After the 14-day gate: **Release → Production → Create release** → upload the same `.aab` (or
   a newer one).
2. Submit. Google reviews it — usually a few days, sometimes up to 7.
3. Once approved, it is **live on Google Play**. 🎉

---

## Realistic timeline

| Stage | Time |
|---|---|
| Account verification | hours – 2 days |
| Closed test (12 testers, 14 days) | **14 days minimum** |
| Production review | a few days |

Budget **~3 weeks** from account creation to going live, mostly the 14-day test gate.

---

## Repo can help with (not Play Console work)

1. Draft the store listing text (short + full description, English + Filipino).
2. Write the Privacy Policy page and wire up `assetlinks.json` on the site (needed for Steps 3
   and 6).
