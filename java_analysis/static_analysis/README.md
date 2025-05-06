# Java Method Call Analyzer

A web-based tool for analyzing and visualizing Java method call stacks.

## Features

- Visualize method call hierarchies
- Search methods with autocomplete
- Browse methods by class
- Analyze Java source code
- Support for multiple SQLite databases
- Method call tracing

## Setup

1. Install Python dependencies:
```bash
pip install -r requirements.txt
```

2. Run the application:
```bash
python app.py
```

3. Open your browser and navigate to `http://localhost:5000`

## Usage

### Method Search
- Use the search box in the top navigation bar to search for methods
- The search supports autocomplete and will show matching methods
- Press Enter to view the method's call stack

### Class Selection
- Use the class dropdown to browse methods by class
- Classes are grouped by package

### Database Management
- Use the left sidebar to select different SQLite databases
- Enter a Java source directory path to analyze new code
- Toggle "Reset Database" to clear existing data before analysis

### Method Call Stack
- View method call hierarchies in the main content area
- Click "Trace" on any method to view its call stack
- Methods are grouped by call depth
- Static methods are marked with "static" prefix

## Database Schema

The application uses SQLite with the following tables:

- `classes`: Stores class information
- `methods`: Stores method information
- `method_calls`: Stores method call relationships

## Development

To modify the application:

1. Edit `app.py` for backend changes
2. Edit templates in the `templates` directory for frontend changes
3. Add new static files to the `static` directory 