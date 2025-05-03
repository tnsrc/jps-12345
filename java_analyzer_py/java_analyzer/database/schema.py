import sqlite3
import os
import glob

# Database directory
DB_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# Default database path
DEFAULT_DB_NAME = 'java_project.db'
DB_PATH = os.path.join(DB_DIR, DEFAULT_DB_NAME)

# Current active database path
ACTIVE_DB_PATH = DB_PATH

def set_active_database(db_path):
    """Set the active database path"""
    global ACTIVE_DB_PATH
    
    # If we're switching databases, clean up any existing connections
    if ACTIVE_DB_PATH != db_path:
        cleanup_connections()
        
    ACTIVE_DB_PATH = db_path
    return ACTIVE_DB_PATH

def get_active_database():
    """Get the active database path"""
    return ACTIVE_DB_PATH

def cleanup_connections():
    """Attempt to clean up any open database connections"""
    # This is a bit of a hack, but it should help force SQLite to release locks
    try:
        # Create a dummy connection and close it to release locks
        conn = sqlite3.connect(ACTIVE_DB_PATH)
        conn.close()
        
        # Force a garbage collection which may help clean up connections
        import gc
        gc.collect()
    except Exception:
        # Ignore errors - this is just a best effort cleanup
        pass

def get_available_databases():
    """Get list of available databases in the app directory"""
    db_pattern = os.path.join(DB_DIR, "*.db")
    db_files = glob.glob(db_pattern)
    return [os.path.basename(db) for db in db_files]

def create_tables(db_path=None):
    """Create the necessary tables for the Java project analyzer"""
    if db_path is None:
        db_path = ACTIVE_DB_PATH
        
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    # Create tables
    
    # Classes table
    cursor.execute('''
    CREATE TABLE IF NOT EXISTS classes (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        package TEXT,
        file_path TEXT,
        is_interface INTEGER DEFAULT 0,
        is_abstract INTEGER DEFAULT 0,
        UNIQUE(name, package)
    )
    ''')
    
    # Methods table
    cursor.execute('''
    CREATE TABLE IF NOT EXISTS methods (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        class_id INTEGER NOT NULL,
        name TEXT NOT NULL,
        signature TEXT NOT NULL,
        return_type TEXT,
        is_static INTEGER DEFAULT 0,
        is_public INTEGER DEFAULT 0,
        start_line INTEGER,
        end_line INTEGER,
        FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE,
        UNIQUE(class_id, signature)
    )
    ''')
    
    # Method calls table (represents method_a calls method_b)
    cursor.execute('''
    CREATE TABLE IF NOT EXISTS method_calls (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        caller_method_id INTEGER NOT NULL,
        called_class TEXT,
        called_method TEXT,
        called_signature TEXT,
        line_number INTEGER,
        resolved_method_id INTEGER,
        FOREIGN KEY (caller_method_id) REFERENCES methods(id) ON DELETE CASCADE,
        FOREIGN KEY (resolved_method_id) REFERENCES methods(id) ON DELETE SET NULL
    )
    ''')
    
    # Imports table
    cursor.execute('''
    CREATE TABLE IF NOT EXISTS imports (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        class_id INTEGER NOT NULL,
        import_path TEXT NOT NULL,
        is_wildcard INTEGER DEFAULT 0,
        is_static INTEGER DEFAULT 0,
        FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE
    )
    ''')
    
    # Class fields
    cursor.execute('''
    CREATE TABLE IF NOT EXISTS fields (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        class_id INTEGER NOT NULL,
        name TEXT NOT NULL,
        type TEXT,
        is_static INTEGER DEFAULT 0,
        FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE
    )
    ''')
    
    conn.commit()
    conn.close()
    
    return True

if __name__ == "__main__":
    create_tables()
    print(f"Database created at {ACTIVE_DB_PATH}") 