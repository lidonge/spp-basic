# antlrmustache

[中文文档](./README.zh-CN.md)

`antlrmustache` is a Java-based Mustache parser and renderer built on top of ANTLR4.

The project implements standard Mustache-like template capabilities and adds several practical extensions for code generation and complex template composition, including:

- recursive sections
- embedded expressions
- Map-as-list iteration
- loop helpers such as `-index`, `-first`, and `-last`
- partials with parameters and return variables

The repository contains two modules:

- `multiexpr`: expression parsing and evaluation
- `mustache`: extended Mustache grammar, template model, and rendering

## Why This Project

This project is useful when plain text substitution is not enough and your templates need light logic or structure-aware rendering, for example:

- generating SQL, DDL, source code, or configuration files
- rendering tree-shaped data recursively
- composing large templates from multiple partials
- computing small formatting values directly inside templates

## Requirements

- Java 17
- Maven 3.6.3+

Build the project with:

```bash
mvn clean test
```

## Quick Start

The basic flow is:

1. Parse a `.mustache` template with `MustacheCompiler`
2. Build the template model with `MustacheListenerImpl`
3. Render it with `MustacheWriter`

```java
import com.servpp.mustache.MustacheCompiler;
import com.servpp.mustache.handler.MustacheListenerImpl;
import com.servpp.mustache.handler.MustacheWriter;
import com.servpp.mustache.model.BaseSection;

import java.io.File;

File mustacheFile = new File("demo.mustache");

MustacheCompiler compiler = new MustacheCompiler(mustacheFile);
compiler.compileAntlr4(null);

MustacheListenerImpl listener = new MustacheListenerImpl(mustacheFile.toURI());
compiler.workListener(listener);

Object root = ...;

MustacheWriter writer = new MustacheWriter(mustacheFile.toURI(), root);
writer.write(listener.getTemplate(), BaseSection.SectionType.Normal);

System.out.println(writer.getOutText());
```

If your template uses partials, provide an `IPartialFileHandler`:

```java
writer.setPartialFileHandler(partialName -> {
    File partialFile = new File("templates/" + partialName + ".mustache");
    MustacheCompiler partialCompiler = new MustacheCompiler(partialFile);
    partialCompiler.compileAntlr4(null);

    MustacheListenerImpl partialListener = new MustacheListenerImpl(partialFile.toURI());
    partialCompiler.workListener(partialListener);
    return partialListener.getTemplate();
});
```

## Extended Syntax

The project supports standard variable, section, inverted section, comment, and partial constructs, plus the following extensions:

| Syntax | Description |
| --- | --- |
| `{{@name}}...{{/name}}` | Iterate over `Map.values()` when `name` resolves to a `Map` |
| `{{-index}}` | Current iteration index, starting at `0` |
| `{{#-first}}...{{/-first}}` | Render only for the first item |
| `{{^-first}}...{{/-first}}` | Render when the current item is not the first |
| `{{#-last}}...{{/-last}}` | Render only for the last item |
| `{{^-last}}...{{/-last}}` | Render when the current item is not the last |
| `{{*children}}` | Continue recursive rendering for the same section |
| `{{#children}}...{{%children}}` | Mark a section as recursive |
| `{{% ... }}` | Evaluate an expression and write its result |
| `{{#$var}}...{{/var}}` | Read section data from an expression variable |
| `{{>partial(a,b)[c]}}` | Call a partial with parameters and return variables |

## Standard Features

### Variables

```mustache
{{id}}
{{user.name}}
{{this}}
```

- simple names and dotted names are supported
- `{{this}}` means the current context object

### Normal Sections

```mustache
{{#children}}
  {{id}}
{{/children}}
```

- if the value is an object, the section renders once with that object as the current context
- if the value is a `List` or array, the section renders once per element

### Inverted Sections

```mustache
{{^children}}
  no children
{{/children}}
```

In the current implementation, inverted sections are triggered when the resolved value is `null`.

### Comments

```mustache
{{! this is a comment }}
```

## Extensions in Detail

### Map-as-List Iteration

Use `@` when the section value is a `Map` and you want to iterate over its values:

```mustache
{{@items}}
  {{name}}
{{/items}}
```

At runtime this behaves like iterating over `items.values().toArray()`.

### Index, First, Last

Inside list sections, the renderer exposes helpers for common formatting logic:

```mustache
{{#children}}
  {{id}}({{-index}}){{^-last}},{{/-last}}
{{/children}}
```

This is especially useful for separators, commas, and edge-item formatting.

### Recursive Sections

Recursive rendering is one of the most important extensions in this project.

Example template:

```mustache
{{id}}{{#children}}
  {{id}}({{-index}}){{^-last}},{{/-last}} {{*children}}{{%children}}
```

Meaning:

1. `{{#children}} ... {{%children}}` defines a recursive section
2. `{{*children}}` re-enters recursive rendering for the same child structure

Runnable examples:

- [`mustache/src/main/resources/TestTree.mustache`](./mustache/src/main/resources/TestTree.mustache)
- [`mustache/src/test/java/TestRecursion.java`](./mustache/src/test/java/TestRecursion.java)

Sample data:

```java
Component root = new Component(1);
root.add(new Component(2).add(new Component(3).add(new Component(6))).add(new Component(5)));
root.add(new Component(4));
```

Recursive indentation is enabled by default and can be disabled:

```java
writer.setIndentIfRecursive(false);
```

### Recursive Rendering via Partials

In practice, partials can also be used to express recursion, and that style is often easier to understand than `{{*children}}` for readers who already think in terms of “render this sub-template again”.

A common pattern is:

