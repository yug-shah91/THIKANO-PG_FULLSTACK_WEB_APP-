// All backend calls live here, grouped by feature.
// Pages just call these functions - they never write URLs themselves.
import api from './axios'

export const authApi = {
  login: (data) => api.post('/auth/login', data),
  register: (data) => api.post('/auth/register', data),
  me: () => api.get('/auth/me'),
}

// No login needed (the token is sent if the user has one, which unlocks the phone number)
export const publicApi = {
  search: (params) => api.get('/public/pgs', {
    params,
    paramsSerializer: { indexes: null }, // amenities=wifi&amenities=ac (what Spring expects)
  }),
  detail: (id) => api.get(`/public/pgs/${id}`),
  reviews: (id, page = 0) => api.get(`/public/pgs/${id}/reviews`, { params: { page, size: 5 } }),
  reviewSummary: (id) => api.get(`/public/pgs/${id}/review-summary`),
  costEstimate: (id, params) => api.get(`/public/pgs/${id}/cost-estimate`, {
    params,
    paramsSerializer: { indexes: null }, // optional=1&optional=2 (what Spring expects)
  }),
}

export const bookingApi = {
  book: (data) => api.post('/tenant/bookings', data),             // INSTANT PGs
  request: (data) => api.post('/tenant/booking-requests', data),  // ON_REQUEST PGs
  mine: () => api.get('/tenant/booking-requests'),
  cancel: (id) => api.put(`/tenant/booking-requests/${id}/cancel`),
  // owner
  all: (pending, page = 0) => api.get('/owner/booking-requests', { params: { pending, page, size: 10 } }),
  pendingCount: () => api.get('/owner/booking-requests/pending-count'),
  decide: (id, data) => api.put(`/owner/booking-requests/${id}`, data),
}

export const visitApi = {
  slots: () => api.get('/public/visit-slots'),
  // tenant
  request: (data) => api.post('/tenant/visits', data),
  mine: () => api.get('/tenant/visits'),
  cancel: (id) => api.put(`/tenant/visits/${id}/cancel`),
  // owner
  all: () => api.get('/owner/visits'),
  pendingCount: () => api.get('/owner/visits/pending-count'),
  decide: (id, data) => api.put(`/owner/visits/${id}`, data),
}

export const chargeApi = {
  list: () => api.get('/owner/charges'),
  add: (data) => api.post('/owner/charges', data),
  update: (id, data) => api.put(`/owner/charges/${id}`, data),
  remove: (id) => api.delete(`/owner/charges/${id}`),
}

export const inspectionApi = {
  list: (tenantId) => api.get('/inspections', { params: { tenantId } }),
  create: (data) => api.post('/inspections', data),
  addItem: (id, data) => api.post(`/inspections/${id}/items`, data),
  removeItem: (id, itemId) => api.delete(`/inspections/${id}/items/${itemId}`),
  addPhoto: (id, form) => api.post(`/inspections/${id}/photos`, form), // FormData: file, caption, itemId
  confirm: (id) => api.post(`/inspections/${id}/confirm`),
  comparison: (tenantId) => api.get('/inspections/comparison', { params: { tenantId } }),
  photo: (photoId) => api.get(`/inspections/photos/${photoId}`, { responseType: 'blob' }),
}

export const reviewApi = {
  status: () => api.get('/tenant/review'),
  save: (data) => api.put('/tenant/review', data),
  remove: () => api.delete('/tenant/review'),
  // owner
  all: (page = 0) => api.get('/owner/reviews', { params: { page, size: 10 } }),
  reply: (id, data) => api.put(`/owner/reviews/${id}/reply`, data),
}

export const pgPhotoApi = {
  list: () => api.get('/owner/pg-photos'),
  add: (form) => api.post('/owner/pg-photos', form), // FormData: file, caption
  cover: (id) => api.put(`/owner/pg-photos/${id}/cover`),
  remove: (id) => api.delete(`/owner/pg-photos/${id}`),
}

export const pgApi = {
  get: () => api.get('/owner/pg'),
  create: (data) => api.post('/owner/pg', data),
  update: (data) => api.put('/owner/pg', data),
  dashboard: () => api.get('/owner/dashboard'),
  allForAdmin: () => api.get('/admin/pgs'),
}

export const roomApi = {
  all: () => api.get('/owner/rooms'),
  available: () => api.get('/owner/rooms/available'),
  create: (data) => api.post('/owner/rooms', data),
  update: (id, data) => api.put(`/owner/rooms/${id}`, data),
  maintenance: (id, on) => api.put(`/owner/rooms/${id}/maintenance`, null, { params: { underMaintenance: on } }),
}

export const tenantApi = {
  all: () => api.get('/owner/tenants'),
  search: (name) => api.get('/owner/tenants/search', { params: { name } }),
  create: (data) => api.post('/owner/tenants', data),
  allocate: (tenantId, roomId) => api.put(`/owner/tenants/${tenantId}/allocate-room/${roomId}`),
  checkout: (tenantId) => api.put(`/owner/tenants/${tenantId}/checkout`),
  myProfile: () => api.get('/tenant/profile'),
}

export const rentApi = {
  pending: () => api.get('/owner/rent/pending'),
  overdue: () => api.get('/owner/rent/overdue'),
  report: (month, year) => api.get('/owner/rent/report', { params: { month, year } }),
  generate: () => api.post('/owner/rent/generate'),
  markPaid: (id, notes) => api.put(`/owner/rent/${id}/mark-paid`, { notes }),
  myHistory: () => api.get('/tenant/rent'),
}

export const complaintApi = {
  // owner
  all: (page = 0) => api.get('/owner/complaints', { params: { page, size: 10 } }),
  byStatus: (status) => api.get(`/owner/complaints/status/${status}`),
  updateStatus: (id, data) => api.put(`/owner/complaints/${id}/status`, data),
  // tenant
  mine: () => api.get('/tenant/complaints'),
  raise: (data) => api.post('/tenant/complaints', data),
  close: (id) => api.put(`/tenant/complaints/${id}/close`),
}
