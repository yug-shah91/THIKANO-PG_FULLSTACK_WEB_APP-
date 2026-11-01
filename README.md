# PG Management – Full Stack

```
backend/    Spring Boot + PostgreSQL API   (http://localhost:8080)
frontend/   React + Vite app               (http://localhost:5173)
```

## 1. Configure the backend (required – secrets are no longer in the code)

The app will NOT start without a database password and a JWT secret.

**Easiest: a local file (git-ignored)**
```
cd backend/src/main/resources
cp application-local.properties.example application-local.properties
```
Edit `application-local.properties` and fill in `spring.datasource.password` and `jwt.secret`
(generate a secret with `openssl rand -base64 48`, at least 32 characters).
Then start the backend with the `local` profile:
- IntelliJ: Run configuration -> "Active profiles" = `local`
- Terminal: `SPRING_PROFILES_ACTIVE=local mvn spring-boot:run`

**Or environment variables:** `DB_PASSWORD`, `JWT_SECRET` (required);
`DB_URL`, `DB_USERNAME`, `DDL_AUTO`, `CORS_ALLOWED_ORIGINS` (optional).

**First super admin (optional):** set `ADMIN_EMAIL` and `ADMIN_PASSWORD` (or `app.admin.email` /
`app.admin.password` in the local file) once; the account is created at startup if it does not exist.
Public registration can create OWNER and TENANT accounts, never SUPER_ADMIN.

## 2. Run
```
# terminal 1
cd backend && mvn spring-boot:run

# terminal 2
cd frontend && npm install && npm run dev
```
Open http://localhost:5173 and register as an owner.

## How they are connected
- Frontend calls `/api/...`; Vite proxies it to port 8080 (`frontend/vite.config.js`).
- Backend also allows `http://localhost:5173` via CORS (`app.cors.allowed-origins`).
- Login returns a JWT; the frontend sends `Authorization: Bearer <token>` on every call.
- On start-up the frontend calls `GET /api/auth/me` to confirm the session.
- The backend re-checks the user in the database on every request, so a checked-out
  or deactivated user is locked out immediately.

## Database
`DDL_AUTO` now defaults to `update` (keeps your data). Never use `create` – it wipes the database
on every restart. For production use `validate` + a migration tool.

## Security / bug-fix notes (what changed in the backend)
| # | Fix |
|---|-----|
| 1 | Public sign-up can create OWNER or TENANT only; SUPER_ADMIN is created by `AdminSeeder` from env vars |
| 2 | DB password / JWT secret read from environment; app refuses to start with a missing or short secret |
| 3 | `ddl-auto` default `update`, configurable |
| 4 | `JwtFilter` loads the user from the DB each request; inactive/deleted users are rejected (401), role comes from the DB |
| 5 | "Generate rent" button only generates for the caller's PG (the monthly cron still covers all PGs) |
| 6 | Room/tenant rows are locked during allocate/checkout; checked-out tenants can't be allocated; same-room and double-checkout blocked; allocation is atomic |
| 7 | `Room.refreshStatus()` is the single source for AVAILABLE/FULLY_OCCUPIED; leaving maintenance no longer marks a full room AVAILABLE |
| 8 | Owner transitions: OPEN→IN_PROGRESS/RESOLVED, IN_PROGRESS→RESOLVED, RESOLVED→IN_PROGRESS. CLOSED is tenant/auto only |
| 9 | Auto-close is one SQL UPDATE, nothing loaded into memory |
| 10 | Room update now applies room number (unique), type and capacity (not below occupancy) as well |
| 11 | Penalty rule made explicit: one flat % of base rent, once; null-safe, validated 0–100, applied at payment time too if the nightly job hasn't run yet |
| 12 | Due day clamped to the month length (31 in February is safe); due day validated 1–28 |
| 13 | Records are not created without a valid positive amount; they are counted as "skipped" |
| 14 | Report month 1–12 and year 2000–2100 validated; bad parameter types return 400 instead of 500 |
| 15 | Status + paidAt + penalty written in one guarded SQL UPDATE; the nightly job and "Mark paid" can't overwrite each other |

Other: `@PreAuthorize` denials return 403 (not 500); missing/invalid token returns 401.

