import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import reportWebVitals from './reportWebVitals';
import ErrorBoundary from './ErrorBoundary';
import LoginPage from './LoginPage';


const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  // <React.StrictMode>
  // <App />
  // </React.StrictMode>
  <ErrorBoundary>
    <App />
  </ErrorBoundary>
);
//    const BACKEND_BASE_URL = 'https://redesigned-space-xylophone-q7r97969rrjc9r9q-8080.app.github.dev';
  // const WS_URL = 'wss://redesigned-space-xylophone-q7r97969rrjc9r9q-8080.app.github.dev/execute-ws';
// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
