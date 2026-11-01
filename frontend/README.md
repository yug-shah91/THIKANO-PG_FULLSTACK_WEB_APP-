# PG Manager – Frontend (React + Vite)

## Run it
1. Start your Spring Boot backend (port 8080) and PostgreSQL.
2. In this folder:
   ```
   npm install
   npm run dev
   ```
3. Open http://localhost:5173

`vite.config.js` proxies `/api` to `http://localhost:8080`, so no CORS setup is needed in development.

## First-time flow
1. Register as an owner (/register)
2. My PG → create your PG
3. Rooms → add rooms
4. Tenants → add tenants (they log in with the email/password you set)
5. Rent → "Generate this month's rent", then mark paid
(The first SUPER_ADMIN is not created through the API. Public registration refuses that role. Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` when starting the backend; `AdminSeeder` creates the account at startup. See the main README.)

## Folder structure
```
src/
  api/        axios.js (token + error helper), services.js (every backend call)
  context/    AuthContext (login state), ToastContext (popup messages)
  components/ Layout (sidebar), ProtectedRoute, Modal, UI (Badge, StatCard...)
  pages/      Login, Register, owner/*, tenant/*, admin/*
  index.css   all styling
```

## Deploying later
Run `npm run build`, host the `dist/` folder, and either serve it from the same domain as the API
or add a CORS config in Spring Boot allowing your frontend's URL.
