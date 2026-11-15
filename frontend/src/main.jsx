import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import WakeBanner from './components/WakeBanner.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import { ToastProvider } from './context/ToastContext.jsx'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        <BrowserRouter>
            <ToastProvider>
                <AuthProvider>
                    <WakeBanner />
                    <App />
                </AuthProvider>
            </ToastProvider>
        </BrowserRouter>
    </React.StrictMode>
)
