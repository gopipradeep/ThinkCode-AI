// src/LoginPage.js

import React, { useState } from 'react';
import { 
    signInWithGoogle, 
    signInAsDemoUser 
} from './firebase'; 

// Inject the custom styles from your main file for consistency
const loginStyles = `
/* Reusing necessary styles from ThinkCodeAI.js */
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');
@import url('https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;600&display=swap');

:root {
  --font-sans: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", sans-serif;
  --shadow-color: rgba(0, 0, 0, 0.2);
}

.theme-light {
  --bg-primary: #fdf6e3;
  --text-primary: #586e75;
  --accent-primary: #268bd2;
  --accent-secondary: #d33682;
  --border-color: #d3cbb7;
}

.theme-dark {
  --bg-primary: #1a1b26;
  --text-primary: #c0caf5;
  --accent-primary: #7aa2f7;
  --accent-secondary: #bb9af7;
  --border-color: #3b3f51;
}

.login-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background-color: var(--bg-primary);
  color: var(--text-primary);
  font-family: var(--font-sans);
  transition: all 0.3s ease;
}

.login-box {
  padding: 3rem;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 0.75rem;
  box-shadow: 0 10px 25px -5px var(--shadow-color);
  text-align: center;
  width: 100%;
  max-width: 400px;
}

.login-box h2 {
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 2rem;
  background: linear-gradient(to right, var(--accent-secondary), var(--accent-primary));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.login-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 0.8rem 1.5rem;
  margin-bottom: 1rem;
  font-size: 1rem;
  font-weight: 600;
  border-radius: 0.375rem;
  cursor: pointer;
  transition: all 0.2s ease;
  gap: 0.75rem;
  border: none;
}

.google-button {
  background-color: #4285F4;
  color: white;
}

.google-button:hover {
  background-color: #3371e5;
}

.demo-button {
  background-color: var(--accent-secondary);
  color: white;
  border: 1px solid var(--accent-secondary);
}

.demo-button:hover {
  background-color: var(--accent-primary);
}

.error-message {
  color: #ef4444;
  margin-top: 1rem;
  font-size: 0.875rem;
}

.loading-spinner {
    border: 4px solid var(--border-color);
    border-top: 4px solid white;
    border-radius: 50%;
    width: 16px;
    height: 16px;
    animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
`;

// Icon Components 
const GoogleIcon = () => (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="white" xmlns="http://www.w3.org/2000/svg">
        <path d="M22.56 12.25c0-.78-.07-1.55-.2-2.3H12v4.39h5.84a5.6 5.6 0 01-2.43 3.65v2.81h3.61c2.1-1.95 3.31-4.78 3.31-8.55z" fill="#FFFFFF"/>
        <path d="M12 23c3.04 0 5.57-1.01 7.42-2.75l-3.61-2.81c-.96.64-2.17 1.02-3.81 1.02-2.95 0-5.46-1.99-6.38-4.72H1.97v2.89A11.97 11.97 0 0012 23z" fill="#FFFFFF"/>
        <path d="M5.62 13.99c-.27-.79-.42-1.63-.42-2.49s.15-1.7.42-2.49V5.72H1.97a11.97 11.97 0 000 12.56l3.65-2.89z" fill="#FFFFFF"/>
        <path d="M12 4.67c1.64 0 3.12.56 4.29 1.66l3.22-3.22A11.66 11.66 0 0012 1.09c-3.04 0-5.57 1.01-6.38 4.72h3.61c.92-2.73 3.43-4.72 6.38-4.72z" fill="#FFFFFF"/>
    </svg>
);

const DemoUserIcon = () => (
    <svg className="icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{color: 'white'}}>
        <path d="M12 2c-.9 0-1.8.3-2.5 1S8 5.1 8 6v1c0 1.1.9 2 2 2h4c1.1 0 2-.9 2-2V6c0-.9-.3-1.8-1-2.5S12.9 2 12 2z"></path>
        <path d="M12 10v11"></path>
        <path d="M16 16l-4 4-4-4"></path>
    </svg>
);

// MODIFIED: No onLoginSuccess prop
function LoginPage() {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const theme = 'dark'; 

    const handleLogin = async (loginFunction) => {
        if (loading) return;
        setLoading(true);
        setError(null);
        try {
            // Initiate the login. The App.js listener handles state change.
            await loginFunction(); 
            
            // Do not set loading=false on success; let App.js unmount this component.

        } catch (err) {
            console.error("Login failed:", err);
            // On error or popup closure, reset loading state in the LoginPage locally.
            const errorMessage = err.code === 'auth/popup-closed-by-user' ? 
                "Authentication cancelled or pop-up blocked." : 
                "Login failed. Please check your connection or try again.";
            setError(errorMessage);
            setLoading(false); 
        }
    };

    return (
        <div className={`login-container theme-${theme}`}>
            <style>{loginStyles}</style>
            <div className="login-box">
                <h2>ThinkCode AI</h2>
                
                <button 
                    className="login-button google-button"
                    onClick={() => handleLogin(signInWithGoogle)}
                    disabled={loading}
                >
                    {loading ? <div className="loading-spinner"></div> : <GoogleIcon />}
                    <span>Sign in with Google</span>
                </button>
                
                <button 
                    className="login-button demo-button"
                    onClick={() => handleLogin(signInAsDemoUser)}
                    disabled={loading}
                >
                    {loading ? <div className="loading-spinner"></div> : <DemoUserIcon />}
                    <span>Continue as Demo User</span>
                </button>

                {error && <p className="error-message">{error}</p>}
            </div>
        </div>
    );
}

export default LoginPage;
