import json
import re
import sys

def tokenize(source_code):
    """
    Performs lexical analysis on the source code.
    Returns a list of tokens.
    """
    token_patterns = [
        (r'\d+', 'NUMBER'),
        (r'\+', 'PLUS'),
        (r'\-', 'MINUS'),
        (r'\*', 'MULTIPLY'),
        (r'\/', 'DIVIDE'),
        (r'\(', 'LPAREN'),
        (r'\)', 'RPAREN'),
        (r'[ \t\n]+', None),  # Skip whitespace
    ]
    
    tokens = []
    pos = 0
    while pos < len(source_code):
        match = None
        for pattern, token_type in token_patterns:
            regex = re.compile(pattern)
            m = regex.match(source_code, pos)
            if m:
                value = m.group(0)
                if token_type:
                    tokens.append({'type': token_type, 'value': value})
                pos = m.end()
                match = True
                break
        if not match:
            raise Exception("Illegal character: " + source_code[pos])
    return tokens

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 lexer.py <source_file>")
        sys.exit(1)
    
    try:
        with open(sys.argv[1], 'r') as f:
            source = f.read()
    except FileNotFoundError:
        print(f"Error: Source file '{sys.argv[1]}' not found.")
        sys.exit(1)

    tokens = tokenize(source)
    with open("tokens.json", "w") as f:
        json.dump(tokens, f, indent=2)

    print("Tokens written to tokens.json")