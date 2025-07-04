import sqlite3
import os
from .schema import get_active_database

def get_connection():
    """Get a database connection to the currently active database"""
    # Get the current active database path to ensure freshness
    active_db_path = get_active_database()
    
    # Create a new connection each time
    conn = sqlite3.connect(active_db_path)
    conn.row_factory = sqlite3.Row
    return conn

# Class operations
def add_class(name, package=None, file_path=None, is_interface=False, is_abstract=False):
    """Add a class to the database"""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute(
            """INSERT INTO classes (name, package, file_path, is_interface, is_abstract) 
               VALUES (?, ?, ?, ?, ?)""",
            (name, package, file_path, 1 if is_interface else 0, 1 if is_abstract else 0)
        )
        class_id = cursor.lastrowid
        conn.commit()
        return class_id
    except sqlite3.IntegrityError:
        # Class already exists, get its ID
        cursor.execute(
            """SELECT id FROM classes WHERE name = ? AND package = ?""",
            (name, package)
        )
        result = cursor.fetchone()
        return result['id'] if result else None
    finally:
        conn.close()

def update_class(class_id, name=None, package=None, file_path=None, is_interface=None, is_abstract=None):
    """Update a class in the database"""
    conn = get_connection()
    cursor = conn.cursor()
    
    updates = []
    values = []
    
    if name is not None:
        updates.append("name = ?")
        values.append(name)
    if package is not None:
        updates.append("package = ?")
        values.append(package)
    if file_path is not None:
        updates.append("file_path = ?")
        values.append(file_path)
    if is_interface is not None:
        updates.append("is_interface = ?")
        values.append(1 if is_interface else 0)
    if is_abstract is not None:
        updates.append("is_abstract = ?")
        values.append(1 if is_abstract else 0)
    
    if not updates:
        return False
    
    values.append(class_id)
    
    query = f"UPDATE classes SET {', '.join(updates)} WHERE id = ?"
    cursor.execute(query, values)
    conn.commit()
    conn.close()
    return cursor.rowcount > 0

def delete_class(class_id):
    """Delete a class from the database"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM classes WHERE id = ?", (class_id,))
    conn.commit()
    conn.close()
    return cursor.rowcount > 0

def get_class(class_id=None, name=None, package=None):
    """Get a class from the database"""
    conn = get_connection()
    cursor = conn.cursor()
    
    if class_id:
        cursor.execute("SELECT * FROM classes WHERE id = ?", (class_id,))
    elif name and package:
        cursor.execute("SELECT * FROM classes WHERE name = ? AND package = ?", (name, package))
    elif name:
        cursor.execute("SELECT * FROM classes WHERE name = ?", (name,))
    else:
        return None
    
    result = cursor.fetchone()
    conn.close()
    return dict(result) if result else None

def get_all_classes():
    """Get all classes from the database"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM classes")
    results = cursor.fetchall()
    conn.close()
    return [dict(row) for row in results]

# Method operations
def add_method(class_id, name, signature, return_type=None, is_static=False, is_public=True, start_line=None, end_line=None):
    """Add a method to the database"""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute(
            """INSERT INTO methods (class_id, name, signature, return_type, is_static, is_public, start_line, end_line) 
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            (class_id, name, signature, return_type, 1 if is_static else 0, 1 if is_public else 0, start_line, end_line)
        )
        method_id = cursor.lastrowid
        conn.commit()
        return method_id
    except sqlite3.IntegrityError:
        # Method already exists, get its ID
        cursor.execute(
            """SELECT id FROM methods WHERE class_id = ? AND signature = ?""",
            (class_id, signature)
        )
        result = cursor.fetchone()
        return result['id'] if result else None
    finally:
        conn.close()

def update_method(method_id, name=None, signature=None, return_type=None, is_static=None, is_public=None, start_line=None, end_line=None):
    """Update a method in the database"""
    conn = get_connection()
    cursor = conn.cursor()
    
    updates = []
    values = []
    
    if name is not None:
        updates.append("name = ?")
        values.append(name)
    if signature is not None:
        updates.append("signature = ?")
        values.append(signature)
    if return_type is not None:
        updates.append("return_type = ?")
        values.append(return_type)
    if is_static is not None:
        updates.append("is_static = ?")
        values.append(1 if is_static else 0)
    if is_public is not None:
        updates.append("is_public = ?")
        values.append(1 if is_public else 0)
    if start_line is not None:
        updates.append("start_line = ?")
        values.append(start_line)
    if end_line is not None:
        updates.append("end_line = ?")
        values.append(end_line)
    
    if not updates:
        return False
    
    values.append(method_id)
    
    query = f"UPDATE methods SET {', '.join(updates)} WHERE id = ?"
    cursor.execute(query, values)
    conn.commit()
    conn.close()
    return cursor.rowcount > 0

def delete_method(method_id):
    """Delete a method from the database"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM methods WHERE id = ?", (method_id,))
    conn.commit()
    conn.close()
    return cursor.rowcount > 0

