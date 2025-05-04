document.addEventListener('DOMContentLoaded', function() {
    // Initialize highlight.js
    hljs.configure({
        languages: ['java']
    });
    
    // Initialize
    loadDatabasePath();
    loadAvailableDatabases();
    loadClasses();
    
    // Load user preferences from localStorage
    const hideUntraceableMethodsStored = localStorage.getItem('hideUntraceableMethods');
    // Default to true if not set in localStorage
    const hideUntraceableMethods = hideUntraceableMethodsStored === null ? true : hideUntraceableMethodsStored === 'true';
    document.getElementById('hide-untraceable-methods').checked = hideUntraceableMethods;
    
    // If this is the first time, save the default preference
    if (hideUntraceableMethodsStored === null) {
        localStorage.setItem('hideUntraceableMethods', 'true');
    }
    
    // Database selection handlers
    document.getElementById('database-select').addEventListener('change', function() {
        const selectedDb = this.value;
        if (selectedDb) {
            switchDatabase(selectedDb);
        }
    });
    
    document.getElementById('create-db-btn').addEventListener('click', function() {
        const newDbName = document.getElementById('new-db-name').value.trim();
        if (newDbName) {
            if (!newDbName.endsWith('.db')) {
                createDatabase(newDbName + '.db');
            } else {
                createDatabase(newDbName);
            }
        } else {
            showAlert('Please enter a name for the new database', 'warning');
        }
    });
    
    // Form submit handlers
    document.getElementById('analyze-form').addEventListener('submit', function(e) {
        e.preventDefault();
        analyzeProject();
    });
    
    document.getElementById('search-form').addEventListener('submit', function(e) {
        e.preventDefault();
        searchMethods();
    });
    
    // Home button click handler
    document.getElementById('home-button').addEventListener('click', function() {
        navigateHome();
    });
    
    // Copy source code button handler
    document.getElementById('copy-source-code').addEventListener('click', function() {
        copySourceCode();
    });
    
    // Hide untraceable methods toggle handler
    document.getElementById('hide-untraceable-methods').addEventListener('change', function(e) {
        const hideUntraceable = e.target.checked;
        localStorage.setItem('hideUntraceableMethods', hideUntraceable);
        
        // Refresh the current view if we're on a trace view
        const callTraceContainer = document.getElementById('call-trace');
        if (!callTraceContainer.classList.contains('d-none') && methodTraceHistory.length > 0) {
            const lastMethod = methodTraceHistory[methodTraceHistory.length - 1];
            traceMethod(lastMethod.id, lastMethod.name, true);
        }
    });
    
    // Handle browser back button
    window.addEventListener('popstate', function(event) {
        if (event.state) {
            handleHistoryNavigation(event.state);
        } else {
            navigateHome();
        }
    });
    
    // Check if we have a state in URL (for page refreshes)
    const urlParams = new URLSearchParams(window.location.search);
    const view = urlParams.get('view');
    const methodId = urlParams.get('methodId');
    const methodName = urlParams.get('methodName');
    const classId = urlParams.get('classId');
    const className = urlParams.get('className');
    
    if (view && view === 'trace' && methodId && methodName) {
        traceMethod(methodId, methodName, true);
    } else if (view && view === 'methods' && classId && className) {
        loadMethods(classId, className, true);
    }
});

// Hide method source and clear its content
function hideMethodSource() {
    const methodSourceContainer = document.getElementById('method-source');
    methodSourceContainer.classList.add('d-none');
    
    // Clear content
    document.getElementById('source-method-name').textContent = '';
    document.getElementById('source-file-location').textContent = '';
    document.getElementById('method-source-code').innerHTML = '';
    
    // Clear execution paths
    document.getElementById('execution-paths-overlay').innerHTML = '';
    document.getElementById('execution-paths-list').innerHTML = '';
    document.getElementById('execution-paths-legend').classList.add('d-none');
    
    // Reset toggle button
    const toggleButton = document.getElementById('toggle-execution-paths');
    toggleButton.innerHTML = '<i class="fas fa-code-branch me-1"></i> Show Execution Paths';
    toggleButton.disabled = false;
}

// Navigate to home view (main results page)
function navigateHome() {
    // Hide all content sections initially
    document.getElementById('method-list-container').classList.add('d-none');
    document.getElementById('call-trace').classList.add('d-none');
    document.getElementById('no-results').classList.add('d-none');
    hideMethodSource();
    
    // Clear method trace history
    methodTraceHistory.length = 0;
    
    // Clear breadcrumbs
    document.getElementById('method-breadcrumb').innerHTML = '';
    
    // Update URL without state
    history.pushState(null, '', window.location.pathname);
    
    // Show search results if they exist
    const searchResultsContainer = document.getElementById('method-search-results');
    if (searchResultsContainer && !searchResultsContainer.classList.contains('d-none')) {
        // Keep search results visible if they were already displayed
        return;
    } else {
        // Otherwise, make search results visible if they have content
        const searchResultsBody = document.getElementById('search-results-body');
        if (searchResultsBody && searchResultsBody.innerHTML.trim() !== '') {
            searchResultsContainer.classList.remove('d-none');
            return;
        }
    }
    
    // If no search results to show, load classes if needed
    loadClasses();
}