## Round 2: frontend + design fixes
| # | Fix |
|---|-----|
| 16 | Axios: 401 = log out (via an app-wide event, no page reload); 403 = "not allowed", user stays logged in. An already-expired token is detected before the request is sent. |
| 17 | `localStorage.clear()` removed. Only `pg.token` and `pg.user` are touched, always inside try/catch (`src/api/storage.js`). |
| 18 | The role in the browser only chooses menus/pages. On start-up the app asks `GET /api/auth/me` and replaces the stored role with the server's answer. Real enforcement is, and always was, the backend. |
| 19 | Rent page: separate loading, error (with "Try again") and "Generating…" states; buttons are disabled while a request runs. |
| 20 | Room update contract: all six fields of the form are editable and applied (blank rent = PG base rent, empty floor/amenities clear them). The form shows the minimum capacity. |

| Topic | Decision |
|-------|----------|
| Multi-PG | Version 1 = **one PG per owner**, now enforced by a database unique constraint (`uq_pg_owner`), not just by code. To support several PGs later: drop the constraint and pass a `pgId` through the services. |
| Rent history after a room move | Each rent record stores the room number at generation time. Older records fall back to the tenant's current room. |
| Unpaid rent after checkout | It stays on file, keeps its penalty, stays in the owner's Pending/Overdue lists with a "CHECKED OUT" tag and in the dashboard's outstanding amount, until the owner marks it paid. (Business rule - change it if you want checkout to settle or cancel rent.) |
| Dashboard | Room counts come from real occupancy, not the stored status. Pending/overdue counts now cover ALL unpaid months, the same as the outstanding amount. |
| Error messages | Rule violations use `BusinessException` (message shown, HTTP 400). Any other exception is logged on the server and the client only sees a generic message (HTTP 500). Login says "Invalid email or password" for both unknown email and wrong password. |

## Round 3: tenants find and book PGs themselves
**Try it:**
1. Register as **List my PG** (owner) -> *My PG*: fill the details, press **Use my current location**, save -> *Rooms*: add a few rooms.
2. Log out. Open `/pgs` (no login needed) and look around. Register as **Find a PG** (tenant).
3. Press **Near me**, open a PG, press **Book this room**, confirm. You land on *My Stay*; the owner sees you in *Tenants*.

**Rules**
- A room can be booked only if it has a free bed and is not under maintenance. Full rooms show "No beds free" and the backend refuses them too (the room row is locked, so two people can not take the last bed).
- No owner approval step. The owner can still add tenants by hand (walk-ins).
- One booking per tenant account. To change it, the owner uses *Checkout*. (A checked-out account is deactivated, so it can not book again - a known limitation.)
- Move-in date: today up to 60 days ahead. Rent is not generated before the month the tenant moves in.
- The owner's phone (PG contact number, or the owner's own phone as fallback) is shown only to logged-in users.
- "Near me" uses the browser's location. It needs HTTPS when deployed (`localhost` is fine). If the user says no, text search still works. The location is only sent with the search, never stored.
- PGs without latitude/longitude still appear, but without a distance, at the end of a "near me" search.

**New endpoints:** `GET /api/public/pgs` (q, maxRent, roomType, onlyAvailable, lat, lng, radiusKm), `GET /api/public/pgs/{id}`, `POST /api/tenant/bookings`.

**Design:** red / white / black, "Plus Jakarta Sans" font (loaded from Google Fonts, falls back to the system font offline). Colours are variables at the top of `frontend/src/index.css`, so you can change the red in one place. PGs show a colour block with their first letter until photo upload exists.

## Round 4: a livelier public page
- `PgScene.jsx`: every PG gets its own illustrated building; **each lit window is a free bed** (no photos needed).
- `HeroBuilding.jsx`: big hero building, click a lit window to "book" it (just a demo, nothing saved).
- Rotating headline word, count-up stats, city quick-pick chips, free-bed dots on cards, scroll fade-ins (`Reveal.jsx`), "how it works" and an owner call-to-action.
- All motion respects `prefers-reduced-motion`. No new npm packages.

## Round 5: in-person visit requests
- A tenant opens a PG, taps **Schedule a visit**, picks a day (next 14 days) and an hourly slot (10:00 to 17:00, at least 1 hour from now). Optional note for the owner.
- The owner sees it under **Visits** (red number in the menu counts new requests) and **confirms or declines**, with an optional message. Only after confirming does the tenant see the owner's phone number.
- Tenants can cancel; owners can cancel a confirmed visit. Rules: one active visit per PG, max 3 active visits per person.
- Both pages refresh every 30 seconds and show a "Live" dot, so no manual reload.
- Backend: `VisitRequest` entity, `VisitService`, `VisitController` (`/api/tenant/visits`, `/api/owner/visits`, `GET /api/public/visit-slots`). Tests: `VisitServiceTest`. As before, nothing was compiled here, so run `mvn test` first.

