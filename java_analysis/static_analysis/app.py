import os
import glob
import sqlite3
from flask import Flask, render_template, jsonify, request
from flask_sqlalchemy import SQLAlchemy
from sqlalchemy import create_engine, text

app = Flask(__name__)
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///java_analysis.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db = SQLAlchemy(app)

def get_available_databases():
    """Get list of SQLite databases in the parent directory"""
    # Get the parent directory path
    parent_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    # Look for .db files in the parent directory
    return [os.path.basename(db) for db in glob.glob(os.path.join(parent_dir, '*.db'))]

def get_method_classes():
    """Get list of classes with methods from the database"""
    try:
        db_path = app.config['SQLALCHEMY_DATABASE_URI'].replace('sqlite:///', '')
        if not os.path.exists(db_path):
            return []
            
        with sqlite3.connect(db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT DISTINCT c.package_name, c.class_name
                FROM classes c
                JOIN methods m ON c.id = m.class_id
                ORDER BY c.package_name, c.class_name
            """)
            return [{'package': row[0], 'class': row[1]} for row in cursor.fetchall()]
    except Exception as e:
        print(f"Error getting method classes: {e}")
        return []

def search_methods(query):
    """Search for methods matching the query"""
    try:
        with sqlite3.connect(app.config['SQLALCHEMY_DATABASE_URI'].replace('sqlite:///', '')) as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT DISTINCT 
                    c.package_name,
                    c.class_name,
                    m.method_name,
                    m.parameters,
                    m.is_static
                FROM methods m
                JOIN classes c ON m.class_id = c.id
                WHERE m.method_name LIKE ? OR c.class_name LIKE ?
                ORDER BY c.package_name, c.class_name, m.method_name
            """, (f'%{query}%', f'%{query}%'))
            return [{
                'package': row[0],
                'class': row[1],
                'method': row[2],
                'parameters': row[3],
                'is_static': bool(row[4])
            } for row in cursor.fetchall()]
    except Exception as e:
        print(f"Error searching methods: {e}")
        return []

def get_method_call_stack(package_name, class_name, method_name, parameters):
    """Get the call stack for a specific method"""
    try:
        with sqlite3.connect(app.config['SQLALCHEMY_DATABASE_URI'].replace('sqlite:///', '')) as conn:
            cursor = conn.cursor()
            cursor.execute("""
                    SELECT 
                        m.id,
                        c.package_name,
                        c.class_name,
                        m.method_name,
                        m.parameters,
                        m.is_static,
                        mc.line_number,
                        0 as depth
                    FROM methods m
                    JOIN classes c ON m.class_id = c.id
                    JOIN method_calls mc ON m.id = mc.called_method_id
                    WHERE mc.caller_method_id IN (
                       SELECT id FROM methods WHERE package_name = ? AND class_name = ? AND method_name = ? AND parameters = ?
                    )
                    ORDER BY depth, line_number
            """, (package_name, class_name, method_name, parameters))
            
            rows = cursor.fetchall()
            seen = set()
            result = []
            for row in rows:
                key = (row[1], row[2], row[3], row[4], row[6], row[7])  # package, class, method, parameters, line_number, depth
                if key not in seen:
                    seen.add(key)
                    result.append({
                        'package': row[1],
                        'class': row[2],
                        'method': row[3],
                        'parameters': row[4],
                        'is_static': bool(row[5]),
                        'line_number': row[6],
                        'depth': row[7]
                    })
            return result
    except Exception as e:
        print(f"Error getting method call stack: {e}")
        return []

@app.route('/')
def index():
    databases = get_available_databases()
    return render_template('index.html', databases=databases)

@app.route('/api/classes')
def get_classes():
    return jsonify(get_method_classes())

@app.route('/api/search')
def search():
    query = request.args.get('q', '')
    return jsonify(search_methods(query))

