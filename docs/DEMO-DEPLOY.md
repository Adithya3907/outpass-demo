# Public demo on a free website (Hugging Face Spaces) - step by step

The demo is a **separate copy** of the program that runs in "demo mode":
* only **fictional** people and requests (no college data at all),
* anybody with the link enters with one click as Student / Tutor / HOD / Principal / Security / Admin (no login, no Google),
* the gate page has **Scan** buttons, so one person can test the whole flow on one phone,
* it **resets itself every 30 minutes**, so whatever visitors do is wiped,
* dangerous actions (delete people, clean-up, imports, password changes) are switched off.

## IMPORTANT - never publish the real data
The normal project folder contains `data/` (the real students' names and e-mails) and may contain `photos/`.
A free public host shows your files to everybody. **Use only the ready-made "Outpass-Demo-Bundle" zip - it has no data folder.**
Never upload `data/`, `photos/`, `.env` or the full project to a public place.

## Steps (about 15 minutes, no credit card)
1. Create a free account at https://huggingface.co and click your picture -> **New Space**.
2. Space name: `outpass-demo`. **SDK: Docker** -> template **Blank**. Hardware: **CPU basic (free)**. Visibility: **Public**. Create.
3. Unzip `Outpass-Demo-Bundle.zip`. In the new Space open **Files -> Add file -> Upload files** and drag in the *contents* of the unzipped folder
   (the files `Dockerfile`, `README.md`, `pom.xml`, folders `src`, `docker`...). Commit.
   (Or with git: `git clone https://huggingface.co/spaces/YOUR-NAME/outpass-demo`, copy the files in, `git add . && git commit -m demo && git push`.)
4. The Space shows "Building" for 8-12 minutes (it downloads Java libraries). Then it says "Running".
5. Open the direct link `https://YOUR-NAME-outpass-demo.hf.space` - this is the link to share (Share -> "Embed"/"Direct URL").
6. Test it yourself in a private window: enter as Student, send a request, switch to Tutor, approve, switch to Security, press Scan.

## Good to know
* Free Spaces go to sleep after about 2 days without visitors; the first visit afterwards takes 1-2 minutes to wake up.
* Share the **direct link** (`...hf.space`), not the huggingface.co page, so the sign-in cookie works on every phone.
* To change anything later (colours, text), edit the files in the Space; it rebuilds automatically.
* To stop the demo: Space -> Settings -> Delete / make private.
* Other free hosts that accept this Dockerfile: Render, Koyeb (these have little memory, so the demo can be slow).