// Handle navigation from browser history
function handleHistoryNavigation(state) {
    if (state.view === 'trace') {
        traceMethod(state.methodId, state.methodName, true);
    } else if (state.view === 'methods') {
        loadMethods(state.classId, state.className, true);
        hideMethodSource();
    } else if (state.view === 'search') {
        // You could implement search history handling here
        hideMethodSource();
    }
}

// Show loading indicator
function showLoading() {
    document.getElementById('loading').classList.remove('d-none');
}

// Hide loading indicator
function hideLoading() {
    document.getElementById('loading').classList.add('d-none');
}

// Show alert message
function showAlert(message, type = 'info') {
    const alertContainer = document.getElementById('alert-container');
    const alertId = 'alert-' + Date.now();
    
    const alertHtml = `
        <div id="${alertId}" class="alert alert-${type} alert-dismissible fade show" role="alert">
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        </div>
    `;
    
    alertContainer.innerHTML += alertHtml;
    
    // Auto-dismiss after 5 seconds
    setTimeout(() => {
        const alertElement = document.getElementById(alertId);
        if (alertElement) {
            alertElement.remove();
        }
    }, 5000);
}

// Update breadcrumb navigation
function updateBreadcrumbs(items) {
    const breadcrumb = document.getElementById('method-breadcrumb');
    breadcrumb.innerHTML = '';
    
    // Add home item
    const homeItem = document.createElement('li');
    homeItem.className = 'breadcrumb-item';
    homeItem.innerHTML = '<a href="javascript:void(0)" onclick="navigateHome()">Home</a>';
    breadcrumb.appendChild(homeItem);
    
    // Add subsequent items
    items.forEach((item, index) => {
        const listItem = document.createElement('li');
        listItem.className = 'breadcrumb-item';
        
        if (index === items.length - 1) {
            // Last item is active
            listItem.classList.add('active');
            listItem.setAttribute('aria-current', 'page');
            listItem.textContent = item.text;
        } else {
            // Clickable item
            listItem.innerHTML = `<a href="javascript:void(0)" onclick="${item.onclick}">${item.text}</a>`;
        }
        
        breadcrumb.appendChild(listItem);
    });
}

// Load database path
function loadDatabasePath() {
    fetch('/db_path')
        .then(response => response.json())
        .then(data => {
            document.getElementById('db-path').textContent = `Database: ${data.db_path}`;
        })
        .catch(error => {
            console.error('Error fetching database path:', error);
        });
}

// Load all classes
function loadClasses() {
    showLoading();
    
    fetch('/classes')
        .then(response => response.json())
        .then(data => {
            hideLoading();
            
            if (data.success) {
                const classList = document.getElementById('class-list');
                classList.innerHTML = '';
                
                if (data.classes.length === 0) {
                    classList.innerHTML = '<div class="text-center text-muted">No classes found</div>';
                    return;
                }
                
                // Group by package
                const packageMap = {};
                data.classes.forEach(classInfo => {
                    const packageName = classInfo.package || '(Default Package)';
                    if (!packageMap[packageName]) {
                        packageMap[packageName] = [];
                    }
                    packageMap[packageName].push(classInfo);
                });
                
                // Create package accordions
                let html = '';
                Object.keys(packageMap).sort().forEach(packageName => {
                    const packageId = 'package-' + packageName.replace(/\./g, '-');
                    
                    html += `
                        <div class="accordion-item">
                            <h2 class="accordion-header" id="heading-${packageId}">
                                <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse" 
                                        data-bs-target="#collapse-${packageId}" aria-expanded="false" aria-controls="collapse-${packageId}">
                                    ${packageName}
                                </button>
                            </h2>
                            <div id="collapse-${packageId}" class="accordion-collapse collapse" aria-labelledby="heading-${packageId}">
                                <div class="accordion-body p-0">
                                    <div class="list-group list-group-flush">
                    `;
                    
                    // Add classes in this package
                    packageMap[packageName].sort((a, b) => a.name.localeCompare(b.name)).forEach(classInfo => {
                        const icon = classInfo.is_interface ? 'interface' : (classInfo.is_abstract ? 'abstract' : 'class');
                        html += `
                            <a href="#" class="list-group-item list-group-item-action" 
                               onclick="loadMethods(${classInfo.id}, '${classInfo.name}')">
                                <i class="fas fa-code me-2"></i>${classInfo.name}
                            </a>
                        `;
                    });
                    
                    html += `
                                    </div>
                                </div>
                            </div>
                        </div>
                    `;
                });
                
                classList.innerHTML = `<div class="accordion">${html}</div>`;
            } else {
                showAlert('Failed to load classes: ' + (data.error || 'Unknown error'), 'danger');
            }
        })
        .catch(error => {
            hideLoading();
            console.error('Error loading classes:', error);
            showAlert('Error loading classes: ' + error.message, 'danger');
        });
}