@app.route('/api/callers')
def get_callers():
    package = request.args.get('package')
    class_name = request.args.get('class')
    method = request.args.get('method')
    parameters = request.args.get('parameters', '[]')
    try:
        with sqlite3.connect(app.config['SQLALCHEMY_DATABASE_URI'].replace('sqlite:///', '')) as conn:
            cursor = conn.cursor()
            cursor.execute('''
                SELECT 
                    mc.line_number,
                    caller.package_name,
                    caller.class_name,
                    caller_method.method_name,
                    caller_method.parameters
                FROM method_calls mc
                JOIN methods called_method ON mc.called_method_id = called_method.id
                JOIN methods caller_method ON mc.caller_method_id = caller_method.id
                JOIN classes caller ON caller_method.class_id = caller.id
                WHERE called_method.method_name = ?
                  AND called_method.parameters = ?
                  AND called_method.class_id = (SELECT id FROM classes WHERE package_name = ? AND class_name = ?)
            ''', (method, parameters, package, class_name))
            return jsonify([
                {
                    'line_number': row[0],
                    'package': row[1],
                    'class': row[2],
                    'method': row[3],
                    'parameters': row[4]
                } for row in cursor.fetchall()
            ])
    except Exception as e:
        print(f"Error getting callers: {e}")
        return jsonify([])

@app.route('/api/call-stack')
def call_stack():
    package = request.args.get('package')
    class_name = request.args.get('class')
    method = request.args.get('method')
    parameters = request.args.get('parameters', '[]')
    direction = request.args.get('direction', 'out')
    if direction == 'both':
        # Get outgoing call stack (callees)
        callees = get_method_call_stack(package, class_name, method, parameters)
        # Get incoming callers
        with sqlite3.connect(app.config['SQLALCHEMY_DATABASE_URI'].replace('sqlite:///', '')) as conn:
            cursor = conn.cursor()
            cursor.execute('''
                SELECT 
                    mc.line_number,
                    caller.package_name,
                    caller.class_name,
                    caller_method.method_name,
                    caller_method.parameters
                FROM method_calls mc
                JOIN methods called_method ON mc.called_method_id = called_method.id
                JOIN methods caller_method ON mc.caller_method_id = caller_method.id
                JOIN classes caller ON caller_method.class_id = caller.id
                WHERE called_method.method_name = ?
                  AND called_method.parameters = ?
                  AND called_method.class_id = (SELECT id FROM classes WHERE package_name = ? AND class_name = ?)
            ''', (method, parameters, package, class_name))
            callers = [
                {
                    'line_number': row[0],
                    'package': row[1],
                    'class': row[2],
                    'method': row[3],
                    'parameters': row[4],
                    'direction': 'caller'
                } for row in cursor.fetchall()
            ]
        # Mark callees with direction
        for c in callees:
            c['direction'] = 'callee'
        return jsonify(callers + callees)
    else:
        return jsonify(get_method_call_stack(package, class_name, method, parameters))

@app.route('/api/methods')
def get_methods():
    package = request.args.get('package')
    class_name = request.args.get('class')
    
    if not package or not class_name:
        return jsonify([])
    
    try:
        with sqlite3.connect(app.config['SQLALCHEMY_DATABASE_URI'].replace('sqlite:///', '')) as conn:
            cursor = conn.cursor()
            cursor.execute("""
                SELECT 
                    m.method_name,
                    m.parameters,
                    m.is_static
                FROM methods m
                JOIN classes c ON m.class_id = c.id
                WHERE c.package_name = ? AND c.class_name = ?
                ORDER BY m.method_name
            """, (package, class_name))
            return [{
                'method': row[0],
                'parameters': row[1],
                'is_static': bool(row[2])
            } for row in cursor.fetchall()]
    except Exception as e:
        print(f"Error getting methods: {e}")
        return []

@app.route('/api/analyze', methods=['POST'])
def analyze():
    java_dir = request.json.get('javaDir')
    reset_db = request.json.get('resetDb', False)
    # TODO: Implement Java source analysis
    return jsonify({'status': 'success'})

@app.route('/api/change-db', methods=['POST'])
def change_database():
    database = request.json.get('database')
    if not database:
        return jsonify({'error': 'No database specified'}), 400
    
    try:
        # Get the parent directory path
        parent_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        db_path = os.path.join(parent_dir, database)
        
        if not os.path.exists(db_path):
            return jsonify({'error': f'Database {database} not found'}), 404
            
        # Update the database URI for future sqlite3.connect calls
        app.config['SQLALCHEMY_DATABASE_URI'] = f'sqlite:///{db_path}'
        
        # Get updated class list
        classes = get_method_classes()
        return jsonify({
            'status': 'success',
            'classes': classes
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(debug=True) 