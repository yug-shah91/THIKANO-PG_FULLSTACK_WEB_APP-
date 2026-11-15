// Shared name of the browser event that says "the server looks like it is still waking up".
// api/axios.js fires it, components/WakeBanner.jsx listens for it.
export const WAKING_EVENT = 'pg:server-waking'