def get_method(method_id=None, class_id=None, name=None, signature=None):
    """Get a method from the database"""
    conn = get_connection()
    cursor = conn.cursor()
    
    if method_id:
        cursor.execute("SELECT * FROM methods WHERE id = ?", (method_id,))
    elif class_id and signature:
        cursor.execute("SELECT * FROM methods WHERE class_id = ? AND signature = ?", (class_id, signature))
    elif class_id and name:
        cursor.execute("SELECT * FROM methods WHERE class_id = ? AND name = ?", (class_id, name))
    else:
        return None
    
    result = cursor.fetchone()
    conn.close()
    return dict(result) if result else None

def get_methods_by_class(class_id):
    """Get all methods for a class"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM methods WHERE class_id = ?", (class_id,))
    results = cursor.fetchall()
    conn.close()
    return [dict(row) for row in results]

# Method call operations
def add_method_call(caller_method_id, called_class, called_method, called_signature=None, line_number=None):
    """Add a method call to the database"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        """INSERT INTO method_calls (caller_method_id, called_class, called_method, called_signature, line_number) 
           VALUES (?, ?, ?, ?, ?)""",
        (caller_method_id, called_class, called_method, called_signature, line_number)
    )
    call_id = cursor.lastrowid
    conn.commit()
    conn.close()
    return call_id

def get_method_calls(caller_method_id):
    """Get all method calls made by a method"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        """SELECT * FROM method_calls WHERE caller_method_id = ?""",
        (caller_method_id,)
    )
    results = cursor.fetchall()
    conn.close()
    return [dict(row) for row in results]

def get_method_call_stack(method_id, visited=None):
    """Get the method call stack for a method (direct calls only)"""
    from . import get_class, get_fields_by_class  # Import here to avoid circular import at module level
    if visited is None:
        visited = set()
    
    if method_id in visited:
        return None  # Prevent infinite recursion, return None not []
    
    visited.add(method_id)
    
    conn = get_connection()
    cursor = conn.cursor()
    
    # Get the method info
    cursor.execute("SELECT * FROM methods WHERE id = ?", (method_id,))
    method = cursor.fetchone()
    if method:
        method = dict(method)
    
    if not method:
        conn.close()
        return None  # Return None if not found
    
    # Get the class info
    cursor.execute("SELECT * FROM classes WHERE id = ?", (method['class_id'],))
    class_info = cursor.fetchone()
    if class_info:
        class_info = dict(class_info)
    
    # Get all method calls made by this method
    cursor.execute("""
        SELECT mc.*
        FROM method_calls mc
        WHERE mc.caller_method_id = ?
    """, (method_id,))
    
    calls = cursor.fetchall()
    conn.close()
    
    calls_info = []
    for call in calls:
        call_dict = dict(call)
        call_dict['children'] = []  # No recursion by resolved_method_id
        called_class_name = call_dict.get('called_class')
        called_package = None
        resolved = False
        # Try to resolve called_class_name to a real class
        if called_class_name:
            # 1. Try direct class lookup
            class_info_lookup = get_class(name=called_class_name)
            if class_info_lookup and isinstance(class_info_lookup, dict):
                called_package = class_info_lookup.get('package')
                called_class_name = f"{called_package}.{called_class_name}" if called_package else called_class_name
                resolved = True
            # 2. If not found, try to resolve as a field (instance variable)
            if not resolved and method and 'class_id' in method:
                fields = get_fields_by_class(method['class_id'])
                found_type = None
                found_package = None
                for field in fields:
                    if field['name'] == call_dict['called_class']:
                        found_type = field['type']
                        # Try to get package for this type
                        class_info2 = get_class(name=found_type)
                        if class_info2 and isinstance(class_info2, dict):
                            found_package = class_info2.get('package')
                        break
                if found_type:
                    called_class_name = f"{found_package}.{found_type}" if found_package else found_type
                    called_package = found_package
                    resolved = True
            # 3. If still not found, try to resolve from local variable declaration in method source
            if not resolved and method and class_info:
                file_path = class_info.get('file_path')
                if file_path and method.get('start_line'):
                    try:
                        with open(file_path, 'r', encoding='utf-8') as f:
                            lines = f.readlines()
                        start = max(0, (method['start_line'] or 1) - 1)
                        end = min(len(lines), start + 30)
                        for i in range(start, end):
                            line = lines[i].strip()
                            import re
                            m = re.match(r'(\w+)\s+' + re.escape(call_dict['called_class']) + r'\s*[;=]', line)
                            if m:
                                found_type = m.group(1)
                                class_info2 = get_class(name=found_type)
                                if class_info2 and isinstance(class_info2, dict):
                                    found_package = class_info2.get('package')
                                break
                        if found_type:
                            called_class_name = f"{found_package}.{found_type}" if found_package else found_type
                            called_package = found_package
                            resolved = True
                    except Exception:
                        pass
        call_dict['called_class_name'] = called_class_name
        call_dict['called_package'] = called_package
        call_dict['method_id'] = None  # No resolved method id
        calls_info.append(call_dict)
    
    return {
        'method_id': method_id,
        'method_name': method['name'],
        'signature': method['signature'],
        'class_name': class_info['name'] if class_info else None,
        'package': class_info['package'] if class_info else None,
        'calls': calls_info
    }

# Import operations
def add_import(class_id, import_path, is_wildcard=False, is_static=False):
    """Add an import to the database"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        """INSERT INTO imports (class_id, import_path, is_wildcard, is_static) 
           VALUES (?, ?, ?, ?)""",
        (class_id, import_path, 1 if is_wildcard else 0, 1 if is_static else 0)
    )
    import_id = cursor.lastrowid
    conn.commit()
    conn.close()
    return import_id

def get_imports_by_class(class_id):
    """Get all imports for a class"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM imports WHERE class_id = ?", (class_id,))
    results = cursor.fetchall()
    conn.close()
    return [dict(row) for row in results]

