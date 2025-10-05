import React, { useState, useEffect, useRef } from 'react';
import DOMPurify from 'dompurify';
// ADDED: Import Firebase persistence functions
import { saveRecentCode, loadRecentCode } from './firebase'; 

// Enhanced CSS
const styles = `
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');
@import url('https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;600&display=swap');

:root {
  --font-sans: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", sans-serif;
  --font-mono: 'JetBrains Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
  --shadow-color: rgba(0, 0, 0, 0.2);
}

.theme-light {
  --bg-primary: #fdf6e3;
  --bg-secondary: #f5efdc;
  --bg-tertiary: #eee8d5;
  --border-color: #d3cbb7;
  --text-primary: #586e75;
  --text-secondary: #657b83;
  --accent-primary: #268bd2;
  --accent-secondary: #d33682;
  --cursor-color: #268bd2;
}

.theme-dark {
  --bg-primary: #1a1b26;
  --bg-secondary: #24283b;
  --bg-tertiary: #414868;
  --border-color: #3b3f51;
  --text-primary: #c0caf5;
  --text-secondary: #a9b1d6;
  --accent-primary: #7aa2f7;
  --accent-secondary: #bb9af7;
  --cursor-color: #7aa2f7;
}

.codesage-container {
  background-color: var(--bg-primary);
  color: var(--text-primary);
  font-family: var(--font-sans);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 1.5rem;
  box-sizing: border-box;
  transition: all 0.3s ease;
}

.codesage-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 1rem;
  margin-bottom: 1rem;
  border-bottom: 1px solid var(--border-color);
}

.codesage-logo {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.codesage-logo h1 {
  font-size: 1.75rem;
  font-weight: 700;
  background: linear-gradient(to right, var(--accent-secondary), var(--accent-primary));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0;
}

.codesage-controls {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.codesage-main {
  flex-grow: 1;
  display: flex;
  gap: 0;
  min-height: 0;
}

.panel {
  display: flex;
  flex-direction: column;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 0.5rem;
  overflow: hidden;
  box-shadow: 0 10px 25px -5px var(--shadow-color);
  transition: all 0.3s ease;
}

.editor-panel { flex-shrink: 0; }
.output-panel { flex-grow: 1; position: relative; }

.output-header {
  background-color: var(--bg-secondary);
  padding: 0;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 48px;
}

.output-controls {
  padding-right: 0.5rem;
  display: flex;
  gap: 0.25rem;
}

.output-controls button {
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 0.5rem;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.output-controls button:hover {
  background-color: var(--bg-tertiary);
  color: var(--text-primary);
}

.terminal-console {
  flex-grow: 1;
  padding: 1rem;
  font-family: var(--font-mono);
  font-size: 0.9rem;
  line-height: 1.4;
  overflow-y: auto;
  background-color: var(--bg-primary);
  cursor: text;
  position: relative;
  min-height: 200px;
}

.terminal-console:focus {
  outline: none;
}

.terminal-output {
  white-space: pre-wrap;
  word-wrap: break-word;
  color: var(--text-primary);
  margin: 0;
  padding: 0;
}

.inline-input-container {
  display: inline-block;
  position: relative;
}

.inline-input {
  background: transparent;
  border: none;
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-size: 0.9rem;
  outline: none;
  line-height: 1.4;
  padding: 0;
  margin: 0;
  min-width: 20px;
  max-width: 400px;
  caret-color: var(--cursor-color) !important;
}

.inline-input::selection {
  background-color: rgba(122, 162, 247, 0.3);
}

.inline-input:focus {
  outline: none;
  background-color: rgba(122, 162, 247, 0.05);
  border-radius: 2px;
  padding: 0 2px;
  margin: 0 -2px;
  caret-color: var(--cursor-color) !important;
}

.theme-dark .inline-input {
  caret-color: #7aa2f7 !important;
}

.theme-light .inline-input {
  caret-color: #268bd2 !important;
}

.resizer {
  flex-shrink: 0;
  width: 10px;
  cursor: col-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-secondary);
  border-left: 1px solid var(--border-color);
  border-right: 1px solid var(--border-color);
}

.resizer:hover::before {
  content: '';
  width: 3px;
  height: 40px;
  background-color: var(--accent-primary);
  border-radius: 3px;
}

.styled-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.6rem 1.2rem;
  font-weight: 600;
  color: white;
  background: linear-gradient(135deg, var(--accent-secondary), var(--accent-primary));
  border: none;
  border-radius: 0.375rem;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px -2px rgba(187, 154, 247, 0.3);
  font-size: 0.875rem;
}

.styled-button:hover { 
  transform: translateY(-2px); 
  box-shadow: 0 6px 20px -2px rgba(122, 162, 247, 0.4); 
}

.styled-button:disabled { 
  background: var(--bg-tertiary); 
  color: var(--text-secondary); 
  cursor: not-allowed; 
  box-shadow: none; 
  transform: translateY(0);
}

.stop-button {
  background: linear-gradient(135deg, #ef4444, #dc2626) !important;
}

.language-selector { position: relative; display: inline-block; }

.language-button { 
  display: inline-flex; 
  justify-content: center; 
  align-items: center; 
  border-radius: 0.375rem; 
  border: 1px solid var(--border-color); 
  padding: 0.6rem 1rem; 
  background-color: var(--bg-secondary); 
  font-size: 0.875rem; 
  font-weight: 500; 
  color: var(--text-primary); 
  cursor: pointer; 
  transition: all 0.2s ease;
  min-width: 120px;
}

.language-button:hover { 
  background-color: var(--bg-tertiary); 
  border-color: var(--accent-primary); 
}

/* NEW: Style for disabled language button */
.language-button[disabled] {
  cursor: not-allowed;
  opacity: 0.6;
  background-color: var(--bg-tertiary);
}
.language-button[disabled]:hover {
  background-color: var(--bg-tertiary); /* Maintain disabled background on hover */
  border-color: var(--border-color); /* Maintain default border on hover */
  color: var(--text-secondary);
}


.language-dropdown { 
  position: absolute; 
  right: 0; 
  top: 100%;
  margin-top: 0.5rem; 
  width: 14rem; 
  border-radius: 0.375rem; 
  background-color: var(--bg-secondary); 
  box-shadow: 0 10px 25px -5px var(--shadow-color); 
  z-index: 1000; 
  border: 1px solid var(--border-color); 
  overflow: hidden; 
  padding: 0.25rem; 
  max-height: 400px; 
  overflow-y: auto; 
}

.language-dropdown-item { 
  display: block; 
  width: 100%; 
  text-align: left; 
  padding: 0.5rem 1rem; 
  font-size: 0.875rem; 
  color: var(--text-secondary); 
  background: none; 
  border: none; 
  cursor: pointer; 
  border-radius: 0.25rem; 
  transition: all 0.2s ease;
}

.language-dropdown-item:hover { 
  background-color: var(--accent-primary); 
  color: white; 
}

.language-dropdown-item.selected { 
  background-color: var(--accent-secondary); 
  color: white; 
  font-weight: 600; 
}

.execution-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  font-size: 0.75rem;
  color: var(--text-secondary);
  font-weight: 500;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  animation: pulse 1.5s ease-in-out infinite;
}

/* AI TEXT DISPLAY ENHANCED STYLING */
.ai-text-display h4 {
    color: var(--accent-primary);
    font-size: 1.1rem;
    font-weight: 700;
    margin-top: 1.5rem;
    margin-bottom: 0.75rem;
    padding-bottom: 0.5rem;
    border-bottom: 2px solid var(--border-color);
    display: flex;
    align-items: center;
    gap: 0.5rem;
}

.ai-text-display h4:first-child {
    margin-top: 0;
}

.ai-text-display h4::before {
    content: '▸';
    color: var(--accent-secondary);
    font-size: 1.2rem;
    font-weight: bold;
}

.ai-text-display p {
    color: var(--text-primary);
    margin: 0.75rem 0;
    line-height: 1.7;
    text-align: justify;
}

.ai-text-display code {
    background: linear-gradient(135deg, 
        rgba(122, 162, 247, 0.15), 
        rgba(187, 154, 247, 0.15));
    color: var(--accent-primary);
    padding: 0.25rem 0.5rem;
    border-radius: 0.375rem;
    font-family: var(--font-mono);
    font-size: 0.9em;
    font-weight: 600;
    border: 1px solid var(--border-color);
    display: inline-block;
    margin: 0.25rem 0;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
    transition: all 0.2s ease;
}

.ai-text-display code:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 8px rgba(122, 162, 247, 0.2);
}

.ai-text-display pre {
    background-color: var(--bg-secondary);
    padding: 1rem;
    border-radius: 0.5rem;
    overflow-x: auto;
    border-left: 4px solid var(--accent-primary);
    margin: 1rem 0;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.ai-text-display pre code {
    background: none;
    border: none;
    padding: 0;
    margin: 0;
    box-shadow: none;
    display: block;
}

.ai-text-display strong {
    color: var(--accent-secondary);
    font-weight: 600;
}

.theme-light .ai-text-display code {
    background: linear-gradient(135deg, 
        rgba(38, 139, 210, 0.12), 
        rgba(211, 54, 130, 0.12));
    color: #268bd2;
}

.theme-light .ai-text-display h4 {
    color: #268bd2;
}

.theme-light .ai-text-display h4::before {
    color: #d33682;
}

.ai-text-display::-webkit-scrollbar {
    width: 10px;
}

.ai-text-display::-webkit-scrollbar-track {
    background: var(--bg-secondary);
    border-radius: 5px;
}

.ai-text-display::-webkit-scrollbar-thumb {
    background: var(--accent-primary);
    border-radius: 5px;
}

.ai-text-display::-webkit-scrollbar-thumb:hover {
    background: var(--accent-secondary);
}


.status-running { background-color: #f59e0b; }
.status-waiting { background-color: #22c55e; }
.status-completed { background-color: #6b7280; }
.status-ai-processing { background-color: #f59e0b; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.execution-status.running {
  background: linear-gradient(90deg, var(--bg-secondary) 0%, rgba(122, 162, 247, 0.1) 50%, var(--bg-secondary) 100%);
  background-size: 200% 100%;
  animation: shimmer 2s infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.icon { width: 1.25rem; height: 1.25rem; flex-shrink: 0; }
.logo-icon { width: 2.25rem; height: 2.25rem; }

.theme-toggle { 
  background: none; 
  border: 1px solid var(--border-color); 
  color: var(--text-secondary); 
  cursor: pointer; 
  padding: 0.5rem; 
  border-radius: 50%; 
  display: flex; 
  align-items: center; 
  justify-content: center; 
  transition: all 0.3s ease;
  width: 40px;
  height: 40px;
}

.theme-toggle:hover { 
  color: var(--accent-primary); 
  border-color: var(--accent-primary); 
  transform: rotate(15deg); 
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
  color: var(--text-secondary);
  padding: 0.5rem;
  border-radius: 0.25rem;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
}

.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-connected { background-color: #22c55e; }
.status-disconnected { background-color: #ef4444; }
.status-connecting { background-color: #f59e0b; animation: pulse 1s infinite; }

/* NEW TAB STYLES */
.tab-bar {
    display: flex;
    border-bottom: 1px solid var(--border-color);
    background-color: var(--bg-secondary);
}

.tab-button {
    padding: 0.75rem 1rem;
    font-weight: 500;
    color: var(--text-secondary);
    background: none;
    border: none;
    cursor: pointer;
    transition: color 0.2s, background-color 0.2s;
    border-radius: 0;
    border-right: 1px solid var(--border-color);
    border-top: 3px solid transparent;
}

.tab-button:last-child {
    border-right: none;
}

.tab-button.active {
    color: var(--accent-primary);
    background-color: var(--bg-primary);
    border-top: 3px solid var(--accent-primary);
    box-shadow: inset 0 1px 0 var(--bg-primary);
}

.tab-content-container {
    flex-grow: 1;
    min-height: 0;
}

/* ACCOUNT DROPDOWN STYLES MOVED FROM App.js */
.account-dropdown-container {
    position: relative;
    display: inline-block;
}

.account-icon {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: transform 0.2s ease, box-shadow 0.2s ease;
    color: white;
    font-weight: 600;
    font-size: 14px;
    user-select: none;
    border: 2px solid rgba(255, 255, 255, 0.2);
}

.account-icon:hover {
    transform: scale(1.05);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.account-icon:active {
    transform: scale(0.98);
}

.dropdown-menu {
    position: absolute;
    top: 45px;
    right: 0;
    background: white;
    border-radius: 8px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
    min-width: 180px;
    z-index: 1000;
    overflow: hidden;
    opacity: 0;
    transform: translateY(-10px);
    pointer-events: none;
    transition: opacity 0.2s ease, transform 0.2s ease;
}

.dropdown-menu.show {
    opacity: 1;
    transform: translateY(0);
    pointer-events: all;
}

.theme-dark .dropdown-menu {
    background: #2a2b36;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}

.dropdown-header {
    padding: 12px 16px;
    border-bottom: 1px solid #e5e7eb;
    font-size: 12px;
    color: #6b7280;
}

.theme-dark .dropdown-header {
    border-bottom-color: #3b3f51;
    color: #9ca3af;
}

.dropdown-item {
    padding: 10px 16px;
    cursor: pointer;
    transition: background-color 0.15s ease;
    color: #374151;
    font-size: 14px;
    display: flex;
    align-items: center;
    gap: 8px;
    border: none;
    background: none;
    width: 100%;
    text-align: left;
}

.dropdown-item:hover {
    background-color: #f3f4f6;
}

.theme-dark .dropdown-item {
    color: #c0caf5;
}

.theme-dark .dropdown-item:hover {
    background-color: #3b3f51;
}

.logout-icon, .recent-code-icon {
    width: 16px;
    height: 16px;
}

/* NEW NOTIFICATION STYLES */
.notification-container {
    position: fixed;
    bottom: 2rem; /* Anchored to bottom */
    right: 2rem; /* Anchored to right */
    z-index: 50;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    pointer-events: none; /* Allows clicks to pass through if empty area */
}

.notification-box {
    padding: 0.75rem 1rem;
    background-color: var(--accent-primary);
    color: white;
    border-radius: 0.5rem;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
    opacity: 0;
    transform: translateY(10px);
    transition: opacity 0.3s ease, transform 0.3s ease;
    min-width: 250px;
    font-size: 0.9rem;
    font-weight: 500;
}

.notification-box.show {
    opacity: 1;
    transform: translateY(0);
}

.notification-box.error {
    background-color: #ef4444; /* Red for errors */
}

.notification-box.success {
    background-color: #22c55e; /* Green for success */
}
`;

