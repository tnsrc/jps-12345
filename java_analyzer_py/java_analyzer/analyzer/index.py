#!/usr/bin/env python
"""
Java Project Analyzer - Main Analysis Script

Provides an easy-to-use interface for analyzing Java projects with improved
wildcard import handling and method resolution.
"""

import os
import sys
import argparse
from .java_parser import JavaAnalyzer
from ..database.schema import create_tables, DB_PATH

def analyze_java_project(source_dir, reset_db=False, verbose=False):
    """
    Analyze a Java project directory
    
    Args:
        source_dir: Path to the Java source directory
        reset_db: Whether to reset the database before analysis
        verbose: Whether to print verbose output
    
    Returns:
        True if analysis completed successfully, False otherwise
    """
    # Validate source directory
    if not os.path.isdir(source_dir):
        print(f"Error: {source_dir} is not a valid directory")
        return False
    
    # Reset database if requested
    if reset_db and os.path.exists(DB_PATH):
        try:
            os.remove(DB_PATH)
            if verbose:
                print(f"Deleted existing database: {DB_PATH}")
        except Exception as e:
            print(f"Error deleting database: {str(e)}")
            return False
    
    # Create tables
    create_tables()
    if verbose:
        print(f"Database initialized at {DB_PATH}")
    
    # Run analysis
    analyzer = JavaAnalyzer()
    if verbose:
        print(f"Analyzing Java code in {source_dir}")
    
    analyzer.analyze_directory(source_dir)
    
    if verbose:
        print("Analysis complete!")
    
    return True

def main():
    parser = argparse.ArgumentParser(description='Analyze Java source code with improved wildcard import handling')
    parser.add_argument('source_dir', help='Directory containing Java source files')
    parser.add_argument('--reset', action='store_true', help='Reset the database before analysis')
    parser.add_argument('--verbose', '-v', action='store_true', help='Print verbose output')
    
    args = parser.parse_args()
    
    success = analyze_java_project(
        source_dir=args.source_dir,
        reset_db=args.reset,
        verbose=args.verbose
    )
    
    return 0 if success else 1

if __name__ == "__main__":
    sys.exit(main()) 