// Load methods for a class
function loadMethods(classId, className, skipHistory = false) {
    showLoading();
    
    // Hide other sections
    document.getElementById('method-search-results').classList.add('d-none');
    document.getElementById('call-trace').classList.add('d-none');
    document.getElementById('no-results').classList.add('d-none');
    hideMethodSource();
    
    // Update browser history for back navigation
    if (!skipHistory) {
        const state = { view: 'methods', classId, className };
        history.pushState(state, '', `?view=methods&classId=${classId}&className=${encodeURIComponent(className)}`);
    }
    
    // Update breadcrumbs
    updateBreadcrumbs([
        { text: `Class: ${className}`, onclick: `loadMethods(${classId}, '${className}')` }
    ]);
    
    fetch(`/classes/${classId}/methods`)
        .then(response => response.json())
        .then(data => {
            hideLoading();
            
            if (data.success) {
                const methodListContainer = document.getElementById('method-list-container');
                const methodListBody = document.getElementById('method-list-body');
                const selectedClassName = document.getElementById('selected-class-name');
                
                selectedClassName.textContent = `Methods in ${className}`;
                methodListBody.innerHTML = '';
                
                if (data.methods.length === 0) {
                    document.getElementById('no-results').classList.remove('d-none');
                    methodListContainer.classList.add('d-none');
                    return;
                }
                
                data.methods.forEach(method => {
                    // Format line numbers if available
                    const lineInfo = method.start_line ? 
                        `<span class="text-muted">(Line: ${method.start_line}${method.end_line ? '-' + method.end_line : ''})</span>` : '';
                    
                    const rowHtml = `
                        <tr>
                            <td>${method.name} ${lineInfo}</td>
                            <td><code>${method.signature}</code></td>
                            <td>${method.return_type || 'void'}</td>
                            <td>
                                <button class="btn btn-sm btn-primary method-action-btn btn-trace" 
                                        onclick="traceMethod(${method.id}, '${method.name}')">
                                    Trace
                                </button>
                            </td>
                        </tr>
                    `;
                    
                    methodListBody.innerHTML += rowHtml;
                });
                
                methodListContainer.classList.remove('d-none');
            } else {
                showAlert('Failed to load methods: ' + (data.error || 'Unknown error'), 'danger');
            }
        })
        .catch(error => {
            hideLoading();
            console.error('Error loading methods:', error);
            showAlert('Error loading methods: ' + error.message, 'danger');
        });
}

// Search for methods
function searchMethods() {
    const methodName = document.getElementById('method-name').value.trim();
    const methodSignature = document.getElementById('method-signature').value.trim();
    
    if (!methodName && !methodSignature) {
        showAlert('Please enter a method name or signature to search for.', 'warning');
        return;
    }
    
    showLoading();
    
    // Hide other sections
    document.getElementById('method-list-container').classList.add('d-none');
    document.getElementById('call-trace').classList.add('d-none');
    document.getElementById('no-results').classList.add('d-none');
    hideMethodSource();
    
    // Update breadcrumbs
    updateBreadcrumbs([
        { text: `Search: ${methodName || methodSignature}`, onclick: "document.getElementById('search-form').dispatchEvent(new Event('submit'))" }
    ]);
    
    const params = new URLSearchParams();
    if (methodName) params.append('name', methodName);
    if (methodSignature) params.append('signature', methodSignature);
    
    fetch(`/methods/search?${params.toString()}`)
        .then(response => response.json())
        .then(data => {
            hideLoading();
            
            if (data.success) {
                const searchResultsContainer = document.getElementById('method-search-results');
                const searchResultsBody = document.getElementById('search-results-body');
                
                searchResultsBody.innerHTML = '';
                
                if (data.methods.length === 0) {
                    document.getElementById('no-results').classList.remove('d-none');
                    searchResultsContainer.classList.add('d-none');
                    return;
                }
                
                data.methods.forEach(method => {
                    // Format line numbers if available
                    const lineInfo = method.start_line ? 
                        `<span class="text-muted ms-2">(Line: ${method.start_line}${method.end_line ? '-' + method.end_line : ''})</span>` : '';
                    
                    const rowHtml = `
                        <tr>
                            <td>${method.name} ${lineInfo}</td>
                            <td>${method.class_name}</td>
                            <td>${method.package || '-'}</td>
                            <td><code>${method.signature}</code></td>
                            <td>
                                <button class="btn btn-sm btn-primary method-action-btn btn-trace" 
                                        onclick="traceMethod(${method.id}, '${method.name}')">
                                    Trace
                                </button>
                            </td>
                        </tr>
                    `;
                    
                    searchResultsBody.innerHTML += rowHtml;
                });
                
                searchResultsContainer.classList.remove('d-none');
            } else {
                showAlert('Failed to search methods: ' + (data.error || 'Unknown error'), 'danger');
            }
        })
        .catch(error => {
            hideLoading();
            console.error('Error searching methods:', error);
            showAlert('Error searching methods: ' + error.message, 'danger');
        });
}

// Store method trace history for breadcrumb navigation
const methodTraceHistory = [];

// Store cache of method existence checks to avoid repeated API calls
const methodExistsCache = {};

// Check if a method exists in the database
function checkMethodExists(methodName) {
    return new Promise((resolve) => {
        // Check cache first
        if (methodExistsCache.hasOwnProperty(methodName)) {
            resolve(methodExistsCache[methodName]);
            return;
        }
        
        // Otherwise, fetch from server
        const params = new URLSearchParams();
        params.append('name', methodName);
        
        fetch(`/methods/exists?${params.toString()}`)
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    // Cache the result
                    methodExistsCache[methodName] = data.exists;
                    resolve(data.exists);
                } else {
                    resolve(false);
                }
            })
            .catch(error => {
                console.error('Error checking method existence:', error);
                resolve(false);
            });
    });
}

