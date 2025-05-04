import os
import json
import re
from flask import Flask, render_template, request, jsonify
from flaskwebgui import FlaskUI

from ..database import (
    create_tables, DB_PATH, get_all_classes, get_methods_by_class,
    find_methods_by_name, find_methods_by_signature, get_full_method_call_trace,
    get_method, get_class
)
from ..database.schema import (
    get_available_databases, set_active_database, get_active_database, 
    DB_DIR, DEFAULT_DB_NAME
)
from ..database.db_utils import get_connection
from ..analyzer import JavaAnalyzer

app = Flask(__name__, 
            template_folder=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'templates'),
            static_folder=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'static'))

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/analyze', methods=['POST'])
def analyze():
    """Analyze a Java source directory"""
    source_dir = request.form.get('source_dir')
    reset_db = request.form.get('reset_db') == 'true'
    
    if not source_dir or not os.path.isdir(source_dir):
        return jsonify({'success': False, 'error': 'Invalid source directory'}), 400
    
    # Get current active database
    active_db = get_active_database()
    
    # Reset database if requested
    if reset_db and os.path.exists(active_db):
        try:
            os.remove(active_db)
        except Exception as e:
            return jsonify({'success': False, 'error': f'Error deleting database: {str(e)}'}), 500
    
    # Create tables
    create_tables()
    
    # Run analysis
    analyzer = JavaAnalyzer()
    try:
        analyzer.analyze_directory(source_dir)
        return jsonify({'success': True})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/classes')
def get_classes():
    """Get all classes in the database"""
    try:
        classes = get_all_classes()
        return jsonify({'success': True, 'classes': classes})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/classes/<int:class_id>/methods')
def get_methods(class_id):
    """Get all methods for a class"""
    try:
        methods = get_methods_by_class(class_id)
        return jsonify({'success': True, 'methods': methods})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/methods/search', methods=['GET'])
def search_methods():
    """Search for methods by name or signature"""
    method_name = request.args.get('name')
    signature = request.args.get('signature')
    exact_match = request.args.get('exact_match') == 'true'
    
    try:
        methods = []
        if method_name:
            methods = find_methods_by_name(method_name, exact_match)
        elif signature:
            methods = find_methods_by_signature(signature)
        return jsonify({'success': True, 'methods': methods})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/methods/<int:method_id>/trace')
def trace_method(method_id):
    """Get the method call trace for a method"""
    try:
        # Get the method info first to include its line information
        method_info = get_method(method_id)
        
        trace = get_full_method_call_trace(method_id)
        
        # Add the method's own line number to the root trace node
        if trace and method_info:
            trace['start_line'] = method_info.get('start_line')
            trace['end_line'] = method_info.get('end_line')
            trace['file_path'] = get_class(method_info.get('class_id')).get('file_path')
            
        return jsonify({'success': True, 'trace': trace})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/db_path')
def get_db_path():
    """Get the current database path"""
    return jsonify({'db_path': get_active_database()})

