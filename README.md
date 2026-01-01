# ThinkCode AI 🚀

A powerful, real-time online code compiler supporting 9+ programming languages with AI-powered code analysis and explanation features.

[![Deploy on Hugging Face](https://img.shields.io/badge/🤗-Deploy%20on%20HF%20Spaces-yellow)](https://huggingface.co/spaces/gopipradeep/ThinkCode-AI)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## ✨ Features

- **Multi-Language Support**: Execute code in Python, Java, C++, C, JavaScript, PHP, Go, C#, and Ruby
- **Real-time Execution**: Interactive WebSocket-based code execution with live output streaming
- **AI-Powered Analysis**: 
  - Code complexity analysis with Big O notation
  - Step-by-step code explanations
  - Optimization suggestions
- **Collaborative Coding**: Real-time code sharing and collaboration sessions
- **Interactive Input**: Full support for programs requiring user input
- **Secure Sandboxing**: Isolated execution environment for each session

## 🛠️ Tech Stack

**Backend:**
- Spring Boot 3.5.5
- WebSocket for real-time communication
- Java 17
- Maven

**AI Integration:**
- Google Gemini 2.5 Flash API

**Deployment:**
- Docker
- Hugging Face Spaces

## 🏗️ Project Structure

The core logic of the application is organized as follows:

- **`InteractiveCodeExecutionHandler.java`**: Manages the WebSocket lifecycle, process sandboxing, and real-time output streaming.
- **`GeminiController.java`**: Handles AI-powered analysis and explanation requests.
- **`CompilerController.java`**: Provides basic REST endpoints for system status and health checks.
- **`application.properties`**: Defines server ports (default `7860`), CORS policies, and execution timeouts.
- **`Dockerfile`**: Configures the multi-stage build and the Debian-based environment with all necessary compilers.

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker (for containerized deployment)

### Local Development

1. **Clone the repository**
```bash
git clone https://github.com/yourusername/thinkcode-ai.git
cd thinkcode-ai/online-compiler
```

2. **Set up environment variables**
```bash
export GEMINI_API_KEY=your_gemini_api_key_here
```

3. **Build and run**
```bash
./mvnw clean package
./mvnw spring-boot:run
```

4. **Access the application**
```
http://localhost:7860
```

### Docker Deployment
```bash
docker build -t thinkcode-ai .
docker run -p 7860:7860 -e GEMINI_API_KEY=your_key thinkcode-ai
```

## 🌐 Deploy on Hugging Face Spaces

Deploy your own instance on Hugging Face Spaces:

1. Fork this repository: [ThinkCode-AI on Hugging Face](https://huggingface.co/spaces/gopipradeep/ThinkCode-AI/tree/main)
2. Add your `GEMINI_API_KEY` in Space secrets
3. The Space will automatically build and deploy

## 📡 API Endpoints

### WebSocket
- **`/execute-ws`**: Real-time code execution with interactive I/O

### REST API
- **`GET /api/status`**: Check service status
- **`POST /gemini/analysis`**: Get AI-powered code complexity analysis
- **`POST /gemini/explain`**: Get step-by-step code explanation

## 🔧 Supported Languages

| Language | Version | Compiler/Interpreter |
|----------|---------|---------------------|
| Python | 3.x | python3 |
| Java | 17 | javac + java |
| C++ | C++17 | g++ |
| C | C11 | gcc |
| JavaScript | Node.js | node |
| PHP | 8.x | php-cli |
| Go | Latest | go |
| C# | Mono | mcs + mono |
| Ruby | Latest | ruby |



## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built with Spring Boot
- Powered by Google Gemini AI
- Deployed on Hugging Face Spaces

## 📧 Contact

Project Link: [https://huggingface.co/spaces/gopipradeep/ThinkCode-AI](https://huggingface.co/spaces/gopipradeep/ThinkCode-AI)

---

**⚡ Live Demo**: Try it now at [ThinkCode AI on Hugging Face](https://huggingface.co/spaces/gopipradeep/ThinkCode-AI)