// Find class ID by name and package
function findClassByNameAndPackage(className, packageName, callback) {
    showLoading();
    
    // Get all classes and find the matching one
    fetch('/classes')
        .then(response => response.json())
        .then(data => {
            hideLoading();
            
            if (data.success) {
                const classes = data.classes;
                let matchedClass = null;
                
                // Look for exact match with class name and package
                if (packageName) {
                    matchedClass = classes.find(c => 
                        c.name === className && 
                        c.package === packageName
                    );
                }
                
                // If no match with package, try just by name
                if (!matchedClass) {
                    matchedClass = classes.find(c => c.name === className);
                }
                
                if (matchedClass) {
                    callback(matchedClass);
                } else {
                    showAlert(`Class "${className}" not found in the database`, 'warning');
                }
            } else {
                showAlert('Failed to load classes: ' + (data.error || 'Unknown error'), 'danger');
            }
        })
        .catch(error => {
            hideLoading();
            console.error('Error loading classes:', error);
            showAlert('Error loading classes: ' + error.message, 'danger');
        });
}

// Build trace tree HTML from trace data with asynchronous method existence checking
async function buildTraceTree(node) {
    if (!node) return '';
    
    // Get the "hide untraceable methods" preference
    const hideUntraceableMethods = document.getElementById('hide-untraceable-methods').checked;
    
    // Format location info for the method
    let locationInfo = '';
    if (node.file_path && node.start_line) {
        const fileName = node.file_path.split('/').pop();
        locationInfo = `<span class="text-muted file-location">
            <i class="fas fa-file-code"></i> ${fileName}:${node.start_line}
            ${node.end_line ? ('-' + node.end_line) : ''}
        </span>`;
    }
    
    // Make the class name clickable if it exists
    const classNameHtml = node.class_name ? 
        `(<a href="javascript:void(0)" class="class-link" onclick="onClassClick('${node.class_name}', '${node.package || ''}')">
            ${node.package ? node.package + '.' : ''}${node.class_name}
        </a>)` :
        '';
    
    let html = `
        <div class="trace-node">
            <div class="trace-node-header">
                <div>
                    <span class="trace-node-name">${node.method_name}</span>
                    <span class="trace-node-class">${classNameHtml}</span>
                    ${locationInfo}
                </div>
    `;
    
    if (node.calls && node.calls.length > 0) {
        html += `<button class="trace-node-toggle"><i class="fas fa-minus"></i></button>`;
    }
    
    html += `</div>`;
    
    if (node.calls && node.calls.length > 0) {
        html += `<div class="trace-node-children">`;
        
        // Process all calls in parallel
        const callPromises = node.calls.map(async (call) => {
            // Skip Java standard library methods
            const isJavaStdLib = call.called_package && 
                (call.called_package.startsWith('java.') || 
                 call.called_package.startsWith('javax.'));
            
            // Format line number for call
            const lineInfo = call.line_number ? `<span class="text-muted call-line-num">:${call.line_number}</span>` : '';
            
            // If it's not resolved but not part of standard library, check if it exists in the database
            let methodExists = false;
            if (!call.resolved_method_id && !isJavaStdLib) {
                methodExists = await checkMethodExists(call.called_method);
            }
            
            // If hiding untraceable methods and this method can't be traced, skip it
            if (hideUntraceableMethods && !call.resolved_method_id && !methodExists && !isJavaStdLib) {
                return '';
            }
            
            // Make the class name clickable
            const calledClassHtml = call.called_class_name ? 
                `(<a href="javascript:void(0)" class="class-link" onclick="onClassClick('${call.called_class_name}', '${call.called_package || ''}')">
                    ${call.called_package ? call.called_package + '.' : ''}${call.called_class_name}
                </a>)` : 
                call.called_class ? 
                `(<span class="class-name-unresolved">${call.called_class}</span>)` : 
                '';
            
            let callHtml = `
                <div class="trace-node">
                    <div class="trace-node-header">
                        <div>
                            <span class="trace-node-name">
                                <a href="javascript:void(0)" onclick="traceMethodByNameAndClass('${call.called_method}', '${call.called_class_name || call.called_class || ''}', '${call.called_package || ''}')">
                                    ${call.called_method}
                                </a>${lineInfo}
                            </span>
                            <span class="trace-node-class">${calledClassHtml}</span>
                            ${!isJavaStdLib ? 
                                (call.resolved_method_id ? 
                                    `<button class="btn btn-xs btn-primary btn-trace method-trace-btn ms-2" onclick="event.stopPropagation(); traceMethod(${call.resolved_method_id}, '${call.called_method}')">
                                        <i class="fas fa-project-diagram"></i> Trace
                                    </button>` : 
                                    (methodExists ? 
                                        `<button class="btn btn-xs btn-outline-primary btn-trace method-trace-btn ms-2" onclick="event.stopPropagation(); findAndTraceMethod('${call.called_method}', '${call.called_class_name || call.called_class || ''}', '${call.called_package || ''}')">
                                            <i class="fas fa-search"></i> Find & Trace
                                        </button>` : 
                                        '')) : 
                                ''}
                        </div>
            `;
            
            if (call.children && Object.keys(call.children).length > 0) {
                callHtml += `<button class="trace-node-toggle"><i class="fas fa-minus"></i></button>`;
            }
            
            callHtml += `</div>`;
            
            if (call.children && Object.keys(call.children).length > 0) {
                callHtml += `<div class="trace-node-children">`;
                callHtml += await buildTraceTree(call.children);
                callHtml += `</div>`;
            }
            
            callHtml += `</div>`;
            
            return callHtml;
        });
        
        // Wait for all calls to be processed
        const callsHtml = await Promise.all(callPromises);
        html += callsHtml.join('');
        
        html += `</div>`;
    }
    
    html += `</div>`;
    
    return html;
}