@app.route('/available_databases')
def available_databases():
    """Get list of available databases"""
    try:
        databases = get_available_databases()
        active_db = os.path.basename(get_active_database())
        return jsonify({
            'success': True, 
            'databases': databases,
            'active_database': active_db
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/switch_database', methods=['POST'])
def switch_database():
    """Switch to a different database"""
    db_name = request.form.get('db_name')
    if not db_name:
        return jsonify({'success': False, 'error': 'Database name is required'}), 400
        
    try:
        # Construct full path
        db_path = os.path.join(DB_DIR, db_name)
        
        # Check if the database exists, if not create it
        is_new_db = not os.path.exists(db_path)
        if is_new_db:
            # For new databases, create an empty file
            # First ensure no file exists
            if os.path.exists(db_path):
                os.remove(db_path)
                
            # Create a new empty database file
            open(db_path, 'w').close()
            
            # Set as active database before creating tables
            set_active_database(db_path)
            
            # Create tables in the new database
            create_tables(db_path)
        else:
            # For existing databases, just switch to it
            set_active_database(db_path)
        
        return jsonify({
            'success': True, 
            'message': f'Switched to database: {db_name}',
            'db_path': db_path,
            'is_new': is_new_db
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/methods/exists')
def check_method_exists():
    """Check if a method exists in the database"""
    method_name = request.args.get('name')
    
    if not method_name:
        return jsonify({'success': False, 'error': 'Method name is required'}), 400
    
    try:
        # Search for the method with exact matching
        methods = find_methods_by_name(method_name, exact_match=True)
        exists = len(methods) > 0
        
        return jsonify({
            'success': True, 
            'exists': exists,
            'count': len(methods)
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/methods/autocomplete')
def autocomplete_methods():
    """Provide autocomplete suggestions for method names or signatures"""
    term = request.args.get('term', '')
    suggestion_type = request.args.get('type', 'name')  # 'name' or 'signature'
    limit = request.args.get('limit', 10, type=int)
    
    # Only require minimum length if a term is provided
    if term and len(term) < 2:
        return jsonify({'success': False, 'error': 'Search term must be at least 2 characters'}), 400
        
    try:
        conn = get_connection()
        cursor = conn.cursor()
        
        if suggestion_type == 'name':
            # When no term is provided, get the most recently added methods
            if not term:
                query = """
                    SELECT DISTINCT name 
                    FROM methods 
                    ORDER BY id DESC
                    LIMIT ?
                """
                cursor.execute(query, (limit,))
            else:
                query = """
                    SELECT DISTINCT name 
                    FROM methods 
                    WHERE name LIKE ? 
                    ORDER BY name 
                    LIMIT ?
                """
                cursor.execute(query, (f"%{term}%", limit))
                
            results = [row[0] for row in cursor.fetchall()]
            
        elif suggestion_type == 'signature':
            # When no term is provided, get the most recently added methods
            if not term:
                query = """
                    SELECT m.signature, c.name as class_name, c.package 
                    FROM methods m
                    JOIN classes c ON m.class_id = c.id
                    ORDER BY m.id DESC
                    LIMIT ?
                """
                cursor.execute(query, (limit,))
            else:
                query = """
                    SELECT m.signature, c.name as class_name, c.package 
                    FROM methods m
                    JOIN classes c ON m.class_id = c.id
                    WHERE m.signature LIKE ? 
                    ORDER BY m.signature 
                    LIMIT ?
                """
                cursor.execute(query, (f"%{term}%", limit))
                
            results = []
            for row in cursor.fetchall():
                signature = row[0]
                class_name = row[1]
                package = row[2] or ''
                full_info = {
                    'signature': signature,
                    'class_name': class_name,
                    'package': package,
                    'display': f"{package}.{class_name}: {signature}" if package else f"{class_name}: {signature}"
                }
                results.append(full_info)
            
        else:
            return jsonify({'success': False, 'error': 'Invalid suggestion type'}), 400
            
        conn.close()
        
        return jsonify({
            'success': True,
            'suggestions': results
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/methods/<int:method_id>/source')
def get_method_source(method_id):
    """Get the source code for a method and analyze execution paths"""
    try:
        # Get method info
        method = get_method(method_id)
        if not method:
            return jsonify({'success': False, 'error': 'Method not found'}), 404
            
        # Get class info to find the file
        class_info = get_class(method['class_id'])
        if not class_info or not class_info.get('file_path') or not os.path.exists(class_info['file_path']):
            return jsonify({'success': False, 'error': 'Source file not found'}), 404
            
        # Get method start and end lines
        start_line = method.get('start_line')
        end_line = method.get('end_line')
        
        if not start_line:
            return jsonify({'success': False, 'error': 'Method line information not available'}), 404
            
        # If end_line is not available, read the entire file and try to determine it
        if not end_line:
            with open(class_info['file_path'], 'r', encoding='utf-8') as f:
                all_lines = f.readlines()
                
            # Start from the method start line and find the matching closing brace
            # This is a simple approach and might not work for complex methods
            brace_count = 0
            found_opening = False
            
            for i in range(start_line - 1, len(all_lines)):
                line = all_lines[i]
                
                # Count opening braces
                brace_count += line.count('{')
                
                # Once we find the first opening brace, we're in the method body
                if brace_count > 0:
                    found_opening = True
                
                # Count closing braces
                brace_count -= line.count('}')
                
                # When brace count is 0 after finding an opening brace, we've reached the end
                if found_opening and brace_count == 0:
                    end_line = i + 1
                    break
        
        # If we still don't have an end line, make a guess
        if not end_line:
            end_line = start_line + 20  # Just a fallback
        
        # Read the source code
        with open(class_info['file_path'], 'r', encoding='utf-8') as f:
            all_lines = f.readlines()
            
        # Extract the method source code
        source_lines = all_lines[start_line - 1:end_line]
        source_code = ''.join(source_lines)
        
        # Analyze execution paths
        execution_paths = analyze_execution_paths(source_code, start_line)
        
        return jsonify({
            'success': True, 
            'method': {
                'name': method['name'],
                'signature': method['signature'],
                'class_name': class_info['name'],
                'package': class_info['package'],
                'start_line': start_line,
                'end_line': end_line,
                'source_code': source_code,
                'execution_paths': execution_paths
            }
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

def analyze_execution_paths(source_code, start_line):
    """
    Analyze source code to identify different execution paths
    Returns an array of execution path objects
    """
    paths = []
    
    # Regex patterns for control structures
    if_pattern = re.compile(r'if\s*\((.*?)\)', re.DOTALL)
    else_pattern = re.compile(r'}\s*else\s*{', re.DOTALL)
    else_if_pattern = re.compile(r'}\s*else\s+if\s*\((.*?)\)', re.DOTALL)
    for_pattern = re.compile(r'for\s*\((.*?)\)', re.DOTALL)
    while_pattern = re.compile(r'while\s*\((.*?)\)', re.DOTALL)
    switch_pattern = re.compile(r'switch\s*\((.*?)\)', re.DOTALL)
    case_pattern = re.compile(r'case\s+(.*?):', re.DOTALL)
    try_pattern = re.compile(r'try\s*{', re.DOTALL)
    catch_pattern = re.compile(r'}\s*catch\s*\((.*?)\)', re.DOTALL)
    
    # Find conditional statements
    if_matches = if_pattern.finditer(source_code)
    for match in if_matches:
        line_num = start_line + source_code[:match.start()].count('\n')
        paths.append({
            'type': 'if',
            'condition': match.group(1).strip(),
            'line': line_num,
            'description': f"Conditional branch: if ({match.group(1).strip()})"
        })
    
    # Find else statements
    else_matches = else_pattern.finditer(source_code)
    for match in else_matches:
        line_num = start_line + source_code[:match.start()].count('\n')
        paths.append({
            'type': 'else',
            'line': line_num,
            'description': "Alternative branch: else"
        })
    
    # Find else-if statements
    else_if_matches = else_if_pattern.finditer(source_code)
    for match in else_if_matches:
        line_num = start_line + source_code[:match.start()].count('\n')
        paths.append({
            'type': 'else_if',
            'condition': match.group(1).strip(),
            'line': line_num,
            'description': f"Alternative conditional branch: else if ({match.group(1).strip()})"
        })
    
    # Find for loops
    for_matches = for_pattern.finditer(source_code)
    for match in for_matches:
        line_num = start_line + source_code[:match.start()].count('\n')
        paths.append({
            'type': 'for',
            'condition': match.group(1).strip(),
            'line': line_num,
            'description': f"Loop: for ({match.group(1).strip()})"
        })
    
    # Find while loops
    while_matches = while_pattern.finditer(source_code)
    for match in while_matches:
        line_num = start_line + source_code[:match.start()].count('\n')
        paths.append({
            'type': 'while',
            'condition': match.group(1).strip(),
            'line': line_num,
            'description': f"Loop: while ({match.group(1).strip()})"
        })
    
    # Find switch statements
    switch_matches = switch_pattern.finditer(source_code)
    for match in switch_matches:
        line_num = start_line + source_code[:match.start()].count('\n')
        paths.append({
            'type': 'switch',
            'condition': match.group(1).strip(),
            'line': line_num,
            'description': f"Branch: switch ({match.group(1).strip()})"
        })
    
    # Find case statements
    case_matches = case_pattern.finditer(source_code)
    for match in case_matches:
        line_num = start_line + source_code[:match.start()].count('\n')
        paths.append({
            'type': 'case',
            'condition': match.group(1).strip(),
            'line': line_num,
            'description': f"Case: {match.group(1).strip()}"
        })
    
    # Find try blocks
    try_matches = try_pattern.finditer(source_code)
    for match in try_matches:
        line_num = start_line + source_code[:match.start()].count('\n')
        paths.append({
            'type': 'try',
            'line': line_num,
            'description': "Exception handling: try block"
        })
    
    # Find catch blocks
    catch_matches = catch_pattern.finditer(source_code)
    for match in catch_matches:
        line_num = start_line + source_code[:match.start()].count('\n')
        paths.append({
            'type': 'catch',
            'condition': match.group(1).strip(),
            'line': line_num,
            'description': f"Exception handler: catch ({match.group(1).strip()})"
        })
    
    # Sort paths by line number
    paths.sort(key=lambda x: x['line'])
    
    return paths

def run_app():
    """Run the Flask application"""
    # Ensure the default database exists and tables are created
    default_db_path = os.path.join(DB_DIR, DEFAULT_DB_NAME)
    if not os.path.exists(default_db_path):
        open(default_db_path, 'w').close()  # Create empty file
    
    create_tables()
    
    # Start the Flask application
    FlaskUI(app=app, server='flask', width=1000, height=800).run()

if __name__ == '__main__':
    run_app() 