## Audit checklist status (PG_Management_Project_Audit_Checklist.docx)
Done in code (earlier rounds): JWT checks DB user/role each request; capacity vs occupancy; occupancy through locked service methods; inactive tenants rejected in allocation; room status derived in one place; booking row locks; rent unique per tenant/month; due-day range and end-of-month rule; rent amount validation; rent snapshot (amount + room number); report month/year 400s; outstanding rent kept after checkout; generic 500s; 401 vs 403 handled separately; logout removes only app keys; loading/error states; DB-side complaint auto-close; secrets from env.

Done now (Round 6):
- Admin seeder logs a clear warning when `ADMIN_EMAIL` already belongs to a non-admin account.
- Login rate limit: 5 wrong passwords per email + IP in 15 minutes returns HTTP 429 (`LoginRateLimiter`, in memory, per server).
- Passwords: 8 to 72 characters on every account-creation path (and in the forms).
- Room number unique per PG in the database (`uq_room_pg_number`). Existing databases with duplicates must be cleaned before this applies.
- Complaints keep the room number from when they were raised (moves no longer rewrite old complaints).
- One explicit timezone (`APP_TIMEZONE`, default `Asia/Kolkata`) for "today", visit times and the scheduled jobs.
- Visit rules documented: several visitors may ask for the same slot (the owner decides), one active visit per person per PG, same-day times must be 1 hour ahead, transitions are checked.

Decided on purpose: **booking stays instant** (no owner approval), as you asked earlier. The checklist's first item assumes approval; ignore it unless you change your mind.

Not done (needs real infrastructure or is a bigger feature): Flyway migrations (for production set `DDL_AUTO=validate` and write the SQL yourself until then), PostgreSQL integration/concurrency tests, CI, backups, multi-server job locking, audit log, complaint timeline, notifications, CSV export, pagination on tenant/rent tables, per-owner cross-tenant integration tests, production CORS review. Tell me which of these you want next.

## Round 7
**Booking policy: the owner chooses, per PG (My PG page, "How tenants book").**
- **Instantly** (default): a free bed is booked at once, no approval. A full room can not be booked.
- **On request**: the tenant sends a request, the bed is **held for 48 hours** (it counts as occupied, so nobody else can take it and search/availability stay correct), and the owner approves or declines under **Bookings**. Approve creates the tenant in that bed; decline, cancel or no answer in 48 hours (checked every 10 minutes) releases it. A tenant sees the answer live under **My Booking**.
- The server enforces it: `bookRoom` refuses on-request PGs and the request endpoint refuses instant PGs (`PgBookingPolicy`). Switching the mode only affects new bookings. Old PGs without a mode count as instant.
- Rule to remember: room occupancy = active tenants + pending holds.
- **Expired holds are handled everywhere, not only by the 10-minute job:** a new request, an owner allocation or an instant booking first releases any expired hold on that room; the public search and PG page count expired holds as free beds; the "waiting" counter ignores expired ones. Only the owner's dashboard numbers can lag by up to 10 minutes.
- **Approving an expired request returns status EXPIRED (no error).** An exception there would roll the transaction back and undo the release; `ConcurrencyIT` checks the database after the call.
- **Every hold is released exactly once:** only a PENDING request can be released, under its row lock (`release()` refuses anything else). If occupancy is ever already 0 it logs an ERROR with the ids instead of silently hiding the problem.
- `RoomBookingService` takes a `Clock`, so tests set "now" and check the exact expiry moment, the 48 hours and the 0 to 60 day move-in window.
- The owner list is paged (10 per page, max 50); a tenant sees their latest 50 requests.
- The My PG page tells the owner how many requests are still waiting and that they stay valid after a mode change.
- Tests: `RoomBookingServiceTest`, and `ConcurrencyIT` (last-bed hold, cancel and decline at the same time, expired approval).