# Field operations
def add_field(class_id, name, type_name=None, is_static=False):
    """Add a field to the database"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        """INSERT INTO fields (class_id, name, type, is_static) 
           VALUES (?, ?, ?, ?)""",
        (class_id, name, type_name, 1 if is_static else 0)
    )
    field_id = cursor.lastrowid
    conn.commit()
    conn.close()
    return field_id

def get_fields_by_class(class_id):
    """Get all fields for a class"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM fields WHERE class_id = ?", (class_id,))
    results = cursor.fetchall()
    conn.close()
    return [dict(row) for row in results]

# Advanced queries
def find_methods_by_name(method_name, exact_match=False):
    """Find all methods with a given name"""
    conn = get_connection()
    cursor = conn.cursor()
    
    if exact_match:
        query = "SELECT m.*, c.name as class_name, c.package FROM methods m JOIN classes c ON m.class_id = c.id WHERE m.name = ?"
        cursor.execute(query, (method_name,))
    else:
        query = "SELECT m.*, c.name as class_name, c.package FROM methods m JOIN classes c ON m.class_id = c.id WHERE m.name LIKE ?"
        cursor.execute(query, (f"%{method_name}%",))
    
    results = cursor.fetchall()
    conn.close()
    return [dict(row) for row in results]

def find_methods_by_signature(signature_pattern):
    """Find all methods with a signature matching the pattern"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
        SELECT m.*, c.name as class_name, c.package
        FROM methods m
        JOIN classes c ON m.class_id = c.id
        WHERE m.signature LIKE ?
    """, (f"%{signature_pattern}%",))
    results = cursor.fetchall()
    conn.close()
    return [dict(row) for row in results]

def get_full_method_call_trace(method_id):
    """Get the complete method call trace for a method excluding Java standard library calls"""
    call_stack = get_method_call_stack(method_id)
    
    # Filter out Java standard library calls (packages starting with java.*, javax.*)
    def filter_standard_lib_calls(call_node):
        if not call_node:
            return None
        
        if 'calls' in call_node:
            filtered_calls = []
            for call in call_node['calls']:
                # If it's a resolved call and not from standard library
                if call.get('called_package') and (
                    call['called_package'].startswith('java.') or 
                    call['called_package'].startswith('javax.')
                ):
                    continue
                
                # Recursively filter children
                if 'children' in call:
                    call['children'] = filter_standard_lib_calls(call['children'])
                
                filtered_calls.append(call)
            
            call_node['calls'] = filtered_calls
        
        return call_node
    
    return filter_standard_lib_calls(call_stack) 