// Trace a method call stack
function traceMethod(methodId, methodName, skipHistory = false) {
    showLoading();
    
    // Hide other sections
    document.getElementById('method-list-container').classList.add('d-none');
    document.getElementById('method-search-results').classList.add('d-none');
    document.getElementById('no-results').classList.add('d-none');
    hideMethodSource();
    
    // Update browser history for back navigation
    if (!skipHistory) {
        const state = { view: 'trace', methodId, methodName };
        history.pushState(state, '', `?view=trace&methodId=${methodId}&methodName=${encodeURIComponent(methodName)}`);
    }
    
    // Check if tracing a new root method (i.e., not coming from a click on a child method)
    // If it's not from the breadcrumb navigation (traceMethodFromHistory), it's a new trace
    if (!skipHistory || methodTraceHistory.length === 0) {
        methodTraceHistory.length = 0; // Reset history if this is a new root method
        methodTraceHistory.push({ id: methodId, name: methodName, children: [] });
    }
    
    // Update breadcrumbs
    updateBreadcrumbs(methodTraceHistory.map((item, index) => ({
        text: item.name,
        onclick: `traceMethodFromHistory(${index})`
    })));
    
    // Fetch call trace
    fetch(`/methods/${methodId}/trace`)
        .then(response => response.json())
        .then(async data => {
            if (data.success) {
                const callTraceContainer = document.getElementById('call-trace');
                const traceMethodName = document.getElementById('trace-method-name');
                const traceTree = document.getElementById('trace-tree');
                
                traceMethodName.textContent = `${methodName} - Call Stack Trace`;
                traceTree.innerHTML = '';
                
                if (!data.trace || Object.keys(data.trace).length === 0) {
                    traceTree.innerHTML = '<div class="alert alert-info">No method calls found.</div>';
                } else {
                    // Build the trace tree recursively (now async)
                    traceTree.innerHTML = await buildTraceTree(data.trace);
                }
                
                callTraceContainer.classList.remove('d-none');
                
                // Add toggle functionality
                const toggleButtons = document.querySelectorAll('.trace-node-toggle');
                toggleButtons.forEach(button => {
                    button.addEventListener('click', function() {
                        const childrenContainer = this.closest('.trace-node').querySelector('.trace-node-children');
                        if (childrenContainer) {
                            childrenContainer.classList.toggle('d-none');
                            
                            // Update button icon
                            if (childrenContainer.classList.contains('d-none')) {
                                this.innerHTML = '<i class="fas fa-plus"></i>';
                            } else {
                                this.innerHTML = '<i class="fas fa-minus"></i>';
                            }
                        }
                    });
                });
                
                // Also fetch and display the method source code
                fetchMethodSource(methodId, methodName);
            } else {
                showAlert('Failed to trace method: ' + (data.error || 'Unknown error'), 'danger');
            }
            
            hideLoading();
        })
        .catch(error => {
            hideLoading();
            console.error('Error tracing method:', error);
            showAlert('Error tracing method: ' + error.message, 'danger');
        });
}

// Fetch and display method source code with execution paths
function fetchMethodSource(methodId, methodName) {
    showLoading();
    
    fetch(`/methods/${methodId}/source`)
        .then(response => response.json())
        .then(data => {
            hideLoading();
            
            if (data.success) {
                const methodSourceContainer = document.getElementById('method-source');
                const sourceMethodName = document.getElementById('source-method-name');
                const sourceFileLocation = document.getElementById('source-file-location');
                const sourceCode = document.getElementById('method-source-code');
                
                // Set method name and file location
                const method = data.method;
                sourceMethodName.textContent = `${method.name}${method.signature.substring(method.name.length)}`;
                
                // Extract filename from the path
                const filePath = method.class_name;
                const packagePath = method.package ? method.package + '.' : '';
                sourceFileLocation.textContent = `${packagePath}${method.class_name} (Lines ${method.start_line}-${method.end_line})`;
                
                // Format and display the source code with line numbers
                const sourceLines = method.source_code.split('\n');
                
                // Generate HTML without highlighting first
                let formattedCode = '';
                sourceLines.forEach((line, index) => {
                    // Use actual file line number instead of index
                    const lineNum = method.start_line + index;
                    formattedCode += `<span class="line" data-line="${lineNum}">${escapeHtml(line)}</span>`;
                });
                sourceCode.innerHTML = formattedCode;
                
                // Apply syntax highlighting to each line
                applyJavaSyntaxHighlighting(sourceCode);
                
                // Store execution paths for later use
                sourceCode.dataset.executionPaths = JSON.stringify(method.execution_paths);
                
                // Show the source code section
                methodSourceContainer.classList.remove('d-none');
                
                // Set up the toggle button for execution paths
                setupExecutionPathsToggle(method.execution_paths);
            } else {
                showAlert('Failed to load method source: ' + (data.error || 'Unknown error'), 'warning');
            }
        })
        .catch(error => {
            hideLoading();
            console.error('Error loading method source:', error);
            showAlert('Error loading method source: ' + error.message, 'danger');
        });
}

