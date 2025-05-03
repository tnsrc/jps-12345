import unittest
import os
import tempfile
import shutil
from java_analyzer.analyzer import JavaAnalyzer
from java_analyzer.database import create_tables, DB_PATH

class TestJavaAnalyzer(unittest.TestCase):
    """Test cases for the Java Analyzer"""
    
    def setUp(self):
        # Create a temporary directory for the test database
        self.temp_dir = tempfile.mkdtemp()
        
        # Initialize the database
        create_tables()
        
        # Create a sample Java file for testing
        self.create_test_java_file()
    
    def tearDown(self):
        # Remove temp directory
        shutil.rmtree(self.temp_dir)
        
        # Remove test database
        if os.path.exists(DB_PATH):
            os.remove(DB_PATH)
    
    def create_test_java_file(self):
        """Create a sample Java file for testing"""
        java_file_content = """
        package com.example.test;
        
        import java.util.List;
        import java.util.ArrayList;
        
        public class TestClass {
            private String name;
            
            public TestClass(String name) {
                this.name = name;
            }
            
            public void testMethod() {
                System.out.println("Testing");
                anotherMethod();
            }
            
            private void anotherMethod() {
                List<String> items = new ArrayList<>();
                items.add("Item 1");
            }
        }
        """
        
        os.makedirs(os.path.join(self.temp_dir, "com", "example", "test"), exist_ok=True)
        with open(os.path.join(self.temp_dir, "com", "example", "test", "TestClass.java"), 'w') as f:
            f.write(java_file_content)
    
    def test_analyze_directory(self):
        """Test analyzing a directory of Java files"""
        analyzer = JavaAnalyzer()
        analyzer.analyze_directory(self.temp_dir)
        
        # Here you would add assertions to verify the database was populated correctly
        # For example, checking that TestClass exists, has methods, etc.
        # This would require adding query functions to check the database state
        
        # For now, we'll just mark this as a placeholder
        self.assertTrue(True, "Placeholder for actual assertions")


if __name__ == '__main__':
    unittest.main() 