// Icon Components 
const CodeSageLogo = () => (
    <svg className="logo-icon" viewBox="0 0 128 128" xmlns="http://www.w3.org/2000/svg">
        <defs>
            <linearGradient id="grad1" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" style={{stopColor:'var(--accent-secondary)', stopOpacity:1}} />
                <stop offset="100%" style={{stopColor:'var(--accent-primary)', stopOpacity:1}} />
            </linearGradient>
        </defs>
        <path fill="url(#grad1)" d="M42.6,128,0,106.7V21.3L42.6,0,85.3,21.3V64l-10.7-5.9V27.2L42.6,10.7,10.7,27.2V100.8l31.9,15.9,32-15.9V74.7l10.7,5.9V106.7Z"/>
    </svg>
);

const PlayIcon = () => (
    <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <polygon points="5 3 19 12 5 21 5 3"></polygon>
    </svg>
);

const StopIcon = () => (
    <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="6" y="6" width="12" height="12" rx="2" ry="2"></rect>
    </svg>
);

const ChevronDownIcon = () => (
    <svg className="icon" style={{ marginLeft: '0.25rem' }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="6 9 12 15 18 9"></polyline>
    </svg>
);

const SunIcon = () => (
    <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="5"></circle>
        <line x1="12" y1="1" x2="12" y2="3"></line>
        <line x1="12" y1="21" x2="12" y2="23"></line>
        <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line>
        <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line>
        <line x1="1" y1="12" x2="3" y2="12"></line>
        <line x1="21" y1="12" x2="23" y2="12"></line>
        <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line>
        <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line>
    </svg>
);

const MoonIcon = () => (
    <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>
    </svg>
);

const CopyIcon = () => (
    <svg className="icon" style={{width: '1rem', height: '1rem'}} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
    </svg>
);

const ClearIcon = () => (
    <svg className="icon" style={{width: '1rem', height: '1rem'}} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <line x1="18" y1="6" x2="6" y2="18"></line>
        <line x1="6" y1="6" x2="18" y2="18"></line>
    </svg>
);

// ICONS FOR AI FEATURES
const SearchIcon = () => (
    <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="11" cy="11" r="8"></circle>
        <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
        <path d="M10 13l2-2m-2 0l2 2" strokeWidth="1.5"></path>
    </svg>
);

const BookOpenIcon = () => (
    <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"></path>
        <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"></path>
    </svg>
);

// Monaco Editor Component (CDN integration)
const MonacoEditor = ({ language, theme, value, onChange }) => {
    const editorRef = useRef(null);
    const monacoRef = useRef(null);
    const containerRef = useRef(null);
    const [isMonacoLoaded, setIsMonacoLoaded] = useState(false);

    const initializeEditor = (monacoInstance) => {
        if (!monacoInstance || !containerRef.current) return;
        
        // Dispose of old editor instance if it exists before creating a new one
        if (editorRef.current) {
            editorRef.current.dispose();
        }

        editorRef.current = monacoInstance.editor.create(containerRef.current, {
            value: value,
            language: language,
            theme: theme === 'dark' ? 'vs-dark' : 'vs',
            fontSize: 14, 
            minimap: { enabled: false }, 
            scrollBeyondLastLine: false,
            automaticLayout: true,
            wordWrap: "on",
            lineNumbers: "on",
            roundedSelection: false,
            scrollbar: {
                vertical: "visible",
                horizontal: "visible"
            }
        });

        // Attach change listener
        editorRef.current.onDidChangeModelContent(() => {
            onChange(editorRef.current.getValue());
        });
    };

    // Load Monaco script only once and set the global reference
    useEffect(() => {
        if (window.monaco) {
            monacoRef.current = window.monaco;
            setIsMonacoLoaded(true);
            return;
        }

        if (!window.__monacoLoaderInjected) {
            window.__monacoLoaderInjected = true;
            const script = document.createElement('script');
            script.src = 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs/loader.min.js';
            script.onload = () => {
                window.require.config({ paths: { vs: 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs' } });
                window.require(['vs/editor/editor.main'], () => {
                    monacoRef.current = window.monaco;
                    setIsMonacoLoaded(true);
                    // Force a re-render to pick up the change
                });
            };
            document.head.appendChild(script);
        }
    }, []);

    // Effect to create the editor instance when Monaco is loaded AND the component (ThinkCodeAI) mounts/re-mounts
    useEffect(() => {
        if (isMonacoLoaded && monacoRef.current) {
            initializeEditor(monacoRef.current);
        }

        return () => {
            // CRITICAL FIX: Dispose of the editor instance on component unmount (e.g., on logout)
            // This prevents the editor from holding onto memory or causing issues on remount (relogin).
            if (editorRef.current) {
                editorRef.current.dispose();
                editorRef.current = null;
            }
        };
    // Re-run whenever monaco is loaded, or its core props change
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isMonacoLoaded, language, theme]); 
    
    // Update value when external state changes
    useEffect(() => {
        if (editorRef.current && editorRef.current.getValue() !== value) {
            editorRef.current.getModel().setValue(value);
        }
    }, [value]);

    // Update language or theme when external state changes
    useEffect(() => {
        if (editorRef.current && monacoRef.current) {
            monacoRef.current.editor.setModelLanguage(editorRef.current.getModel(), language);
            monacoRef.current.editor.setTheme(theme === 'dark' ? 'vs-dark' : 'vs');
            // FIX: layout() call should be deferred to prevent ResizeObserver loop warning
            requestAnimationFrame(() => {
                // FIX: Added null check here to prevent calling layout() on a disposed editor during logout
                if (editorRef.current) {
                    editorRef.current.layout(); 
                }
            });
        }
    }, [language, theme]);
    
    // Ensure layout adjusts on container resize
    useEffect(() => {
        const resizeObserver = new ResizeObserver(() => {
            // FIX: layout() call must be deferred to prevent ResizeObserver loop warning
            requestAnimationFrame(() => {
                // FIX: Added null check here to prevent calling layout() on a disposed editor during logout
                if (editorRef.current) {
                    editorRef.current.layout();
                }
            });
        });
        if (containerRef.current) {
            resizeObserver.observe(containerRef.current);
        }
        return () => {
            resizeObserver.disconnect();
        };
    }, []);

    return <div ref={containerRef} style={{ height: '100%', width: '100%' }} />;
};

// Language Selector Component (MODIFIED to accept isDisabled)
const LanguageSelector = ({ language, setLanguage, languages, isDisabled }) => {
    const [isOpen, setIsOpen] = useState(false);
    const dropdownRef = useRef(null);
    const selectedLanguage = languages.find(l => l.value === language);

    useEffect(() => {
        const handleClickOutside = (event) => {
            // Only close if we are open AND the target is outside the dropdown container
            if (isOpen && dropdownRef.current && !dropdownRef.current.contains(event.target)) {
                setIsOpen(false);
            }
        };
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, [isOpen]);

    const toggleDropdown = () => {
        if (!isDisabled) {
            setIsOpen(!isOpen);
        }
    };

    return (
        <div className="language-selector" ref={dropdownRef}>
            <button 
                type="button" 
                className="language-button" 
                onClick={toggleDropdown} 
                disabled={isDisabled} // Disable the main button
            >
                {selectedLanguage?.label || 'Select Language'} <ChevronDownIcon />
            </button>
            {/* Conditionally render the dropdown menu only if not disabled */}
            {isOpen && !isDisabled && (
                <div className="language-dropdown">
                    {languages.map((lang) => (
                        <button 
                            key={lang.value} 
                            className={`language-dropdown-item ${lang.value === language ? 'selected' : ''}`} 
                            onClick={() => { 
                                setLanguage(lang.value); 
                                setIsOpen(false); 
                            }}
                        >
                            {lang.label}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};

// Inline Terminal Component (adapted for use in a single tab)
const InlineTerminal = ({ output, isWaitingForInput, onSendInput }) => {
    const [currentInput, setCurrentInput] = useState("");
    const consoleRef = useRef(null);
    const inputRef = useRef(null);

    useEffect(() => {
        if (consoleRef.current) {
            consoleRef.current.scrollTop = consoleRef.current.scrollHeight;
        }
    }, [output]);

    useEffect(() => {
        if (isWaitingForInput && inputRef.current) {
            inputRef.current.focus();
        }
    }, [isWaitingForInput]);

    const handleInputSubmit = (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            if (currentInput.trim() || currentInput === "") { // Allow empty line for pure input read
                onSendInput(currentInput);
                setCurrentInput("");
            }
        }
    };

    const handleConsoleClick = () => {
        if (isWaitingForInput && inputRef.current) {
            inputRef.current.focus();
        }
    };

    const handleInputChange = (e) => {
        const value = e.target.value;
        setCurrentInput(value);
        
        if (inputRef.current) {
            const canvas = document.createElement('canvas');
            const context = canvas.getContext('2d');
            // Use the same font styles as .terminal-console
            context.font = '0.9rem JetBrains Mono, monospace'; 
            const width = context.measureText(value || 'T').width;
            // Ensure input field is wide enough to see text
            inputRef.current.style.width = Math.max(20, width + 15) + 'px';
        }
    };

    return (
        <div 
            ref={consoleRef}
            className="terminal-console"
            onClick={handleConsoleClick}
            tabIndex={0}
            style={{height: '100%', boxSizing: 'border-box'}}
        >
            <div className="terminal-output">
                {output}
                {isWaitingForInput && (
                    <span className="inline-input-container">
                        <input
                            ref={inputRef}
                            type="text"
                            className="inline-input"
                            value={currentInput}
                            onChange={handleInputChange}
                            onKeyPress={handleInputSubmit}
                            autoFocus
                            style={{ width: '20px' }}
                            placeholder=""
                        />
                    </span>
                )}
            </div>
        </div>
    );
};


// AITextDisplay component for showing static AI results
const AITextDisplay = ({ content }) => {
    const [processedContent, setProcessedContent] = useState('');
    const contentRef = useRef(null);

    useEffect(() => {
        try {
            if (/<[a-z][\s\S]*>/i.test(content)) {
                const parser = new DOMParser();
                const doc = parser.parseFromString(content, 'text/html');

                const h4s = doc.querySelectorAll('h4');
                h4s.forEach(h4 => {
                    let next = h4.nextSibling;
                    while (next && (next.nodeType === 3 && !/\S/.test(next.nodeValue))) {
                        next = next.nextSibling;
                    }
                    if (next && next.tagName === 'CODE') {
                        const wrapper = doc.createElement('span');
                        wrapper.className = 'h4-code-wrapper';
                        h4.parentNode.insertBefore(wrapper, h4);
                        wrapper.appendChild(h4);
                        wrapper.appendChild(next);
                    }
                });
                setProcessedContent(doc.body.innerHTML);
            } else {
                setProcessedContent(content);
            }
        } catch (e) {
            setProcessedContent(content); // Fail gracefully
        }
    }, [content]);

    useEffect(() => {
        if (contentRef.current) {
            contentRef.current.scrollTop = contentRef.current.scrollHeight;
        }
    }, [processedContent]);

    return (
        <div
            ref={contentRef}
            className="ai-text-display"
            tabIndex={0}
            dangerouslySetInnerHTML={{ __html: processedContent }}
            style={{
                height: '100%',
                overflowY: 'auto',
                padding: '1.5rem',
                fontFamily: 'var(--font-sans)',
                fontSize: '0.95rem',
                lineHeight: '1.6',
                backgroundColor: 'var(--bg-primary)',
            }}
        />
    );
};

/**
 * NEW Component: Notification
 * Displays a temporary message
 */
const Notification = ({ message, type, onClose }) => {
    const [show, setShow] = useState(false);

    useEffect(() => {
        if (message) {
            // Show the notification immediately
            setShow(true);
            
            // Set a timeout to hide the notification
            const timer = setTimeout(() => {
                setShow(false);
                // Clear the message entirely after the fade out transition is complete
                const clearTimer = setTimeout(() => {
                    onClose();
                }, 300); // Must match CSS transition duration
                return () => clearTimeout(clearTimer);

            }, 4000); // Display time

            return () => {
                clearTimeout(timer);
                setShow(false);
            };
        } else {
            // Ensure it's hidden if message is null
            setShow(false);
        }
    }, [message, onClose]);

    if (!message) return null;

    return (
        <div className="notification-container">
            <div className={`notification-box ${show ? 'show' : ''} ${type}`}>
                {message}
            </div>
        </div>
    );
};


// Main Application Component
function ThinkCodeAI({ user, onLogout, uid }) {
    // LANGUAGE EXAMPLES (FIXED C#, C, C++, and Go I/O, PHP Global Scope)
    const languageExamples = {
        java: `import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.print("Enter your name: ");
        String name = scanner.nextLine();
        
        System.out.print("Enter your age: ");
        int age = scanner.nextInt();
        
        System.out.println("Hello " + name + ", you are " + age + " years old!");
        
        scanner.close();
    }
}`,
        python: `# Start coding with Python...\n\nname = input("Enter your name: ")\nage = input("Enter your age: ")\n\nprint(f"Hello {name}, you are {age} years old!")`,
        cpp: `#include <iostream>
#include <string>
using namespace std;

int main() {
    // Ensure standard output is not buffered
    ios_base::sync_with_stdio(false); 

    string name;
    int age;
    
    cout << "Enter your name: " << flush; // Added flush
    getline(cin, name);
    
    cout << "Enter your age: " << flush; // Added flush
    cin >> age;
    
    cout << "Hello " << name << ", you are " << age << " years old!" << endl;
    
    return 0;
}`,
        kotlin: `fun main() {
    print("Enter your name: ")
    val name = readLine()
    
    print("Enter your age: ")
    val age = readLine()?.toIntOrNull() ?: 0
    
    println("Hello $name, you are $age years old!")
    
    print("Enter a number: ")
    val number = readLine()?.toIntOrNull() ?: 0
    println("You entered: $number")
}`,
        // FIX: PHP code example updated to use global prefix (\) for functions when in a namespace.
        php: `<?php
// Save as compiler_test.php
namespace CompilerTest;

use Exception;

function factorial($n){
    if($n<=1) return 1;
    return $n * factorial($n-1);
}

// NOTE: Global functions must be prefixed with a backslash (\\) when inside a namespace.
\\file_put_contents("ct_php.json", \\json_encode(["msg"=>"php","unicode"=>"こんにちは"]));
$data = \\json_decode(\\file_get_contents("ct_php.json"), true);
echo "MSG: ".$data['msg']."\\n";

echo "FACT5: ".factorial(5)."\\n";

// simple TCP socket attempt to localhost: (non-fatal)
// This will fail because the sockets extension is usually not enabled in sandboxes,
// but it will fail gracefully because it uses the global prefix (\\).
$sock = @\\socket_create(AF_INET, SOCK_STREAM, SOL_TCP);
if($sock){
    $res = @\\socket_bind($sock, "127.0.0.1", 0);
    echo $res ? "SOCKET_BIND_OK\\n" : "SOCKET_BIND_FAIL\\n";
    \\socket_close($sock);
}

\\unlink("ct_php.json");
?>`,
        rust: `use std::io::{self, Write};

fn main() {
    print!("Enter your name: ");
    io::stdout().flush().unwrap();
    
    let mut name = String::new();
    io::stdin().read_line(&mut name).expect("Failed to read name");
    let name = name.trim();
    
    print!("Enter your age: ");
    io::stdout().flush().unwrap();
    
    let mut age = String::new();
    io::stdin().read_line(&mut age).expect("Failed to read age");
    let age: u32 = age.trim().parse().expect("Please enter a number");
    
    println!("Hello {}, you are {} years old!", name, age);
}`,
        go: `package main

import (
    "bufio"
    "fmt"
    "os"
    "strconv"
    "strings"
)

func main() {
    reader := bufio.NewReader(os.Stdin)
    
    fmt.Print("Enter your name: ")
    os.Stdout.Sync() // Added Sync
    name, _ := reader.ReadString('\\n')
    name = strings.TrimSpace(name)
    
    fmt.Print("Enter your age: ")
    os.Stdout.Sync() // Added Sync
    ageStr, _ := reader.ReadString('\\n')
    age, _ := strconv.Atoi(strings.TrimSpace(ageStr))
    
    fmt.Printf("Hello %s, you are %d years old!\\n", name, age)
}`,
        c: `#include <stdio.h>
#include <string.h>

int main() {
    char name[100];
    int age;
    
    printf("Enter your name: ");
    fflush(stdout); // Force immediate output
    
    if (fgets(name, sizeof(name), stdin)) {
        // Remove newline if present
        name[strcspn(name, "\\n")] = 0;
    }
    
    printf("Enter your age: ");
    fflush(stdout); // Force immediate output
    
    scanf("%d", &age);
    
    printf("Hello %s, you are %d years old!\\n", name, age);
    fflush(stdout); // Force final output
    
    printf("Program completed successfully!\\n");
    
    return 0;
}`,
        csharp: `using System;

class Program 
{
    static void Main() 
    {
        Console.Write("Enter your name: ");
        // Added robust null checks
        string name = Console.ReadLine() ?? ""; 
        
        Console.Write("Enter your age: ");
        string ageInput = Console.ReadLine() ?? "0";
        int age;
        if (!int.TryParse(ageInput, out age)) {
            age = 0;
        }
        
        Console.WriteLine($"Hello {name}, you are {age} years old!");
        
        Console.Write("Enter a number: ");
        string numberInput = Console.ReadLine() ?? "0";
        int number;
        if (!int.TryParse(numberInput, out number)) {
            number = 0;
        }
        Console.WriteLine($"You entered: {number}");
    }
}`,
        javascript: `const readline = require('readline');

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
});

console.log('Interactive JavaScript Program');

rl.question('Enter your name: ', (name) => {
    rl.question('Enter your age: ', (age) => {
        console.log(\`Hello \${name}, you are \${age} years old!\`);
        
        rl.question('Enter a number: ', (number) => {
            console.log(\`You entered: \${number}\`);
            
            // Proper cleanup and termination
            rl.close();
            if (process.stdin.readable) {
                process.stdin.destroy();
            }
            
            // Force exit after short delay
            setTimeout(() => {
                process.exit(0);
            }, 100);
        });
    });
});

// Handle cleanup on termination signals
process.on('SIGINT', () => {
    rl.close();
    process.exit(0);
});

process.on('SIGTERM', () => {
    rl.close();
    process.exit(0);
});`,
        ruby: `$stdout.sync = true  # Force immediate output
$stdin.sync = true   # Force immediate input

puts "Interactive Ruby Program"

print "Enter your name: "
$stdout.flush
name = gets.chomp

print "Enter your age: "
$stdout.flush  
age = gets.chomp.to_i

puts "Hello #{name}, you are #{age} years old!"

print "Enter a number: "
$stdout.flush
number = gets.chomp.to_i
puts "You entered: #{number}"

puts "Program completed successfully!"`
    };

    const [language, setLanguage] = useState("python");
    // Change initial state to a placeholder message for initial load
    const [code, setCode] = useState(languageExamples["python"]); 
    // Split output state into separate streams
    const [terminalOutput, setTerminalOutput] = useState("");
    const [analysisOutput, setAnalysisOutput] = useState("");
    const [explainOutput, setExplainOutput] = useState("");
    const [activeTab, setActiveTab] = useState('terminal'); // Default tab

    // FIX 1: Change currentCodeToSave from state to a mutable Ref
    const currentCodeToSaveRef = useRef({ code: '', language: '' });
    // NEW REF: Tracks if stop was initiated internally by loading new code
    const suppressStopMessageRef = useRef(false);


    const [isRunning, setIsRunning] = useState(false);
    const [isWaitingForInput, setIsWaitingForInput] = useState(false);
    const [executionStatus, setExecutionStatus] = useState("idle");
    const [editorWidth, setEditorWidth] = useState(65);
    const [theme, setTheme] = useState('dark');
    const [connectionStatus, setConnectionStatus] = useState('disconnected');
    const [isAIProcessing, setIsAIProcessing] = useState(false);
    
    // Notification State
    const [notification, setNotification] = useState({ message: null, type: 'success' });
    const setNotificationMessage = (message, type = 'success') => {
        setNotification({ message, type });
    };
    const clearNotification = () => setNotification({ message: null, type: 'success' });


    // State and Ref for the Account Dropdown
    const [showDropdown, setShowDropdown] = useState(false);
    const dropdownRef = useRef(null);
    
    const ws = useRef(null);
    const isResizing = useRef(null);
    let reconnectTimeout = useRef(null);
    let healthCheckInterval = useRef(null);


    // Updated Codespace URLs for Production Deployment
    const BACKEND_BASE_URL = 'https://redesigned-space-xylophone-q7r97969rrjc9r9q-8080.app.github.dev';
    const WS_URL = `wss://redesigned-space-xylophone-q7r97969rrjc9r9q-8080.app.github.dev/execute-ws`;

    // CLEAN 11 LANGUAGES
    const languages = [
        { value: 'java', label: 'Java', monacoLang: 'java' },
        { value: 'python', label: 'Python', monacoLang: 'python' },
        { value: 'cpp', label: 'C++', monacoLang: 'cpp' },
        { value: 'c', label: 'C', monacoLang: 'c' },
        { value: 'csharp', label: 'C#', monacoLang: 'csharp' },
        { value: 'go', label: 'Go', monacoLang: 'go' },
        { value: 'rust', label: 'Rust', monacoLang: 'rust' },
        { value: 'javascript', label: 'JavaScript', monacoLang: 'javascript' },
        { value: 'ruby', label: 'Ruby', monacoLang: 'ruby' },
        { value: 'php', label: 'PHP', monacoLang: 'php' },
        { value: 'kotlin', label: 'Kotlin', monacoLang: 'kotlin' },
    ];
    
    // Dropdown handlers
    const toggleDropdown = () => {
        setShowDropdown(!showDropdown);
    };

    // MODIFIED: handleRecentCode now loads code from Firebase
    const handleRecentCode = async () => {
        // 1. Immediately signal suppression and stop execution
        suppressStopMessageRef.current = true;
        stopExecution();
        
        setShowDropdown(false);
        clearNotification();

        // Use a short delay before setting the "loading" state to guarantee animation reset
        await new Promise(resolve => setTimeout(resolve, 50)); 
        
        setNotificationMessage("Attempting to load recent code...", "success");

        if (!uid) {
            setNotificationMessage("Cannot load recent code: You must be logged in.", "error");
            // Reset suppression flag in case stopExecution failed immediately
            suppressStopMessageRef.current = false;
            return;
        }

        const savedData = await loadRecentCode(uid);
        
        // Clear terminal output first (the suppress flag will handle messages from stopExecution)
        setTerminalOutput("");
        setAnalysisOutput("");
        setExplainOutput("");
        setActiveTab('terminal');

        if (savedData && savedData.code) {
            setCode(savedData.code);
            
            // Check if the loaded language is valid before setting
            if (languages.some(l => l.value === savedData.language)) {
                setLanguage(savedData.language);
            }
            
            setNotificationMessage(`Recent code loaded successfully for ${savedData.language}.`, "success");
        } else {
            setNotificationMessage("No recent code found in the database for your account.", "error");
        }
    };
    
    // Close dropdown when clicking outside
    useEffect(() => {
        const handleClickOutside = (event) => {
            if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
                setShowDropdown(false);
            }
        };

        if (showDropdown) {
            document.addEventListener('mousedown', handleClickOutside);
        }

        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
        };
    }, [showDropdown]);
    
    // REMOVED: Initial load/new user check useEffect

    const handleLanguageChange = (newLanguage) => {
        if (isBusy) return; 
        
        setLanguage(newLanguage);
        setCode(languageExamples[newLanguage] || '// Code example not available');
        setTerminalOutput(""); 
        setAnalysisOutput("");
        setExplainOutput("");
        setExecutionStatus("idle");
        setIsWaitingForInput(false);
        setActiveTab('terminal');
    };

    const handleMouseDown = (e) => { 
        isResizing.current = true; 
        e.currentTarget.classList.add('is-dragging'); 
    };
    
    const handleMouseUp = () => { 
        isResizing.current = false; 
        const resizer = document.querySelector('.resizer'); 
        if(resizer) resizer.classList.remove('is-dragging'); 
    };
    
    const handleMouseMove = (e) => {
        if (!isResizing.current) return;
        const container = e.currentTarget;
        const totalWidth = container.offsetWidth;
        const newEditorWidth = (e.clientX - container.getBoundingClientRect().left) / totalWidth * 100;
        if (newEditorWidth > 30 && newEditorWidth < 80) setEditorWidth(newEditorWidth);
    };

    useEffect(() => {
        window.addEventListener('mouseup', handleMouseUp);
        return () => window.removeEventListener('mouseup', handleMouseUp);
    }, []);

    // WebSocket Connection and Logic
    const connectWebSocket = () => {
        if (reconnectTimeout.current) {
            clearTimeout(reconnectTimeout.current);
            reconnectTimeout.current = null;
        }

        if (ws.current && (ws.current.readyState === WebSocket.OPEN || ws.current.readyState === WebSocket.CONNECTING)) {
            return;
        }

        console.log(`🔌 Attempting connection to WebSocket: ${WS_URL}`);
        setConnectionStatus('connecting');

        try {
            if (ws.current) {
                 ws.current.close(1000, 'Reconnecting due to error');
            }

            ws.current = new WebSocket(WS_URL);

            ws.current.onopen = () => {
                console.log('✅ WebSocket connected');
                setConnectionStatus('connected');
            };

            ws.current.onmessage = (event) => {
                try {
                    const message = JSON.parse(event.data);
                    
                    switch (message.type) {
                        case 'output':
                            setTerminalOutput(prev => prev + message.data);
                            break;
                            
                        case 'input_request':
                            setIsWaitingForInput(true);
                            setExecutionStatus('waiting');
                            setActiveTab('terminal'); // Switch to terminal when input is needed
                            break;
                            
                        case 'execution_complete':
                            setIsRunning(false);
                            setIsWaitingForInput(false);
                            setExecutionStatus('completed');

                            // ******************************************************
                            // FIX: Save logic moved back here, checking for success
                            // ******************************************************
                            const codeToSave = currentCodeToSaveRef.current;
                            const exitCodeMatch = message.data.match(/Exit code: (\d+)/);
                            const exitCode = exitCodeMatch ? parseInt(exitCodeMatch[1], 10) : -1;
                            
                            // Check for fatal keywords in the output stream
                            const currentOutput = terminalOutput.toLowerCase();
                            // Expanded error checking to be more specific to crashes/compilation issues
                            const hasFatalError = 
                                currentOutput.includes("fatal error") ||
                                currentOutput.includes("parse error") ||
                                currentOutput.includes("compilation failed") ||
                                currentOutput.includes("syntaxerror") ||
                                currentOutput.includes("uncaught error") ||
                                currentOutput.includes("traceback") ||
                                currentOutput.includes("build failed") ||
                                currentOutput.includes("error:"); // Catches compiler/runtime errors

                            // Code is considered savable if no fatal error keywords are present.
                            if (!hasFatalError && uid && codeToSave.code.trim() && !user.isAnonymous) {
                                saveRecentCode(uid, codeToSave.code, codeToSave.language)
                                    .then(() => console.log("[Firestore]: Code saved (Successful execution/interruption)."))
                                    .catch(err => console.error("[Firestore]: Failed to save code:", err));
                            } else if (hasFatalError) {
                                console.log(`[Firestore]: Code not saved due to fatal error detected (Exit code: ${exitCode}).`);
                            }
                            
                            // Check if the message should be suppressed (only for stop/completion messages after loading recent code)
                            const isSuppressed = suppressStopMessageRef.current && message.data.includes("Execution stopped");

                            if (isSuppressed) {
                                console.log("Suppressed stop messages due to recent code load.");
                                suppressStopMessageRef.current = false; // Reset the flag immediately after consumption
                            } else {
                                // Only append messages if not suppressed
                                if (message.data) {
                                    setTerminalOutput(prev => prev + '\n' + message.data); 
                                }
                                setTerminalOutput(prev => prev + '\n\nExecution completed');
                            }
                            
                            // Clear temporary Ref regardless of save success/failure
                            currentCodeToSaveRef.current = { code: '', language: '' };
                            break;
                            
                        case 'error':
                            // Error handling for compilation/execution
                            const errorMsg = '\n[System Error]: ' + message.data;
                            setTerminalOutput(prev => prev + errorMsg);
                            
                            setIsRunning(false);
                            setIsWaitingForInput(false);
                            setExecutionStatus('completed');

                            // Clear temporary Ref on error
                            currentCodeToSaveRef.current = { code: '', language: '' }; 
                            break;
                            
                        case 'execution_started':
                            setExecutionStatus('running');
                            setTerminalOutput("Starting execution...\n"); 
                            setActiveTab('terminal');
                            // Note: Code is stored in ref in runCode, waiting for completion/error message here.
                            break;
                        default: break;
                    }
                } catch (error) {
                    console.error('Error parsing message:', error);
                }
            };

            ws.current.onerror = (error) => {
                console.error('❌ WebSocket error:', error);
            };

            ws.current.onclose = (event) => {
                console.log('🔌 WebSocket closed:', event.code, event.reason);
                setConnectionStatus('disconnected');
                setIsRunning(false);
                setIsWaitingForInput(false);
                
                if (event.code !== 1000 && event.code !== 1001) {
                    reconnectTimeout.current = setTimeout(connectWebSocket, 3000);
                }
            };

        } catch (error) {
            console.error('❌ WebSocket creation failed:', error);
            setConnectionStatus('disconnected');
            reconnectTimeout.current = setTimeout(connectWebSocket, 3000);
        }
    };

    // Health check and connection management useEffect
    // eslint-disable-next-line react-hooks/exhaustive-deps
    useEffect(() => {
        connectWebSocket(); // Initial connection attempt

        healthCheckInterval.current = setInterval(() => {
            if (ws.current && ws.current.readyState === WebSocket.OPEN) {
                try {
                    ws.current.send(JSON.stringify({ type: 'ping' }));
                } catch (error) {
                    ws.current.close(1001, 'Ping failed'); 
                }
            } else if (connectionStatus === 'disconnected' && !reconnectTimeout.current) {
                reconnectTimeout.current = setTimeout(connectWebSocket, 100); 
            }
        }, 30000); 

        return () => {
            if (ws.current) {
                ws.current.close(1000, 'Component unmounting');
            }
            if (reconnectTimeout.current) {
                clearTimeout(reconnectTimeout.current);
            }
            if (healthCheckInterval.current) {
                clearInterval(healthCheckInterval.current);
            }
        };
    }, []); 

    // MODIFIED: runCode initiates execution and sets code to save buffer
    const runCode = async () => {
        if (!code || isRunning || isAIProcessing) return;

        // Clear Explain tab before new execution starts
        setExplainOutput("");
        
        setIsRunning(true);
        setIsWaitingForInput(false);
        setExecutionStatus('running');
        setTerminalOutput("Starting execution...\n"); 
        setActiveTab('terminal'); // Switch to terminal

        const message = {
            type: 'execute',
            language: language,
            code: code
        };

        try {
            if (ws.current && ws.current.readyState === WebSocket.OPEN) {
                ws.current.send(JSON.stringify(message));
                
                // Store the current code/language in the mutable Ref
                if (uid && code.trim() && !user.isAnonymous) {
                    currentCodeToSaveRef.current = { code: code, language: language };
                }

            } else {
                setTerminalOutput(prev => prev + '\nConnection required. Please wait for connection or try reloading.');
                setIsRunning(false);
            }
        } catch (error) {
            setTerminalOutput(prev => prev + `\nFailed to send code: ${error.message}`);
            setIsRunning(false);
        }
    };

    // API Call (HTTP Fetch) for Analysis/Explain
    const handleAICall = async (mode) => {
        if (isAIProcessing || isRunning || connectionStatus !== 'connected' || !code.trim()) return;

        setIsAIProcessing(true);
        setIsRunning(false);
        setIsWaitingForInput(false);
        setExecutionStatus('running'); // Use 'running' status for processing, but 'ai-processing' dot

        let modeText = mode === 'analysis' ? 'Code Analysis' : 'Code Explanation';
        let setOutputState = mode === 'analysis' ? setAnalysisOutput : setExplainOutput;
        let tabId = mode === 'analysis' ? 'analysis' : 'explain';

        // LOGIC FOR CONTEXTUAL EXPLANATION: Check if terminal output exists to send contextual data
        const executionContext = terminalOutput.trim() !== '' ? terminalOutput : '';

        setOutputState(`Running ${modeText} with Gemini... This may take a few seconds.\n\n`);
        setActiveTab(tabId);

        try {
            const payload = {
                code: code, 
                language: language,
                // Conditionally include executionContext only if the terminal has content
                ...(executionContext && { executionContext }), 
            };

            const response = await fetch(`${BACKEND_BASE_URL}/gemini/${mode}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            const data = await response.json();

            if (!response.ok || data.result.startsWith("Error")) {
                throw new Error(data.result || `HTTP Error ${response.status}`);
            }
            
            const title = mode === 'analysis' ? 
                "\n--- CODE ANALYSIS (Gemini) ---" : 
                "\n--- CODE EXPLANATION (Gemini) ---";

            const cleanContent = DOMPurify.sanitize(data.result);

setOutputState(cleanContent);

        } catch (error) {
            setOutputState(prev => prev + `\n\nERROR: ${error.message}`);
        } finally {
            setIsAIProcessing(false);
            setExecutionStatus('completed');
        }
    };

    const stopExecution = () => {
        if (ws.current && ws.current.readyState === WebSocket.OPEN) {
            ws.current.send(JSON.stringify({ type: 'stop' }));
        }
        setIsRunning(false);
        setIsWaitingForInput(false);
        setExecutionStatus('completed');
        setIsAIProcessing(false);
        // Clear temporary Ref if stopped manually
        currentCodeToSaveRef.current = { code: '', language: '' }; 
        // Do NOT append 'Execution stopped' here; let the backend confirm it via the WebSocket message flow
    };

    const handleInlineInput = (inputText) => {
        if (!ws.current || ws.current.readyState !== WebSocket.OPEN) {
            return;
        }

        const message = {
            type: 'input',
            data: inputText
        };

        setTerminalOutput(prev => prev + inputText + '\n');
        
        try {
            ws.current.send(JSON.stringify(message));
            setIsWaitingForInput(false);
            setExecutionStatus('running');
        } catch (error) {
            setTerminalOutput(prev => prev + '\nFailed to send input\n');
        }
    };

    const getActiveContent = () => {
        switch (activeTab) {
            case 'analysis': return analysisOutput;
            case 'explain': return explainOutput;
            case 'terminal':
            default: return terminalOutput;
        }
    }

    const copyToClipboard = () => {
        navigator.clipboard.writeText(getActiveContent());
        setNotificationMessage("Output copied to clipboard!");
    };

    const clearOutput = () => {
        if (activeTab === 'terminal') {
            setTerminalOutput("");
            setIsWaitingForInput(false);
        }
        else if (activeTab === 'analysis') setAnalysisOutput("");
        else if (activeTab === 'explain') setExplainOutput("");
        
        // Reset status only if the current tab was terminal
        if (activeTab === 'terminal') {
              setExecutionStatus('idle');
        }
    };

    const getMonacoLanguage = () => {
        const langObj = languages.find(l => l.value === language);
        return langObj?.monacoLang || language;
    };

    // eslint-disable-next-line
    const getExecutionStatusText = () => {
        if (isAIProcessing) return 'AI Processing...';
        switch(executionStatus) {
            case 'running': return 'Running...';
            case 'waiting': return 'Waiting for input...';
            case 'completed': return 'Completed';
            default: return 'Ready';
        }
    };
    // eslint-disable-next-line
    const getConnectionStatusText = () => {
        switch(connectionStatus) {
            case 'connected': return 'Connected';
            case 'connecting': return 'Connecting...';
            case 'disconnected': return 'Disconnected';
            default: return 'Unknown';
        }
    };
    
    // Check if any major operation is currently running or processing
    const isBusy = isRunning || isAIProcessing;
    const tabList = [
        { id: 'terminal', label: 'Terminal' },
        { id: 'analysis', label: 'Analysis' },
        { id: 'explain', label: 'Explain' },
    ];

    return (
        <>
            <style>{styles}</style>
            <div className={`codesage-container theme-${theme}`}>
                <Notification 
                    message={notification.message} 
                    type={notification.type} 
                    onClose={clearNotification}
                />
                <header className="codesage-header">
                    <div className="codesage-logo">
                        <CodeSageLogo />
                        <h1>ThinkCode AI</h1>
                    </div>
                    <div className="codesage-controls">
                        <LanguageSelector 
                            language={language} 
                            setLanguage={handleLanguageChange} 
                            languages={languages} 
                            isDisabled={isBusy} // PASS THE isBusy state here
                        />
                        
                        
                        {!isBusy ? (
                            <>
                                <button 
                                    onClick={runCode} 
                                    className="styled-button"
                                    disabled={connectionStatus !== 'connected'}
                                >
                                    <PlayIcon />
                                    <span>Run Code</span>
                                </button>
                                
                                {/* AI: Analyze Button (Attractive Gradient) */}
                                <button 
                                    onClick={() => handleAICall('analysis')} 
                                    className="styled-button"
                                    disabled={connectionStatus !== 'connected' || isBusy || !code.trim()}
                                    style={{background: 'linear-gradient(135deg, #10b981, #059669)', boxShadow: '0 4px 15px -2px rgba(16, 185, 129, 0.3)'}}
                                >
                                    <SearchIcon />
                                    <span>Analyze</span>
                                </button>
                                
                                {/* AI: Explain Button (Attractive Gradient) */}
                                <button 
                                    onClick={() => handleAICall('explain')} 
                                    className="styled-button"
                                    disabled={connectionStatus !== 'connected' || isBusy || !code.trim()}
                                    style={{background: 'linear-gradient(135deg, #6366f1, #4f46e5)', boxShadow: '0 4px 15px -2px rgba(99, 102, 241, 0.3)'}}
                                >
                                    <BookOpenIcon />
                                    <span>Explain</span>
                                </button>
                            </>
                        ) : (
                            <button onClick={stopExecution} className="styled-button stop-button">
                                <StopIcon />
                                <span>Stop</span>
                            </button>
                        )}

                        <button className="theme-toggle" onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>
                            {theme === 'dark' ? <SunIcon /> : <MoonIcon />}
                        </button>
                        
                        {/* ADDED: Account Dropdown UI */}
                        <div className="account-dropdown-container" ref={dropdownRef}>
                            <div className="account-icon" onClick={toggleDropdown}>
                                {/* FIX: Ensure the photoURL is used when available */}
                                {user?.photoURL ? (
                                    <img 
                                        src={user.photoURL} 
                                        alt="Profile" 
                                        // CRITICAL FIX: Add onError to ensure the fallback text displays if the image is blocked/broken
                                        onError={(e) => { e.target.style.display = 'none'; e.target.nextSibling.style.display = 'flex'; }}
                                        style={{ 
                                            width: '100%', 
                                            height: '100%', 
                                            borderRadius: '50%', 
                                            objectFit: 'cover',
                                            // Ensure visibility initially
                                            display: user.photoURL ? 'block' : 'none'
                                        }}
                                    />
                                ) : null}
                                <span style={{
                                    // Fallback text (user initial) styling
                                    display: user?.photoURL ? 'none' : 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    width: '100%',
                                    height: '100%',
                                    position: 'absolute',
                                    top: 0,
                                    left: 0,
                                    zIndex: 1
                                }}>
                                    {user?.email?.charAt(0).toUpperCase() || user?.displayName?.charAt(0).toUpperCase() || 'U'}
                                </span>
                            </div>
                            <div className={`dropdown-menu ${showDropdown ? 'show' : ''}`}>
                                <div className="dropdown-header">
                                    {user?.email || user?.displayName || 'User Account'}
                                </div>
                                
                                {/* Recent Code Button - NOW FUNCTIONAL */}
                                <button className="dropdown-item" onClick={handleRecentCode}>
                                    <svg className="recent-code-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                        <polyline points="16 17 21 12 16 7"></polyline>
                                        <polyline points="8 17 3 12 8 7"></polyline>
                                        <line x1="10" y1="19" x2="14" y2="5"></line>
                                    </svg>
                                    Recent Code
                                </button>
                                
                                {/* Logout Button */}
                                <button className="dropdown-item" onClick={() => {onLogout(); setShowDropdown(false);}}>
                                    <svg className="logout-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                        <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                                        <polyline points="16 17 21 12 16 7" />
                                        <line x1="21" y1="12" x2="9" y2="12" />
                                    </svg>
                                    Logout
                                </button>
                            </div>
                        </div>
                        
                    </div>
                </header>

                <main className="codesage-main" onMouseMove={handleMouseMove} onMouseUp={handleMouseUp}>
                    <div className="panel editor-panel" style={{ width: `${editorWidth}%` }}>
                        <MonacoEditor // Switched to custom Monaco component
                            language={getMonacoLanguage()} 
                            theme={theme} // Pass theme directly
                            value={code} 
                            onChange={(v) => setCode(v || "")} 
                        />
                    </div>

                    <div className="resizer" onMouseDown={handleMouseDown}></div>

                    <div className="panel output-panel">

                        {/* Output Controls - heading and buttons */}
                        <div className="output-header" style={{borderBottom: 'none'}}>
                            <div style={{padding: '0 1rem', color: 'var(--text-primary)', fontWeight: '500'}}>
                                Output Controls
                            </div>
                            <div className="output-controls">
                                <button onClick={copyToClipboard} title="Copy to clipboard"><CopyIcon/></button>
                                <button onClick={clearOutput} title={`Clear ${activeTab} output`}><ClearIcon/></button>
                            </div>
                        </div>

                        {/* Tabs go here right below Output Controls */}
                        <div className="tab-bar" style={{marginBottom: '1rem'}}>
                            {tabList.map(tab => (
                                <button
                                    key={tab.id}
                                    className={`tab-button ${activeTab === tab.id ? 'active' : ''}`}
                                    onClick={() => setActiveTab(tab.id)}
                                >
                                    {tab.label}
                                </button>
                            ))}
                        </div>

                        {/* Output content below the tabs */}
                        <div className="tab-content-container">
                            {activeTab === 'terminal' ? (
                                <InlineTerminal 
                                    output={terminalOutput}
                                    isWaitingForInput={isWaitingForInput && connectionStatus === 'connected'}
                                    onSendInput={handleInlineInput}
                                />
                            ) : (
                                <AITextDisplay content={getActiveContent()} />
                            )}
                        </div>

                    </div>

                </main>
            </div>
        </>
    );
}

export default ThinkCodeAI;
