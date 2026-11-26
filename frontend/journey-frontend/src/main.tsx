import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import { refreshSession } from './auth/session'

// Restore the session from the refresh cookie before the first render; the app shows
// "Checking your session…" until this settles, then the sign-in page or the app.
void refreshSession()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
