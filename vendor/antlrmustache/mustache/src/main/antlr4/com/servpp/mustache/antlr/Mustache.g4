grammar Mustache;

// Define the starting point for parsing
template: (statement | variable | text | calculateValue)*;

// Define statements enclosed in {{ }} or {{#...}}
statement: (section | invertedSection | partial | comment) ;

// Define a section, which is used for loops or conditionals
section: sectionBeg sectionContent* sectionEnd;

// Define an inverted section, which is used for "if not" conditionals
invertedSection: invertedSectionBeg sectionContent* sectionEnd;

// Define a partial, which is a reference to another template
partial: '{{' '>' qualifiedName parameters?  partialReturns? '}}';

partialReturns : '[' (parameter (',' parameter)*)? ']';
parameters: '(' (parameter (',' parameter)*)? ')';

parameter: IDENTIFIER;

// Define variables that are replaced with data
variable: '{{' qualifiedName '}}';
sectionVar: ('$' ? qualifiedName | first | last) ;
//# means normal section, @ means take map as list
sectionBeg: '{{' ('#' | '@') sectionVar '}}';
sectionContent : statement | variable | text | sectionIndex | sectionRecursive | calculateValue;
// '%' means section is recursive, '/' means section is end
sectionEnd : '{{' ('/'|'%') sectionVar '}}';
invertedSectionBeg: '{{' '^' sectionVar '}}';
sectionIndex : '{{' '-index' '}}';
sectionRecursive : '{{' '*' qualifiedName '}}';
calculateValue : '{{' '%' multiexpr '}}';
multiexpr : (~('{{' | '}}') | ANY)+;
// Define 'first' and 'last' as special variables
first: '-first';
last: '-last';

// Define comments that are ignored
comment: '{{!' .*? '}}';

// Define plain text that is not a Mustache tag
text: (~('{{' | '}}') | ANY)+;
qualifiedName
    : (WS* IDENTIFIER WS*) ('.' (WS* IDENTIFIER WS*))*
    ;
// Define an identifier for variable names
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_]*;
// Define whitespace and newlines
WS: [ \t\r\n]+;

// Define any other characters to be handled as text
ANY: .;

// Define error handling
error: .+;
