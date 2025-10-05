// src/App.js
import React, { useState, useEffect } from 'react';
import ThinkCodeAI from './ThinkCodeAI';
import LoginPage from './LoginPage';
import { auth, onAuthStateChanged, signOut } from './firebase';

function App() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const handleLoginSuccess = (currentUser) => {
    // If the login result gives us a user, set it.
    if (currentUser) {
      setUser(currentUser);
    }
    // Always set loading to false to force the App to render past the loading screen.
    setLoading(false);
  };

  useEffect(() => {
    // This remains the official listener for initial hydration and external state changes (like token expiry).
    const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
      // Reverting the explicit photoURL sanitization here, letting ThinkCodeAI handle rendering
      setUser(currentUser);
      setLoading(false); // This resolves the loading state on initial load/refresh.
    });

    return () => unsubscribe();
  }, []);

  const handleLogout = async () => {
    try {
      await signOut(auth);
    } catch (error) {
      console.error("Logout error:", error);
      // NOTE: Using a custom modal/message box instead of alert()
      // alert("Failed to log out. Please try again.");
      console.error("Failed to log out. Please try again.");
    }
  };

  if (loading) {
    // Show a loading screen while checking auth status
    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          height: '100vh',
          fontSize: '1.5rem',
          fontFamily: 'Inter, sans-serif',
        }}
      >
        Authenticating...
      </div>
    );
  }

  // Render login page if not authenticated
  if (!user) {
    return <LoginPage onLoginSuccess={handleLoginSuccess} />;
  }

  // Render main application if authenticated
  return (
    <>
      <ThinkCodeAI 
        onLogout={handleLogout} 
        user={user}
        // Pass user.uid for persistence logic
        uid={user.uid} 
      />
    </>
  );
}

export default App;
