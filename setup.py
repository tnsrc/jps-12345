from setuptools import setup, find_packages

setup(
    name="java_analyzer",
    version="0.1.0",
    packages=find_packages(),
    include_package_data=True,
    install_requires=[
        "javalang",
        "flask",
        "flaskwebgui",
    ],
    entry_points={
        "console_scripts": [
            "java-analyzer=java_analyzer.analyzer.run_analyzer:main",
            "java-analyzer-web=java_analyzer.web.app:run_app",
        ],
    },
    author="Your Name",
    author_email="your.email@example.com",
    description="A tool for analyzing Java source code and tracing method call stacks",
    keywords="java, code analysis, call stack",
    python_requires=">=3.6",
) 