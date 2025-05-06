# Java Project Analysis Tool

This tool helps you analyze Java projects by extracting method signatures and tracing method call relationships. It uses JavaParser to parse Java source code and stores the analysis results in an SQLite database for efficient querying.

## Features

- Parse Java source code and extract method signatures
- Track method call relationships with line numbers
- Store analysis results in SQLite database
- Search for methods by signature (name, return type, parameters)
- Find method call relationships (callers and callees)

## Prerequisites

- Java 11 or later
- Maven 3.6 or later

## Building the Project

```bash
mvn clean package
```

This will create a JAR file in the `target` directory.

## Usage

1. Run the application with a Java project directory as argument:

```bash
java -jar target/java-analysis-1.0-SNAPSHOT.jar /path/to/java/project
```

2. The application will analyze the project and populate the SQLite database.

3. Use the interactive menu to:
   - Search for methods by signature
   - Find method call relationships
   - Exit the application

## Database Schema

The SQLite database (`java_analysis.db`) contains three main tables:

1. `classes`: Stores class information
   - package_name
   - class_name
   - source_code
   - file_path

2. `methods`: Stores method information
   - class_id (foreign key to classes)
   - method_name
   - return_type
   - parameters
   - is_static
   - is_public

3. `method_calls`: Stores method call relationships
   - caller_method_id (foreign key to methods)
   - called_method_id (foreign key to methods)
   - line_number

## Example Queries

1. Find all public methods in a specific package:
```sql
SELECT m.method_name, m.return_type, m.parameters
FROM methods m
JOIN classes c ON m.class_id = c.id
WHERE c.package_name = 'com.example.package'
AND m.is_public = 1;
```

2. Find all methods that call a specific method:
```sql
SELECT c1.package_name, c1.class_name, m1.method_name, m1.parameters
FROM method_calls mc
JOIN methods m1 ON mc.caller_method_id = m1.id
JOIN methods m2 ON mc.called_method_id = m2.id
JOIN classes c1 ON m1.class_id = c1.id
WHERE m2.method_name = 'targetMethod';
```

## Limitations

- The tool currently only analyzes Java source files (`.java`)
- Method resolution may not work correctly for complex inheritance scenarios
- External library calls are not fully resolved
- The analysis is performed at the source code level, not the bytecode level

## Contributing

Feel free to submit issues and enhancement requests! 