**Races found and fixed while writing the concurrency tests**
- One person double-clicking "Book" on two rooms could create two bookings: now their user row is locked first.
- The same visit request sent twice at once could create duplicates: same fix in `VisitService`.
- Two rent generations at once: the loser used to fail the whole batch on the unique constraint. Now a Postgres advisory lock makes them run one after the other (the second sees "already existed").

**Tests**
- `ConcurrencyIT` (Testcontainers + real PostgreSQL): last-bed race, double booking, duplicate visit, concurrent rent generation, duplicate room number. Needs Docker. Run `mvn verify` (they are skipped without Docker, and `mvn test` does not run them).
- `LoginRateLimiterTest`: repeated failures, expiry, success reset, sweep above 10,000 entries (uses a fake clock). The limiter's cleanup now uses `remove(key, value)` and takes an injectable `Clock`.

**Public search speed:** the rooms of all matching PGs are now loaded with ONE query (`RoomRepository.findByPgIds`) instead of one per PG, and the text filter runs before any room is loaded. `PublicPgServiceTest` checks this.

**True monthly cost calculator**
- Owner: new **Charges** page lists everything besides rent: every-month (required), every-month (optional, e.g. meals, laundry) and one-time (deposit, joining fee).
- Tenant: PG page shows **True monthly cost**: pick a room, tick services, add commute (your own number). The server adds it all up (`CostEstimateService`, tested) and shows rent, extras, total, and the money needed on day one. It only uses numbers the owner entered; nothing is guessed.

**Move-in evidence and deposit protection** ("Room report" for owner per tenant, "Room Report" in the tenant menu)
- The owner starts a move-in report; owner and tenant each add lines (part of room + condition + note) and photos; each presses Confirm. When both confirmed it is locked.
- At move-out the owner starts a move-out report. The comparison shows, per part of the room, move-in vs move-out, and flags places that look worse. Damage already recorded at move-in is marked as such.
- Photos: JPEG/PNG/WebP up to 5 MB, type checked from the file's first bytes, stored in `UPLOAD_DIR` (default `./uploads`, git-ignored, outside the web root), shown only to the owner and that tenant, never deletable, with a server timestamp and a SHA-256 fingerprint.
- Do the move-out report BEFORE pressing Checkout, because a checked-out tenant can no longer log in to confirm.
- The comparison does not calculate deductions. Back up `UPLOAD_DIR` together with the database.
- Not included yet: photo EXIF checks, e-signatures, e-mail notifications.

## Tests
Unit tests (JUnit 5 + Mockito, no database needed) cover: registration/role rules, JWT filter with checked-out users,
room allocation / checkout / maintenance, rent generation, penalty, mark-paid races, report validation and complaint transitions.
```
cd backend && mvn test
```
Not covered yet: controller/security integration tests and a real-database test. Those are the next step.

## Round 8: photos, map, reviews, skeletons

- **Photos**: owners upload up to 8 JPEG/PNG/WebP photos (5 MB each) under *Photos*. The first is the cover on search cards. Files are type-checked by content, saved under `UPLOAD_DIR` with random names, and served publicly (cached 1 h) at `/api/public/pg-photos/{id}`.
- **Map**: the PG list has a List/Map toggle (Leaflet + OpenStreetMap tiles, price pins). The PG detail page shows a "Where it is" map. Run `npm install` in `frontend` to fetch `leaflet`.
- **Reviews**: a tenant can review their own PG after living there 7 days (one review per tenant, editable, 1–5 stars, comment up to 1000 chars). Owners reply under *Reviews*. Averages show on cards and detail pages.
- **Skeleton loading**: `Loader`/`SkeletonGrid` show shimmering placeholders instead of spinners.
- **Fix**: `PgVisual` always wraps in `.pg-visual` so card images keep their 4:3 frame.

Not yet run: do `mvn test`, then `mvn verify` (Docker needed for the integration tests), and `npm install && npm run dev` to check the new screens.

## Round 9: fixes from the audit review

**Tenant stays (checkout no longer locks people out).** Checkout ends the *stay* (`tenants.is_active = false`) and leaves the *user account* active. The person can still log in to see their profile, rent history and the move-out Room report, can review their PG, and can book again. A new booking creates a **new `tenants` row** (one row per stay). Rules:
- Only **one active stay per person**: a partial unique index `uq_tenant_one_active_stay` (`user_id WHERE is_active = true`) is created at startup by `config/DatabaseRules`, which then **checks the index exists and is valid**. The same class drops the old `unique(user_id)` constraint on existing databases. If anything fails (for example old data already has two active stays for one person) the application **refuses to start** and says what to fix. Until you move this into a Flyway migration, the rule is applied at startup, not by a versioned migration.
- `TenantRepository.findActiveByUserEmail` = current stay (book, raise complaint). `findCurrentByUserEmail` = current stay, else the latest finished one (profile, rent history, room report, reviews).
- Complaints need a current stay. Former tenants may still review if they really stayed 7+ days.

