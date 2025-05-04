import os
import javalang
from javalang.tree import ClassDeclaration, MethodDeclaration, MethodInvocation, FieldDeclaration, Import
from ..database import (
    add_class, get_class, add_method, get_method, add_method_call, 
    add_import, get_imports_by_class, add_field, update_method_call_resolution,
    get_all_classes
)

class JavaAnalyzer:
    """Analyzes Java source code files and populates the database"""
    
    def __init__(self, db_path=None):
        """Initialize the analyzer"""
        self.source_dir = None
        self.class_cache = {}  # Cache for class lookups
        self.method_cache = {}  # Cache for method lookups
        self.package_class_cache = {}  # Cache for package-to-classes mapping
        self.wildcard_imports = {}  # Cache for wildcard imports
        self.static_imports = {}  # Cache for static imports
        
    def analyze_directory(self, source_dir):
        """Analyze all Java files in a directory recursively"""
        self.source_dir = os.path.abspath(source_dir)
        
        # Pre-scan to build package mapping
        self.build_package_mapping()
        
        # First pass: analyze all files to build class and method database
        for root, _, files in os.walk(source_dir):
            for file in files:
                if file.endswith('.java'):
                    file_path = os.path.join(root, file)
                    try:
                        self.analyze_file(file_path, first_pass=True)
                    except Exception as e:
                        print(f"Error analyzing file {file_path}: {str(e)}")
        
        # Second pass: resolve method calls
        for root, _, files in os.walk(source_dir):
            for file in files:
                if file.endswith('.java'):
                    file_path = os.path.join(root, file)
                    try:
                        self.analyze_file(file_path, first_pass=False)
                    except Exception as e:
                        print(f"Error resolving calls in file {file_path}: {str(e)}")
    
    def build_package_mapping(self):
        """Build a mapping of packages to potential classes for wildcard import resolution"""
        # First get all classes from the database (in case we're adding to an existing analysis)
        all_classes = get_all_classes()
        
        for class_info in all_classes:
            package = class_info.get('package')
            class_name = class_info.get('name')
            
            if package and class_name:
                if package not in self.package_class_cache:
                    self.package_class_cache[package] = {}
                
                self.package_class_cache[package][class_name] = class_info['id']
        
        # Then scan all .java files to build initial package mapping
        for root, _, files in os.walk(self.source_dir):
            for file in files:
                if file.endswith('.java'):
                    file_path = os.path.join(root, file)
                    try:
                        with open(file_path, 'r', encoding='utf-8') as f:
                            content = f.read()
                            tree = javalang.parse.parse(content)
                            
                            # Extract package
                            package = tree.package.name if tree.package else None
                            if not package:
                                continue
                                
                            # Process classes
                            for path, node in tree.filter(ClassDeclaration):
                                class_name = node.name
                                
                                # Add to package mapping
                                if package not in self.package_class_cache:
                                    self.package_class_cache[package] = {}
                                
                                self.package_class_cache[package][class_name] = None  # We'll fill the ID later
                    except Exception as e:
                        print(f"Error pre-scanning file {file_path}: {str(e)}")
    
    def analyze_file(self, file_path, first_pass=True):
        """Analyze a single Java file"""
        print(f"Analyzing {'classes in' if first_pass else 'method calls in'} {file_path}")
        
        with open(file_path, 'r', encoding='utf-8') as f:
            try:
                content = f.read()
                tree = javalang.parse.parse(content)
                
                # Extract package
                package = tree.package.name if tree.package else None
                
                # Process imports in first pass
                if first_pass:
                    imports = []
                    for imp in tree.imports:
                        imports.append({
                            'path': imp.path,
                            'wildcard': imp.wildcard,
                            'static': imp.static if hasattr(imp, 'static') else False
                        })
                
                # Process classes
                for path, node in tree.filter(ClassDeclaration):
                    class_name = node.name
                    
                    if first_pass:
                        # First pass: Add class and its members
                        is_interface = isinstance(node, javalang.tree.InterfaceDeclaration)
                        is_abstract = 'abstract' in node.modifiers
                        
                        class_id = add_class(
                            name=class_name,
                            package=package,
                            file_path=file_path,
                            is_interface=is_interface,
                            is_abstract=is_abstract
                        )
                        
                        # Cache the class for efficient lookups
                        full_class_name = f"{package}.{class_name}" if package else class_name
                        self.class_cache[full_class_name] = class_id
                        
                        # Update package mapping with class ID
                        if package in self.package_class_cache and class_name in self.package_class_cache[package]:
                            self.package_class_cache[package][class_name] = class_id
                        
                        # Add imports
                        for imp in imports:
                            import_path = imp['path']
                            is_wildcard = imp['wildcard']
                            is_static = imp['static']
                            
                            # Track wildcard imports for this class
                            if is_wildcard:
                                if not is_static:
                                    # Regular wildcard import (import package.*)
                                    if class_id not in self.wildcard_imports:
                                        self.wildcard_imports[class_id] = []
                                    self.wildcard_imports[class_id].append(import_path)
                                else:
                                    # Static wildcard import (import static package.Class.*)
                                    if class_id not in self.static_imports:
                                        self.static_imports[class_id] = []
                                    
                                    # For static wildcards, the path is typically package.Class
                                    self.static_imports[class_id].append(import_path)
                            
                            add_import(
                                class_id=class_id,
                                import_path=import_path,
                                is_wildcard=is_wildcard,
                                is_static=is_static
                            )
                        
                        # Add fields
                        for _, field_node in tree.filter(FieldDeclaration):
                            for declarator in field_node.declarators:
                                is_static = 'static' in field_node.modifiers
                                field_type = field_node.type.name if hasattr(field_node.type, 'name') else str(field_node.type)
                                add_field(
                                    class_id=class_id,
                                    name=declarator.name,
                                    type_name=field_type,
                                    is_static=is_static
                                )
                        
                        # Add methods
                        for _, method_node in node.filter(MethodDeclaration):
                            method_name = method_node.name
                            
                            # Build method signature
                            params = []
                            for param in method_node.parameters:
                                param_type = param.type.name if hasattr(param.type, 'name') else str(param.type)
                                params.append(f"{param_type} {param.name}")
                            
                            signature = f"{method_name}({', '.join(params)})"
                            
                            # Get method modifiers
                            is_static = 'static' in method_node.modifiers
                            is_public = 'public' in method_node.modifiers
                            
                            # Get method position
                            start_line = method_node.position.line if method_node.position else None
                            end_line = None  # Javalang doesn't provide end position
                            
                            return_type = method_node.return_type.name if hasattr(method_node.return_type, 'name') and method_node.return_type else "void"
                            
                            method_id = add_method(
                                class_id=class_id,
                                name=method_name,
                                signature=signature,
                                return_type=return_type,
                                is_static=is_static,
                                is_public=is_public,
                                start_line=start_line,
                                end_line=end_line
                            )
                            
                            # Cache the method
                            method_key = f"{full_class_name}.{signature}"
                            self.method_cache[method_key] = method_id
                    
                    else:
                        # Second pass: Process method calls
                        full_class_name = f"{package}.{class_name}" if package else class_name
                        class_id = self.get_class_id(full_class_name)
                        
                        if not class_id:
                            continue
                        
                        # Get imports for this class to help resolve calls
                        imports = get_imports_by_class(class_id)
                        
                        # Process methods
                        for _, method_node in node.filter(MethodDeclaration):
                            method_name = method_node.name
                            
                            # Build method signature
                            params = []
                            for param in method_node.parameters:
                                param_type = param.type.name if hasattr(param.type, 'name') else str(param.type)
                                params.append(f"{param_type} {param.name}")
                            
                            signature = f"{method_name}({', '.join(params)})"
                            method_key = f"{full_class_name}.{signature}"
                            
                            method_id = self.method_cache.get(method_key)
                            
                            if not method_id:
                                continue
                            
                            # Process method invocations
                            for _, call_node in method_node.filter(MethodInvocation):
                                called_method = call_node.member
                                line_number = call_node.position.line if call_node.position else None
                                
                                # Determine the called class
                                called_class = None
                                
                                # If it's a qualified method call like obj.method() or Class.method()
                                if call_node.qualifier:
                                    called_class = call_node.qualifier
                                
                                # Add the call to the database
                                call_id = add_method_call(
                                    caller_method_id=method_id,
                                    called_class=called_class,
                                    called_method=called_method,
                                    line_number=line_number
                                )
                                
                                # Try to resolve the call
                                resolved_method_id = self.resolve_method_call(
                                    call_node, class_id, full_class_name, imports
                                )
                                
                                if resolved_method_id:
                                    update_method_call_resolution(call_id, resolved_method_id)
                
            except Exception as e:
                print(f"Error parsing {file_path}: {str(e)}")
    
    def get_class_id(self, full_class_name):
        """Get class ID from cache or database"""
        if full_class_name in self.class_cache:
            return self.class_cache[full_class_name]
        
        # Try to split and find
        if '.' in full_class_name:
            package, class_name = full_class_name.rsplit('.', 1)
            
            # Check package cache first
            if package in self.package_class_cache and class_name in self.package_class_cache[package]:
                class_id = self.package_class_cache[package][class_name]
                if class_id:
                    self.class_cache[full_class_name] = class_id
                    return class_id
            
            class_info = get_class(name=class_name, package=package)
            if class_info:
                self.class_cache[full_class_name] = class_info['id']
                # Also update package cache
                if package not in self.package_class_cache:
                    self.package_class_cache[package] = {}
                self.package_class_cache[package][class_name] = class_info['id']
                return class_info['id']
        else:
            class_info = get_class(name=full_class_name)
            if class_info:
                self.class_cache[full_class_name] = class_info['id']
                return class_info['id']
        
        return None
    
    def resolve_method_call(self, call_node, class_id, caller_class_name, imports):
        """
        Resolve a method call to find the target method
        
        call_node: The method invocation node
        class_id: The ID of the calling class
        caller_class_name: The fully qualified name of the calling class
        imports: List of imports for the calling class
        """
        method_name = call_node.member
        
        # If we have a qualifier, try to resolve it directly
        if call_node.qualifier:
            # First check if it's a reference to the current class
            if call_node.qualifier == 'this':
                # It's a call to the current class
                called_class_name = caller_class_name
            else:
                # Try to find the class in imports
                called_class_name = self.resolve_class_from_imports(call_node.qualifier, imports, class_id)
                
                if not called_class_name:
                    # It might be a fully qualified class name already
                    called_class_name = call_node.qualifier
            
            if called_class_name:
                # Create a method signature approximation
                # This is not perfect since we don't know parameter types from the call
                # Would need symbol table analysis for perfect resolution
                signature = f"{method_name}("
                if call_node.arguments:
                    signature += "..."  # Just indicate there are arguments
                signature += ")"
                
                # Try to find the method
                class_id = self.get_class_id(called_class_name)
                if class_id:
                    # Get methods with this name from the class
                    method = get_method(class_id=class_id, name=method_name)
                    if method:
                        return method['id']
        
        # If no qualifier, it could be a method in the current class or statically imported
        else:
            # First check if it's a method in the current class
            class_id = self.get_class_id(caller_class_name)
            if class_id:
                method = get_method(class_id=class_id, name=method_name)
                if method:
                    return method['id']
            
            # Check for statically imported methods
            if class_id in self.static_imports:
                for static_import_path in self.static_imports[class_id]:
                    # For a static import like 'import static package.Class.method'
                    # or 'import static package.Class.*'
                    if '.' in static_import_path:
                        # Split the path to get the class
                        potential_class_path = static_import_path
                        
                        # If the static import ends with the method name,
                        # then it's a direct static import of that method
                        if static_import_path.endswith('.' + method_name):
                            # Strip the method name to get the class
                            potential_class_path = static_import_path.rsplit('.', 1)[0]
                        
                        potential_class_id = self.get_class_id(potential_class_path)
                        if potential_class_id:
                            # Look for a static method with this name
                            static_method = get_method(class_id=potential_class_id, name=method_name)
                            if static_method:
                                return static_method['id']
        
        return None
    
    def resolve_class_from_imports(self, class_name, imports, current_class_id):
        """
        Resolve a class name using the imports
        
        class_name: The class name to resolve
        imports: List of imports for the calling class
        current_class_id: The ID of the class containing this reference
        """
        # Check regular imports first
        for imp in imports:
            import_path = imp['import_path']
            is_wildcard = imp['is_wildcard']
            is_static = imp.get('is_static', False)
            
            if not is_wildcard and not is_static and import_path.endswith('.' + class_name):
                return import_path
            
            # For static imports, ignore them here (handled in resolve_method_call)
            if is_static:
                continue
            
            if is_wildcard:
                # For wildcard imports, check if the package contains this class
                package = import_path
                
                # Check the package cache
                if package in self.package_class_cache and class_name in self.package_class_cache[package]:
                    class_id = self.package_class_cache[package][class_name]
                    if class_id:
                        full_class_name = f"{package}.{class_name}"
                        return full_class_name
                
                # If not in cache, try a more thorough search
                full_class_name = f"{package}.{class_name}"
                
                # Check if this class exists in our database
                class_id = self.get_class_id(full_class_name)
                if class_id:
                    return full_class_name
                
                # If not found, we might need to check for inner classes or qualified names
                # This is a more complex case that would require additional analysis
        
        # If not found in imports, check if it's a reference to a class in the same package
        class_info = get_class(current_class_id)
        if class_info and class_info.get('package'):
            same_package_class = f"{class_info['package']}.{class_name}"
            if self.get_class_id(same_package_class):
                return same_package_class
        
        # If all else fails, check if it's a built-in Java class (java.lang package)
        java_lang_class = f"java.lang.{class_name}"
        common_java_classes = ["String", "Object", "Integer", "Boolean", "Double", "Float",
                               "Long", "Short", "Byte", "Character", "Thread", "Exception"]
        
        if class_name in common_java_classes:
            return java_lang_class
        
        return None 