// Apply Java syntax highlighting to code with line numbers
function applyJavaSyntaxHighlighting(codeElement) {
    // Get all line elements
    const lineElements = codeElement.querySelectorAll('.line');
    
    // Highlight each line individually
    lineElements.forEach(line => {
        // Create a temporary element for highlighting
        const tempElement = document.createElement('code');
        tempElement.className = 'language-java';
        tempElement.textContent = line.textContent;
        
        // Use highlight.js to highlight the code
        hljs.highlightElement(tempElement);
        
        // Replace the content of the line with the highlighted code
        line.innerHTML = tempElement.innerHTML;
    });
}

// Setup the toggle button and functionality for execution paths
function setupExecutionPathsToggle(executionPaths) {
    const toggleButton = document.getElementById('toggle-execution-paths');
    const pathsOverlay = document.getElementById('execution-paths-overlay');
    const pathsList = document.getElementById('execution-paths-list');
    const pathsLegend = document.getElementById('execution-paths-legend');
    
    // Clear any existing paths
    pathsOverlay.innerHTML = '';
    pathsList.innerHTML = '';
    
    // If no execution paths, disable the button
    if (!executionPaths || executionPaths.length === 0) {
        toggleButton.disabled = true;
        toggleButton.innerHTML = '<i class="fas fa-code-branch me-1"></i> No Execution Paths';
        return;
    }
    
    // Enable the button
    toggleButton.disabled = false;
    toggleButton.innerHTML = '<i class="fas fa-code-branch me-1"></i> Show Execution Paths';
    
    // Add each execution path to the paths list
    executionPaths.forEach((path, index) => {
        // Add to the legend/list
        const listItem = document.createElement('div');
        listItem.className = `list-group-item ${path.type}`;
        listItem.setAttribute('data-line', path.line);
        listItem.textContent = `Line ${path.line}: ${path.description}`;
        
        // When clicking on a path in the list, highlight that line
        listItem.addEventListener('click', function() {
            const lineNum = parseInt(this.getAttribute('data-line'));
            highlightLine(lineNum);
        });
        
        pathsList.appendChild(listItem);
        
        // Add the marker to the overlay
        const marker = document.createElement('div');
        marker.className = `execution-path-marker ${path.type}`;
        marker.setAttribute('data-line', path.line);
        marker.setAttribute('title', path.description);
        pathsOverlay.appendChild(marker);
    });
    
    // Toggle button to show/hide execution paths
    toggleButton.addEventListener('click', function() {
        const isShowing = !pathsOverlay.classList.contains('d-none');
        
        if (isShowing) {
            // Hide the paths
            pathsOverlay.classList.add('d-none');
            pathsLegend.classList.add('d-none');
            toggleButton.innerHTML = '<i class="fas fa-code-branch me-1"></i> Show Execution Paths';
        } else {
            // Show the paths
            pathsOverlay.classList.remove('d-none');
            pathsLegend.classList.remove('d-none');
            toggleButton.innerHTML = '<i class="fas fa-code-branch me-1"></i> Hide Execution Paths';
            
            // Position the markers on the correct lines
            positionExecutionPathMarkers();
        }
    });
    
    // Position markers when the source code container is scrolled
    const sourceContainer = document.getElementById('method-source-container');
    sourceContainer.addEventListener('scroll', function() {
        if (!pathsOverlay.classList.contains('d-none')) {
            positionExecutionPathMarkers();
        }
    });
}

// Position execution path markers over their corresponding lines
function positionExecutionPathMarkers() {
    const sourceContainer = document.getElementById('method-source-container');
    const markers = document.querySelectorAll('.execution-path-marker');
    
    markers.forEach(marker => {
        const lineNum = parseInt(marker.getAttribute('data-line'));
        // Look for the line element with the matching actual line number
        const lineElement = document.querySelector(`.line[data-line="${lineNum}"]`);
        
        if (lineElement) {
            const rect = lineElement.getBoundingClientRect();
            const containerRect = sourceContainer.getBoundingClientRect();
            
            marker.style.top = `${lineElement.offsetTop}px`;
            marker.style.height = `${lineElement.offsetHeight}px`;
        }
    });
}