**Enum queries.** The JPQL that compared enums with `'AVAILABLE'`, `'PENDING'`, `'OVERDUE'` text now uses enum parameters. The old method names remain as small `default` methods, so callers did not change. `ConcurrencyIT.enumQueriesRunAgainstRealPostgres` runs them on real PostgreSQL.

**Lock order (deadlocks).** Every workflow takes locks in this one order, and may skip a step but never go back: **user → tenant → rooms (lowest id first) → booking**.
- Moving a tenant locks both rooms in ascending id order, so A→B and B→A at the same time cannot deadlock.
- Booking cancel / approve / decline / expire first read only the ids, lock user and room, and only then load the booking (so its status is fresh).
- The 10-minute expiry job (`BookingExpiryJob`) expires each request in its **own transaction** (`expireOne`), so one transaction never holds locks on many rooms.
- Tests: `ConcurrencyIT` (swap rooms, cancel + approve + decline + expire together, expiry vs approval) and unit tests that check the lock order.

**Other fixes.** Date-only values (`2026-11-01`) no longer show a day early (`utils/dates.js`, test with `npm test`, run in 6 timezones). A missing inspection photo file is a 404, not a 500; a photo file is deleted again if the database transaction rolls back. Lombok annotation processing is configured explicitly in `pom.xml`. `PGRequest.clearCoordinates = true` removes a saved location. Owners can **Move room** from the Tenants page. One `Clock` is injected in Rent, Tenant, Visit, Review, Complaint, Inspection and Public search services.

**Public search.** The text filter now runs in the database and the result is capped at 100 PGs. Distance, free beds (which must account for expired holds) and sorting still run in memory on that smaller set. Full database pagination would need the free-bed and distance logic moved into SQL: not done yet.

**Not done (be aware).** Orphan-file reconciliation job; Flyway migrations (see below); automated tests that call every owner endpoint with another owner's ids (the services check ownership and have unit tests, but there is no HTTP-level test yet); pagination of the tenant, rent and visit lists.

**Production checklist.** Use Flyway/Liquibase instead of `ddl-auto=update`, keep `JWT_SECRET`, `DB_PASSWORD` and `ADMIN_PASSWORD` out of git, schedule database backups and test a restore, and make sure logs never print passwords, JWTs, ID proofs or inspection data (the code does not log them today).

## Round 10: second review

- **Login errors**: unknown email, wrong password and a deactivated account now all give `Invalid email or password`. A dummy password check runs for unknown emails so response time does not reveal them either. Deactivated users need support through a separate route, not through the login message.
- **Booking release**: if releasing a hold finds occupancy already 0 (impossible data), the transaction is **rolled back** with an error instead of marking the request closed. Same for checkout/move-out in `TenantService`.
- **Rate limiter**: expired entries are removed every 5 minutes, and memory is hard-capped at 10,000 entries (flooding evicts the least dangerous entries first; already blocked ones are kept). It is still per server instance: use Redis if you run several.
- **Docs**: `frontend/README.md` no longer says a super admin can be created through the API. Use `ADMIN_EMAIL` / `ADMIN_PASSWORD` (see `AdminSeeder`).
- **Startup rule**: see Round 9, `DatabaseRules` now fails startup instead of logging.
- **Frontend checklist** (checked by reading the code, not in a browser): expired tokens log the user out before the request is sent, and a 401 logs out while a 403 shows the message; the location-denied case shows a message and search by city still works. Double submits: forms that lacked a guard (tenant complaint, rooms, my PG, visit answer, add/move tenant) now use the `useBusy` hook (`src/hooks.js`), which ignores a second click while the first request runs. Not verified: that owner/tenant pages never show other people's data (the backend enforces it; there is no HTTP-level test yet) and the visual result in a browser.

## Round 11: rate limiter, Flyway, ownership tests

