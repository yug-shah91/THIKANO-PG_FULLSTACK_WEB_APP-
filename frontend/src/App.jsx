import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth, homeFor } from './context/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import { Loader } from './components/UI'
import Layout from './components/Layout'
import PublicLayout from './components/PublicLayout'
import Home from './pages/public/Home'
import PgList from './pages/public/PgList'
import PgDetail from './pages/public/PgDetail'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/owner/Dashboard'
import MyPG from './pages/owner/MyPG'
import Rooms from './pages/owner/Rooms'
import Tenants from './pages/owner/Tenants'
import Rent from './pages/owner/Rent'
import OwnerComplaints from './pages/owner/Complaints'
import TenantProfile from './pages/tenant/Profile'
import TenantRent from './pages/tenant/Rent'
import TenantComplaints from './pages/tenant/Complaints'
import OwnerInspections from './pages/owner/Inspections'
import TenantInspections from './pages/tenant/Inspections'
import OwnerBookings from './pages/owner/Bookings'
import TenantBooking from './pages/tenant/Booking'
import OwnerPhotos from './pages/owner/Photos'
import OwnerReviews from './pages/owner/Reviews'
import OwnerCharges from './pages/owner/Charges'
import OwnerVisits from './pages/owner/Visits'
import TenantVisits from './pages/tenant/Visits'
import AllPGs from './pages/admin/AllPGs'

export default function App() {
  const { user, checking } = useAuth()

  if (checking) return <Loader />

  return (
    <Routes>
      <Route path="/login" element={user ? <Navigate to={homeFor(user.role)} /> : <Login />} />
      <Route path="/register" element={user ? <Navigate to={homeFor(user.role)} /> : <Register />} />

      {/* Public pages: anyone can look, only booking needs a tenant login */}
      <Route element={<PublicLayout />}>
        <Route path="/" element={<Home />} />
        <Route path="/pgs" element={<PgList />} />
        <Route path="/pgs/:id" element={<PgDetail />} />
      </Route>

      {/* Owner pages */}
      <Route path="/owner" element={<ProtectedRoute role="OWNER"><Layout /></ProtectedRoute>}>
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="pg" element={<MyPG />} />
        <Route path="rooms" element={<Rooms />} />
        <Route path="charges" element={<OwnerCharges />} />
        <Route path="photos" element={<OwnerPhotos />} />
        <Route path="reviews" element={<OwnerReviews />} />
        <Route path="tenants" element={<Tenants />} />
        <Route path="tenants/:tenantId/report" element={<OwnerInspections />} />
        <Route path="rent" element={<Rent />} />
        <Route path="bookings" element={<OwnerBookings />} />
        <Route path="visits" element={<OwnerVisits />} />
        <Route path="complaints" element={<OwnerComplaints />} />
      </Route>

      {/* Tenant pages */}
      <Route path="/tenant" element={<ProtectedRoute role="TENANT"><Layout /></ProtectedRoute>}>
        <Route path="profile" element={<TenantProfile />} />
        <Route path="booking" element={<TenantBooking />} />
        <Route path="visits" element={<TenantVisits />} />
        <Route path="report" element={<TenantInspections />} />
        <Route path="rent" element={<TenantRent />} />
        <Route path="complaints" element={<TenantComplaints />} />
      </Route>

      {/* Admin pages */}
      <Route path="/admin" element={<ProtectedRoute role="SUPER_ADMIN"><Layout /></ProtectedRoute>}>
        <Route path="pgs" element={<AllPGs />} />
      </Route>

      <Route path="*" element={<Navigate to={user ? homeFor(user.role) : '/'} />} />
    </Routes>
  )
}