// Highlight a specific line in the source code
function highlightLine(lineNum) {
    // Remove any existing highlights
    const existingHighlights = document.querySelectorAll('.line-highlight');
    existingHighlights.forEach(highlight => highlight.remove());
    
    // Find the line element using the actual line number
    const lineElement = document.querySelector(`.line[data-line="${lineNum}"]`);
    
    if (lineElement) {
        // Create a highlight div
        const highlight = document.createElement('div');
        highlight.className = 'line-highlight';
        highlight.style.top = `${lineElement.offsetTop}px`;
        highlight.style.height = `${lineElement.offsetHeight}px`;
        
        // Add it to the container
        const sourceContainer = document.getElementById('method-source-container');
        sourceContainer.appendChild(highlight);
        
        // Scroll to the line
        lineElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
}

// Escape HTML to prevent XSS
function escapeHtml(html) {
    const escapeMap = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return html.replace(/[&<>"']/g, function(m) { return escapeMap[m]; });
}

// Trace method from history (for breadcrumb navigation)
function traceMethodFromHistory(index) {
    if (index >= 0 && index < methodTraceHistory.length) {
        // Trim the history array to this point
        methodTraceHistory.splice(index + 1);
        
        // Trace the method
        const method = methodTraceHistory[index];
        traceMethod(method.id, method.name, true);
    }
}

// Find and trace method by name and class
function traceMethodByNameAndClass(methodName, className, packageName) {
    showLoading();
    
    // First, search for the method
    const params = new URLSearchParams();
    params.append('name', methodName);
    params.append('exact_match', 'true');  // Use exact matching for better precision
    
    fetch(`/methods/search?${params.toString()}`)
        .then(response => response.json())
        .then(data => {
            if (data.success && data.methods.length > 0) {
                // Try to find an exact match first
                let method = null;
                
                if (className && packageName) {
                    // Look for exact match with class and package
                    method = data.methods.find(m => 
                        m.name === methodName && 
                        m.class_name === className && 
                        m.package === packageName
                    );
                } else if (className) {
                    // Look for match with just class name
                    method = data.methods.find(m => 
                        m.name === methodName && 
                        m.class_name === className
                    );
                }
                
                // If no exact match, take the first one
                if (!method) {
                    method = data.methods[0];
                }
                
                // Add this method as a child of the last method in trace history
                if (methodTraceHistory.length > 0) {
                    const parentMethod = methodTraceHistory[methodTraceHistory.length - 1];
                    // Only add to history if not already a child of the current method
                    const existingChild = parentMethod.children.find(c => c.id === method.id);
                    if (!existingChild) {
                        parentMethod.children.push({
                            id: method.id,
                            name: method.name
                        });
                    }
                    // Add to the breadcrumb chain
                    methodTraceHistory.push({
                        id: method.id,
                        name: method.name,
                        children: [],
                        parent: parentMethod.id
                    });
                }
                
                // Trace the found method
                traceMethod(method.id, method.name, true);
            } else {
                hideLoading();
                showAlert(`Method "${methodName}" not found in database`, 'warning');
            }
        })
        .catch(error => {
            hideLoading();
            console.error('Error searching method:', error);
            showAlert('Error searching method: ' + error.message, 'danger');
        });
}

// Find and trace method that doesn't have a resolved_method_id
function findAndTraceMethod(methodName, className, packageName) {
    showLoading();
    
    // First, search for the method
    const params = new URLSearchParams();
    params.append('name', methodName);
    params.append('exact_match', 'true');  // Use exact matching for better precision
    
    fetch(`/methods/search?${params.toString()}`)
        .then(response => response.json())
        .then(data => {
            if (data.success && data.methods.length > 0) {
                // Try to find an exact match first
                let method = null;
                
                if (className && packageName) {
                    // Look for exact match with class and package
                    method = data.methods.find(m => 
                        m.name === methodName && 
                        m.class_name === className && 
                        m.package === packageName
                    );
                } else if (className) {
                    // Look for match with just class name
                    method = data.methods.find(m => 
                        m.name === methodName && 
                        m.class_name === className
                    );
                }
                
                // If no exact match but we have candidates, show a selection dialog
                if (!method && data.methods.length > 1) {
                    hideLoading();
                    showMethodSelectionDialog(methodName, data.methods);
                    return;
                }
                
                // If no exact match, take the first one
                if (!method) {
                    method = data.methods[0];
                }
                
                // Trace the found method
                traceMethod(method.id, method.name);
            } else {
                hideLoading();
                showAlert(`Method "${methodName}" not found in database`, 'warning');
            }
        })
        .catch(error => {
            hideLoading();
            console.error('Error searching method:', error);
            showAlert('Error searching method: ' + error.message, 'danger');
        });
}

// Show a dialog to select which method implementation to trace
function showMethodSelectionDialog(methodName, methods) {
    // Create modal dialog for method selection
    const modalId = 'methodSelectionModal';
    let modalHtml = `
        <div class="modal fade" id="${modalId}" tabindex="-1" aria-labelledby="${modalId}Label" aria-hidden="true">
            <div class="modal-dialog modal-lg">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title" id="${modalId}Label">Select Method Implementation: ${methodName}</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                    </div>
                    <div class="modal-body">
                        <p>Multiple implementations found. Select one to trace:</p>
                        <div class="list-group">
    `;
    
    methods.forEach(method => {
        modalHtml += `
            <button type="button" class="list-group-item list-group-item-action" 
                    onclick="traceMethodAndCloseModal(${method.id}, '${method.name}', '${modalId}')">
                <strong>${method.name}</strong> - ${method.class_name}${method.package ? ` (${method.package})` : ''}
                <br><small class="text-muted">${method.signature}</small>
            </button>
        `;
    });
    
    modalHtml += `
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    // Add modal to body
    const modalContainer = document.createElement('div');
    modalContainer.innerHTML = modalHtml;
    document.body.appendChild(modalContainer);
    
    // Initialize and show modal
    const modal = new bootstrap.Modal(document.getElementById(modalId));
    modal.show();
    
    // Remove modal from DOM when hidden
    document.getElementById(modalId).addEventListener('hidden.bs.modal', function() {
        document.body.removeChild(modalContainer);
    });
}

// Trace method and close selection modal
function traceMethodAndCloseModal(methodId, methodName, modalId) {
    // Close the modal
    const modalElement = document.getElementById(modalId);
    const modal = bootstrap.Modal.getInstance(modalElement);
    modal.hide();
    
    // Trace the selected method
    traceMethod(methodId, methodName);
}

// Analyze project
function analyzeProject() {
    const sourceDir = document.getElementById('source-dir').value.trim();
    const resetDb = document.getElementById('reset-db').checked;
    
    if (!sourceDir) {
        showAlert('Please enter a source directory path.', 'warning');
        return;
    }
    
    showLoading();
    
    const formData = new FormData();
    formData.append('source_dir', sourceDir);
    formData.append('reset_db', resetDb);
    
    fetch('/analyze', {
        method: 'POST',
        body: formData
    })
    .then(response => response.json())
    .then(data => {
        hideLoading();
        
        if (data.success) {
            showAlert('Project analyzed successfully!', 'success');
            loadClasses();
            navigateHome();
        } else {
            showAlert('Failed to analyze project: ' + (data.error || 'Unknown error'), 'danger');
        }
    })
    .catch(error => {
        hideLoading();
        console.error('Error analyzing project:', error);
        showAlert('Error analyzing project: ' + error.message, 'danger');
    });
}

// Handle class click in the call stack trace
function onClassClick(className, packageName) {
    findClassByNameAndPackage(className, packageName, (classInfo) => {
        hideMethodSource();
        loadMethods(classInfo.id, className);
    });
}

// Copy source code to clipboard
function copySourceCode() {
    const methodSourceCode = document.getElementById('method-source-code');
    
    // Extract the raw text content without HTML tags
    const rawText = Array.from(methodSourceCode.querySelectorAll('.line'))
        .map(line => line.textContent)
        .join('\n');
    
    // Use the clipboard API to copy the text
    navigator.clipboard.writeText(rawText)
        .then(() => {
            // Show success message
            showAlert('Source code copied to clipboard!', 'success');
            
            // Flash the button to indicate success
            const copyButton = document.getElementById('copy-source-code');
            copyButton.classList.remove('btn-outline-secondary');
            copyButton.classList.add('btn-success');
            setTimeout(() => {
                copyButton.classList.remove('btn-success');
                copyButton.classList.add('btn-outline-secondary');
            }, 1000);
        })
        .catch(err => {
            console.error('Failed to copy: ', err);
            showAlert('Failed to copy source code', 'danger');
        });
}

// Load available databases
function loadAvailableDatabases() {
    fetch('/available_databases')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                const databaseSelect = document.getElementById('database-select');
                databaseSelect.innerHTML = '';
                
                if (data.databases.length === 0) {
                    databaseSelect.innerHTML = '<option value="">No databases available</option>';
                    return;
                }
                
                data.databases.forEach(db => {
                    const option = document.createElement('option');
                    option.value = db;
                    option.textContent = db;
                    if (data.active_database === db) {
                        option.selected = true;
                    }
                    databaseSelect.appendChild(option);
                });
            } else {
                showAlert('Failed to load databases: ' + (data.error || 'Unknown error'), 'danger');
            }
        })
        .catch(error => {
            console.error('Error loading databases:', error);
            showAlert('Error loading databases: ' + error.message, 'danger');
        });
}

// Switch to a different database
function switchDatabase(dbName) {
    showLoading();
    
    const formData = new FormData();
    formData.append('db_name', dbName);
    
    fetch('/switch_database', {
        method: 'POST',
        body: formData
    })
    .then(response => response.json())
    .then(data => {
        hideLoading();
        
        if (data.success) {
            if (data.is_new) {
                showAlert(`Created and switched to new database: ${dbName}`, 'success');
            } else {
                showAlert(`Switched to database: ${dbName}`, 'success');
            }
            
            document.getElementById('db-path').textContent = `Database: ${data.db_path}`;
            
            // Reload the class list
            loadClasses();
            
            // Reset views
            navigateHome();
            
            // Refresh database list
            loadAvailableDatabases();
        } else {
            showAlert('Failed to switch database: ' + (data.error || 'Unknown error'), 'danger');
        }
    })
    .catch(error => {
        hideLoading();
        console.error('Error switching database:', error);
        showAlert('Error switching database: ' + error.message, 'danger');
    });
}

// Create a new database
function createDatabase(dbName) {
    // Simply switching to a non-existent database will create it
    switchDatabase(dbName);
    
    // After successful creation, reload the database list
    setTimeout(() => {
        loadAvailableDatabases();
    }, 500);
} 