- **Login rate limit is atomic.** `LoginRateLimiter.acquire(email, ip)` checks AND counts the attempt in one locked step *before* the password is checked, so 50 simultaneous requests let exactly 5 reach the password check. A correct password calls `recordSuccess`, which gives the attempts back. The map is only read and written inside that lock and room is made before a new key is added, so it can never hold more than 10,000 keys (the earlier "hard limit" claim was too strong for the old code; it is true now). Tests: `LoginRateLimiterTest` (50 threads on one key, 40 simultaneous failed logins through the real `AuthController`, 8 threads flooding new keys, many rounds because races are random).
- **Index check (P0).** `DatabaseRules` reads the index definition back from PostgreSQL and checks table, one column (`user_id`), UNIQUE, valid, btree and the `WHERE is_active = true` condition. A wrong index with the right name stops startup. `ConcurrencyIT` also checks `pg_get_indexdef`.
- **Flyway.** `flyway-core` is added and `db/migration/V2__one_active_stay_per_user.sql` holds the rule. Flyway is **off by default** (`FLYWAY_ENABLED=false`). How to switch:
  1. Run the app once with Flyway off so Hibernate creates the tables (new database), or use your existing database.
  2. Set `FLYWAY_ENABLED=true`. Flyway baselines the existing schema as version 1 and applies V2. From now on `DatabaseRules` only verifies.
  3. Every later schema change is a new `V3__...sql`; then set `DDL_AUTO=validate`.
  Not done: a real `V1` script that creates every table. I could not generate and test it here. To make one, run `pg_dump --schema-only` on a database Hibernate created and save it as `V1__baseline.sql`; then Flyway can also build an empty database on its own.
- **HTTP ownership tests.** `AuthorizationIT` (MockMvc, real JWT, real PostgreSQL): owner A and an outsider tenant try to read or change owner B's tenant, rooms, rent, complaint, charge, photo, review reply, visit, booking request and inspection. Each must get 403/404 and the record must be unchanged. It also checks role separation, anonymous and garbage tokens, and that a deactivated account's token stops working.
- **Frontend.** `useBusy` now also guards room maintenance, checkout, closing a complaint, removing a charge, cancelling a booking or visit, deleting a review, and the owner complaint update.

## Round 12: Thikano redesign

What changed
- **Look:** navy `#172D46`, warm white `#F7F5F0`, sand `#D5A66B`, sage `#3D8174`; Manrope headings, Inter body. Flat panels, small corners, no illustrations. Brick red is used only for errors and overdue rent.
- **Landing page (`/`)**: headline, location + budget search, "Explore PGs", separate "List your property". Photos come only from owner uploads (a quiet "No photo yet" block otherwise). The numbers shown (PGs listed, free beds, reviews) are counted from live data. There is no verification system, so nothing says "verified".
- **Search (`/pgs`)**: filter panel (location, budget, gender preference, occupancy, facilities, availability), 3-column grid, List/Map toggle. Filters live in the URL.
- **Owner dashboard:** revenue this month, occupancy, available beds, outstanding rent, 6-month rent chart, recent bookings, pending complaints, recent tenant activity.
- **Rooms:** availability labels, bed strip, grouped Edit / Maintenance actions, empty states. **Charges:** a table (name, amount, billing frequency, status).
- **Backend support:** `GenderPolicy` on PG (`ANY`, `MEN_ONLY`, `WOMEN_ONLY`; needs a `gender_policy` column, `ddl-auto` adds it, or add it in a migration), `gender` and `amenities` search params, dashboard bed counts and `rentTrend`.

How to run
- `cd frontend && npm install` (Leaflet is needed for the map), then `npm run dev`.
- `cd backend && mvn test` (nothing here has been compiled by me; please run it).
- Removed: PgScene, HeroBuilding, Reveal components.

### Round 12c: editorial look
The interface follows an editorial direction: paper background, forest green, Instrument Serif headings, Courier Prime labels, hairline rules, square corners and large photographs. Fonts load from Google Fonts. A green ticker at the top of the public pages has a pause button and only states things the app really does.

### Round 13: warm light theme
Teal for actions and availability, coral for rent and booking buttons, warm white background, Plus Jakarta Sans. Cards show Boys PG / Girls PG tags, sharing types (Single, Double, Triple) and a Meals included badge when the owner lists meals. The landing page has quick links for Boys PG, Girls PG and the cities that really have listings.
