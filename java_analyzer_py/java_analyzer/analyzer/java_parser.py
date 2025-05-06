import os
import javalang
from javalang.tree import ClassDeclaration, MethodDeclaration, MethodInvocation, FieldDeclaration, Import
from ..database import (
    add_class, get_class, add_method, get_method, add_method_call, 
    add_import, get_imports_by_class, add_field, update_method_call_resolution,
    get_all_classes, get_fields_by_class, get_methods_by_class
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
                                self.process_method_call(call_node, method_id, class_id, full_class_name, imports)
                            
                            # Process constructor calls
                            for _, constructor_node in method_node.filter(javalang.tree.ClassCreator):
                                self.process_constructor_call(constructor_node, method_id, class_id, full_class_name, imports)
                            
                            # Process method references
                            for _, ref_node in method_node.filter(javalang.tree.MethodReference):
                                self.process_method_reference(ref_node, method_id, class_id, full_class_name, imports)
                            
                            # Process lambda expressions
                            for _, lambda_node in method_node.filter(javalang.tree.LambdaExpression):
                                self.process_lambda_expression(lambda_node, method_id, class_id, full_class_name, imports)
                
            except Exception as e:
                print(f"Error parsing {file_path}: {str(e)}")
                
    def process_method_call(self, call_node, caller_method_id, class_id, caller_class_name, imports):
        """Process a method invocation node"""
        called_method = call_node.member
        line_number = call_node.position.line if call_node.position else None
        
        # Determine the called class
        called_class = None
        
        # If it's a qualified method call like obj.method() or Class.method()
        if call_node.qualifier:
            called_class = call_node.qualifier
        
        # Add the call to the database
        call_id = add_method_call(
            caller_method_id=caller_method_id,
            called_class=called_class,
            called_method=called_method,
            line_number=line_number
        )
        
        # Try to resolve the call
        resolved_method_id = self.resolve_method_call(
            call_node, class_id, caller_class_name, imports
        )
        
        if resolved_method_id:
            update_method_call_resolution(call_id, resolved_method_id)
            
    def process_constructor_call(self, constructor_node, caller_method_id, class_id, caller_class_name, imports):
        """Process a constructor call node"""
        line_number = constructor_node.position.line if constructor_node.position else None
        
        # Get the class being constructed
        called_class = constructor_node.type.name
        
        # Add the call to the database
        call_id = add_method_call(
            caller_method_id=caller_method_id,
            called_class=called_class,
            called_method="<init>",  # Special name for constructors
            line_number=line_number
        )
        
        # Try to resolve the constructor
        resolved_method_id = self.resolve_constructor_call(
            constructor_node, class_id, caller_class_name, imports
        )
        
        if resolved_method_id:
            update_method_call_resolution(call_id, resolved_method_id)
            
    def process_method_reference(self, ref_node, caller_method_id, class_id, caller_class_name, imports):
        """Process a method reference node"""
        line_number = ref_node.position.line if ref_node.position else None
        
        # Get the method being referenced
        called_method = ref_node.method
        called_class = None
        
        if ref_node.qualifier:
            called_class = ref_node.qualifier
            
        # Add the call to the database
        call_id = add_method_call(
            caller_method_id=caller_method_id,
            called_class=called_class,
            called_method=called_method,
            line_number=line_number
        )
        
        # Try to resolve the method reference
        resolved_method_id = self.resolve_method_reference(
            ref_node, class_id, caller_class_name, imports
        )
        
        if resolved_method_id:
            update_method_call_resolution(call_id, resolved_method_id)
            
    def process_lambda_expression(self, lambda_node, caller_method_id, class_id, caller_class_name, imports):
        """Process a lambda expression node"""
        # Process any method calls within the lambda body
        for _, call_node in lambda_node.filter(MethodInvocation):
            self.process_method_call(call_node, caller_method_id, class_id, caller_class_name, imports)
            
        # Process any constructor calls within the lambda body
        for _, constructor_node in lambda_node.filter(javalang.tree.ClassCreator):
            self.process_constructor_call(constructor_node, caller_method_id, class_id, caller_class_name, imports)
            
        # Process any method references within the lambda body
        for _, ref_node in lambda_node.filter(javalang.tree.MethodReference):
            self.process_method_reference(ref_node, caller_method_id, class_id, caller_class_name, imports)
            
    def resolve_constructor_call(self, constructor_node, class_id, caller_class_name, imports):
        """Resolve a constructor call to find the target constructor"""
        # Similar to resolve_method_call but specialized for constructors
        called_class = constructor_node.type.name
        
        # Try to resolve the class
        target_class_name = self.resolve_class_from_imports(called_class, imports, class_id)
        if not target_class_name:
            target_class_name = called_class
            
        target_class_id = self.get_class_id(target_class_name)
        if not target_class_id:
            return None
            
        # Get all constructors for the class
        methods = self.get_methods_by_class(target_class_id)
        for method in methods:
            if method['name'] == "<init>":  # Constructor name
                # Check if parameter types match
                method_params = self.parse_method_signature(method['signature'])
                arg_types = [self.resolve_type(arg.type) for arg in constructor_node.arguments]
                
                if len(method_params) == len(arg_types):
                    # Simple check - real IDEs do more sophisticated matching
                    return method['id']
                    
        return None
        
    def resolve_method_reference(self, ref_node, class_id, caller_class_name, imports):
        """Resolve a method reference to find the target method"""
        # Similar to resolve_method_call but specialized for method references
        method_name = ref_node.method
        qualifier = ref_node.qualifier
        
        # Create a synthetic method invocation node
        synthetic_call = javalang.tree.MethodInvocation(
            qualifier=qualifier,
            member=method_name,
            arguments=[]  # Method references don't have arguments
        )
        
        return self.resolve_method_call(synthetic_call, class_id, caller_class_name, imports)
    
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
        Resolve a method call to find the target method with improved accuracy.
        Similar to how Java IDEs resolve method calls.
        
        Args:
            call_node: The method invocation node
            class_id: The ID of the calling class
            caller_class_name: The fully qualified name of the calling class
            imports: List of imports for the calling class
            
        Returns:
            The ID of the resolved method, or None if not found
        """
        method_name = call_node.member
        qualifier = call_node.qualifier
        arguments = call_node.arguments
        
        # Step 1: Determine the target class
        target_class_name = None
        
        if qualifier:
            if qualifier == 'this':
                target_class_name = caller_class_name
            elif qualifier == 'super':
                # Get the superclass of the current class
                class_info = get_class(class_id)
                if class_info and class_info.get('superclass'):
                    target_class_name = class_info['superclass']
            else:
                # Try to resolve the qualifier as a class name
                target_class_name = self.resolve_class_from_imports(qualifier, imports, class_id)
                
                # If not found in imports, check if it's a field in the current class
                if not target_class_name:
                    field_type = self.resolve_field_type(class_id, qualifier)
                    if field_type:
                        target_class_name = field_type
        else:
            # No qualifier - could be a method in the current class or a static import
            target_class_name = caller_class_name
        
        if not target_class_name:
            return None
            
        # Step 2: Get the target class ID
        target_class_id = self.get_class_id(target_class_name)
        if not target_class_id:
            return None
            
        # Step 3: Build method signature based on arguments
        arg_types = []
        for arg in arguments:
            if hasattr(arg, 'type'):
                arg_type = self.resolve_type(arg.type)
            else:
                # For complex expressions, we'll need to infer the type
                arg_type = self.infer_expression_type(arg, class_id, imports)
            arg_types.append(arg_type)
            
        # Step 4: Find matching methods in the target class
        methods = get_methods_by_class(target_class_id)
        best_match = None
        best_score = 0
        
        for method in methods:
            if method['name'] != method_name:
                continue
                
            # Get method parameter types
            method_params = self.parse_method_signature(method['signature'])
            
            # Calculate match score based on parameter types
            score = self.calculate_method_match_score(method_params, arg_types)
            
            if score > best_score:
                best_score = score
                best_match = method
                
        if best_match:
            return best_match['id']
            
        # Step 5: If no match found, check superclasses and interfaces
        class_info = get_class(target_class_id)
        if class_info:
            # Check superclass
            if class_info.get('superclass'):
                superclass_id = self.get_class_id(class_info['superclass'])
                if superclass_id:
                    resolved_id = self.resolve_method_call(call_node, superclass_id, 
                                                         class_info['superclass'], imports)
                    if resolved_id:
                        return resolved_id
                        
            # Check interfaces
            for interface in class_info.get('interfaces', []):
                interface_id = self.get_class_id(interface)
                if interface_id:
                    resolved_id = self.resolve_method_call(call_node, interface_id, 
                                                         interface, imports)
                    if resolved_id:
                        return resolved_id
                        
        return None
        
    def resolve_type(self, type_node):
        """Resolve a type node to its fully qualified name"""
        if not type_node:
            return None
            
        if hasattr(type_node, 'name'):
            base_type = type_node.name
        else:
            base_type = str(type_node)
            
        # Handle array types
        if hasattr(type_node, 'dimensions') and type_node.dimensions:
            return f"{base_type}[]"
            
        # Handle generic types
        if hasattr(type_node, 'type_arguments') and type_node.type_arguments:
            type_args = [self.resolve_type(arg) for arg in type_node.type_arguments]
            return f"{base_type}<{', '.join(type_args)}>"
            
        return base_type
        
    def infer_expression_type(self, expr_node, class_id, imports):
        """
        Infer the type of an expression node.
        This is a simplified version - real IDEs do much more sophisticated type inference.
        """
        if hasattr(expr_node, 'type'):
            return self.resolve_type(expr_node.type)
            
        if isinstance(expr_node, javalang.tree.Literal):
            if isinstance(expr_node.value, str):
                return "java.lang.String"
            elif isinstance(expr_node.value, bool):
                return "boolean"
            elif isinstance(expr_node.value, int):
                return "int"
            elif isinstance(expr_node.value, float):
                return "double"
                
        if isinstance(expr_node, javalang.tree.MethodInvocation):
            # If it's a method call, try to resolve the return type
            resolved_method_id = self.resolve_method_call(expr_node, class_id, 
                                                        self.get_class_name(class_id), imports)
            if resolved_method_id:
                method = get_method(resolved_method_id)
                if method:
                    return method['return_type']
                    
        return "java.lang.Object"  # Default to Object if we can't determine the type
        
    def parse_method_signature(self, signature):
        """Parse a method signature string into parameter types"""
        # Extract the parameter part between parentheses
        params_str = signature[signature.find('(')+1:signature.rfind(')')]
        if not params_str:
            return []
            
        # Split by comma and clean up each parameter
        params = []
        for param in params_str.split(','):
            param = param.strip()
            # Remove parameter name if present
            if ' ' in param:
                param = param.rsplit(' ', 1)[0]
            params.append(param)
            
        return params
        
    def calculate_method_match_score(self, method_params, arg_types):
        """
        Calculate how well the method parameters match the argument types.
        Returns a score between 0 and 1, where 1 is a perfect match.
        """
        if len(method_params) != len(arg_types):
            return 0
            
        score = 0
        for method_param, arg_type in zip(method_params, arg_types):
            if method_param == arg_type:
                score += 1
            elif self.is_subtype(arg_type, method_param):
                score += 0.8
            elif self.is_convertible(arg_type, method_param):
                score += 0.5
                
        return score / len(method_params)
        
    def is_subtype(self, type1, type2):
        """Check if type1 is a subtype of type2"""
        # This is a simplified version - real IDEs have more sophisticated type hierarchy checks
        common_subtypes = {
            'int': ['long', 'float', 'double'],
            'long': ['float', 'double'],
            'float': ['double'],
            'byte': ['short', 'int', 'long', 'float', 'double'],
            'short': ['int', 'long', 'float', 'double'],
            'char': ['int', 'long', 'float', 'double']
        }
        
        if type1 == type2:
            return True
            
        if type1 in common_subtypes and type2 in common_subtypes[type1]:
            return True
            
        return False
        
    def is_convertible(self, type1, type2):
        """Check if type1 can be converted to type2"""
        # This is a simplified version - real IDEs have more sophisticated conversion rules
        convertible_types = {
            'int': ['Integer', 'Number', 'Object'],
            'long': ['Long', 'Number', 'Object'],
            'float': ['Float', 'Number', 'Object'],
            'double': ['Double', 'Number', 'Object'],
            'boolean': ['Boolean', 'Object'],
            'char': ['Character', 'Object'],
            'byte': ['Byte', 'Number', 'Object'],
            'short': ['Short', 'Number', 'Object']
        }
        
        if type1 in convertible_types and type2 in convertible_types[type1]:
            return True
            
        return False
    
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

    def resolve_field_type(self, class_id, field_name):
        """
        Resolve the type of a field in a class.
        
        Args:
            class_id: The ID of the class containing the field
            field_name: The name of the field to resolve
            
        Returns:
            The fully qualified type name of the field, or None if not found
        """
        # First check if it's a field in the current class
        fields = get_fields_by_class(class_id)
        for field in fields:
            if field['name'] == field_name:
                return field['type_name']
                
        # If not found, check superclass
        class_info = get_class(class_id)
        if class_info and class_info.get('superclass'):
            superclass_id = self.get_class_id(class_info['superclass'])
            if superclass_id:
                return self.resolve_field_type(superclass_id, field_name)
                
        return None

    def get_methods_by_class(self, class_id):
        """
        Get all methods belonging to a class.
        
        Args:
            class_id: The ID of the class
            
        Returns:
            List of method dictionaries containing method information
        """
        # First get methods directly in the class
        methods = []
        
        # Get methods from database
        class_methods = get_methods_by_class(class_id)
        if class_methods:
            methods.extend(class_methods)
            
        # Get methods from superclass
        class_info = get_class(class_id)
        if class_info and class_info.get('superclass'):
            superclass_id = self.get_class_id(class_info['superclass'])
            if superclass_id:
                superclass_methods = self.get_methods_by_class(superclass_id)
                if superclass_methods:
                    methods.extend(superclass_methods)
                    
        # Get methods from interfaces
        if class_info and class_info.get('interfaces'):
            for interface in class_info['interfaces']:
                interface_id = self.get_class_id(interface)
                if interface_id:
                    interface_methods = self.get_methods_by_class(interface_id)
                    if interface_methods:
                        methods.extend(interface_methods)
                        
        return methods

    def get_class_name(self, class_id):
        """
        Get the fully qualified name of a class from its ID.
        
        Args:
            class_id: The ID of the class
            
        Returns:
            The fully qualified name of the class
        """
        class_info = get_class(class_id)
        if class_info:
            package = class_info.get('package', '')
            name = class_info.get('name', '')
            if package:
                return f"{package}.{name}"
            return name
        return None 