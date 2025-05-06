# Static Method Call Analyzer

A web application for searching and visualizing static method calls from a Java codebase using SQLite database.

## Features

- Search static method calls by:
  - Caller class and method
  - Static class and method
- Visualize static method call relationships as an interactive graph
- View detailed static method call information in a table
- REST API endpoint for programmatic access
- Identify static method usage patterns

## Prerequisites

- Python 3.8 or higher
- SQLite database file (`java_analysis.db`) containing method call data

## Installation

1. Clone the repository
2. Install the required packages:
   ```bash
   pip install -r requirements.txt
   ```

## Usage

1. Make sure your `java_analysis.db` file is in the same directory as `app.py`
2. Run the application:
   ```bash
   python app.py
   ```
3. Open your web browser and navigate to `http://localhost:5000`

## API Endpoints

- `GET /api/method_calls`: Returns static method calls in JSON format
  - Query parameters:
    - `caller_class`: Filter by caller class name
    - `caller_method`: Filter by caller method name
    - `called_class`: Filter by static class name
    - `called_method`: Filter by static method name

## Example API Usage

```bash
curl "http://localhost:5000/api/method_calls?called_class=Helper&called_method=helperMethod"
```

## Development

The application is built using:
- Flask for the web server
- SQLAlchemy for database models
- Pandas for data manipulation
- Plotly for interactive visualizations
- Bootstrap for the UI

## License

MIT 