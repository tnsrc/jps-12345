import os
import sys
import argparse
from .java_parser import JavaAnalyzer
from ..database.schema import create_tables, DB_PATH

def main():
    parser = argparse.ArgumentParser(description='Analyze Java source code')
    parser.add_argument('source_dir', help='Directory containing Java source files')
    parser.add_argument('--reset', action='store_true', help='Reset the database before analysis')
    
    args = parser.parse_args()
    
    # Validate source directory
    if not os.path.isdir(args.source_dir):
        print(f"Error: {args.source_dir} is not a valid directory")
        return 1
    
    # Reset database if requested
    if args.reset and os.path.exists(DB_PATH):
        try:
            os.remove(DB_PATH)
            print(f"Deleted existing database: {DB_PATH}")
        except Exception as e:
            print(f"Error deleting database: {str(e)}")
            return 1
    
    # Create tables
    create_tables()
    print(f"Database initialized at {DB_PATH}")
    
    # Run analysis
    analyzer = JavaAnalyzer()
    print(f"Analyzing Java code in {args.source_dir}")
    analyzer.analyze_directory(args.source_dir)
    print("Analysis complete!")
    
    return 0

if __name__ == "__main__":
    sys.exit(main()) 