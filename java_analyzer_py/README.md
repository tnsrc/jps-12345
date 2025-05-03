# Java Project Analyzer

A tool for analyzing Java source code and tracing method call stacks.

## Features

- Parse Java source files with wildcard imports
- Track method calls and build call stack traces
- Store project structure in SQLite database
- Web interface for exploring method call hierarchies
- Filter out Java standard library calls

## Setup

1. Install dependencies:
   ```
   pip install -r requirements.txt
   ```

2. Run the application:
   ```
   python java_analyzer/web/app.py
   ```

3. Load your Java project by providing the source directory path

## Components

- `analyzer/`: Java source code parsing and analysis
- `database/`: SQLite database schema and utilities
- `web/`: Flask web application
- `templates/`: HTML templates
- `static/`: CSS, JavaScript, and other static assets 