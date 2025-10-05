// src/firebase.js

import { initializeApp } from "firebase/app";
import { 
    getAuth, 
    GoogleAuthProvider, 
    signInWithPopup, 
    onAuthStateChanged,
    signOut,
    signInAnonymously
} from "firebase/auth";
// ADDED: Firestore imports
import { getFirestore, doc, setDoc, getDoc } from "firebase/firestore";

// Your provided Firebase configuration
const firebaseConfig = {
    apiKey: "AIzaSyBik-2QJpwRFSo5dERX0qnqbo4CpK3X3g4",
    authDomain: "major-project-43009.firebaseapp.com",
    projectId: "major-project-43009",
    storageBucket: "major-project-43009.firebasestorage.app",
    messagingSenderId: "8162221082",
    appId: "1:8162221082:web:2fc603d9b66e88ecfbc5a3",
    measurementId: "G-PWDYN079Q0"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);

// Initialize Firebase Authentication and get a reference to the service
const auth = getAuth(app);
const googleProvider = new GoogleAuthProvider();

// ADDED: Initialize Firestore
const db = getFirestore(app);

// Define and export the sign-in helper functions explicitly (FIXED EXPORTS)
const signInWithGoogle = () => signInWithPopup(auth, googleProvider);
const signInAsDemoUser = () => signInAnonymously(auth);

// ADDED: Firestore functions for code persistence

/**
 * Saves the most recent executed code and language to Firestore.
 * We use the user's UID as the document ID for simplicity (one record per user).
 */
const saveRecentCode = async (uid, code, language) => {
    if (!uid) return;
    try {
        await setDoc(doc(db, "recentCode", uid), {
            code: code, 
            language: language,
            timestamp: new Date().toISOString()
        });
        console.log("Recent code saved successfully for user:", uid);
    } catch (e) {
        console.error("Error saving recent code: ", e);
    }
};

/**
 * Retrieves the most recent executed code and language from Firestore.
 */
const loadRecentCode = async (uid) => {
    if (!uid) return null;
    try {
        const docRef = doc(db, "recentCode", uid);
        const docSnap = await getDoc(docRef);

        if (docSnap.exists()) {
            console.log("Recent code loaded.");
            return docSnap.data();
        } else {
            return null; // Document does not exist
        }
    } catch (e) {
        console.error("Error loading recent code: ", e);
        return null;
    }
};


// Export the necessary functions
export { 
    auth, 
    onAuthStateChanged, 
    signOut,
    signInWithGoogle,      
    signInAsDemoUser,
    // EXPORTED new functions
    saveRecentCode,
    loadRecentCode
};