1. render the current node
2. include a partial for the children
3. let that partial render the same structure again

This approach is often easier to maintain when the recursive fragment is large, because the recursive logic is isolated in a dedicated template file instead of being mixed into a single section body.

The project already uses partial loading through `IPartialFileHandler`, so recursive partial composition fits naturally into the existing rendering model.

For example, the entry template can stay very small:

```mustache
Tree root: {{id}}
{{>recursion}}
```

And the recursive part can live in a separate partial:

```mustache
{{id}}{{#children}}
  {{id}}({{-index}}){{^-last}},{{/-last}}
  {{>recursion}}
{{/children}}
```

This style is often easier to read because:

- the entry template stays focused on top-level output
- the recursive logic is isolated in one reusable partial
- debugging nested rendering becomes easier when combined with stack-trace output

### Embedded Expressions

Use `{{% ... }}` to evaluate an expression block and append the final value to the output:

```mustache
{{% val=obj_getVar("id");text="id is " + val;val == 1 ? text : ""}}
```

The expression engine supports:

- assignments
- strings and numbers
- arithmetic operators: `+ - * /`
- comparison operators: `== != < > <= >=`
- logic operators: `&& || !`
- ternary expressions: `cond ? a : b`
- function calls
- multiple statements separated by `;`

Grammar reference:

- [`multiexpr/src/main/antlr4/com/servpp/multiexpr/antlr/MultiExpr.g4`](./multiexpr/src/main/antlr4/com/servpp/multiexpr/antlr/MultiExpr.g4)

### Sections Backed by Expression Variables

If a section name starts with `$`, the renderer reads data from the expression environment rather than from the current object:

```mustache
{{% TEST=id!=1? "true":null;#""}}
{{#$TEST}}
  Calculated id is : {{%TEST}}
{{/TEST}}
```

This allows compute-then-render patterns directly in the template.

### Partials with Parameters and Return Variables

Partials support a custom calling form:

```mustache
{{>partialName(param1,param2)[ret1,ret2]}}
```

Semantics:

- `(param1,param2)`: variables copied into the partial context before rendering
- `[ret1,ret2]`: variables copied back to the caller after rendering

This is a project-specific extension and is more powerful than standard Mustache partial behavior.

## Expression Environment

The `multiexpr` module provides a default evaluation environment used by `MustacheWriter`.

Common built-in functions include:

- `obj_getVar(name)`
- `obj_getCurVar(name)`
- `obj_getCurObj()`
- `obj_getParObj()`
- `str_sub(str, begin, end)`
- `str_indexOf(str, sub)`
- `obj_newList()` / `obj_addToList(...)` / `obj_getFromList(...)`
- `obj_newMap()` / `obj_addToMap(...)` / `obj_getFromMap(...)`
- `println(x)`

Mustache also adds:

- `must_saveBuff()`
- `must_popBuff()`
- `must_getStackTrace(deep)`
- `must_getShortStackTrace(deep)`

These stack-trace helpers are especially useful when debugging nested partial calls or recursive rendering.

You can print the current template call stack directly inside an expression block:

```mustache
{{%
    println(must_getStackTrace());
    ""
}}
```

Or print a shorter version:

```mustache
{{%
    println(must_getShortStackTrace());
    ""
}}
```

There is already a sample in [`mustache/src/main/resources/recursion.mustache`](./mustache/src/main/resources/recursion.mustache) showing `must_getStackTrace()` in action.

When partials call other partials, or when recursion becomes deep, the stack output helps answer questions such as:

- which template is currently rendering
- which partial called the current partial
- how deep the current rendering path is
- whether rendering entered an unexpected recursive branch

You can extend the environment like this:

```java
writer.getExprEvaluator().setEnvironment(writer.new MustacheEnvironment() {
    @Override
    public void addDefault() {
        super.addDefault();
        addFunction("str_replace", args ->
                ((String) args[0]).replace((String) args[1], (String) args[2]));
    }
});
```

## Partial Loading

`MustacheWriter` does not decide where partials come from. That responsibility is delegated to `IPartialFileHandler`.

This makes it easy to load partials from:

- the file system
- classpath resources
- a database or cache
- any custom lookup strategy

## Core Files

- [`mustache/src/main/antlr4/com/servpp/mustache/antlr/Mustache.g4`](./mustache/src/main/antlr4/com/servpp/mustache/antlr/Mustache.g4): extended Mustache grammar
- [`mustache/src/main/java/com/servpp/mustache/handler/MustacheListenerImpl.java`](./mustache/src/main/java/com/servpp/mustache/handler/MustacheListenerImpl.java): parse tree to template model
- [`mustache/src/main/java/com/servpp/mustache/handler/MustacheWriter.java`](./mustache/src/main/java/com/servpp/mustache/handler/MustacheWriter.java): rendering core
- [`multiexpr/src/main/java/com/servpp/multiexpr/handler/ExprEvaluator.java`](./multiexpr/src/main/java/com/servpp/multiexpr/handler/ExprEvaluator.java): expression evaluator
- [`multiexpr/src/main/java/com/servpp/multiexpr/IEvaluatorEnvironment.java`](./multiexpr/src/main/java/com/servpp/multiexpr/IEvaluatorEnvironment.java): environment and built-in functions

## Differences from Standard Mustache

- this is not a strict “logic-less template” implementation
- inverted section behavior currently focuses on `null`
- `@`, `*`, `%`, and `$` are custom syntax extensions
- partial parameters and return variables are also custom extensions

## Next Improvements You May Want

- a complete partial parameter/return example
- a Map iteration example
- a SQL or DDL generation example
- a side-by-side comparison with standard Mustache
- a short debugging and error-location guide
