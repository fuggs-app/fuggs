#!/bin/bash
# Fuggs Landing Page - Development Server

PORT="${1:-8001}"

echo "🚀 Starting Fuggs Landing Page Development Server..."
echo "📍 URL: http://localhost:$PORT"
echo "🛑 Press Ctrl+C to stop"
echo ""

# Check if Python 3 is available
if command -v python3 &> /dev/null; then
    python3 -m http.server "$PORT"
elif command -v python &> /dev/null; then
    python -m http.server "$PORT"
else
    echo "❌ Error: Python is not installed."
    echo "Please install Python 3 or use another web server."
    exit 1
fi
