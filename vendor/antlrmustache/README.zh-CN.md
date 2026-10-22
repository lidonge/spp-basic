# antlrmustache

[English Documentation](./README.md)

`antlrmustache` 是一个基于 ANTLR4 的 Java Mustache 解析与渲染项目。

它不仅实现了标准 Mustache 风格的模板能力，还增加了一些更适合代码生成和复杂模板编排的扩展能力，包括：

- 递归 section
- 内嵌表达式
- Map 按列表方式遍历
- `-index`、`-first`、`-last` 等循环辅助能力
- 带参数和返回值的 partial

仓库当前包含两个模块：

- `multiexpr`：表达式解析与执行引擎
- `mustache`：扩展版 Mustache 语法、模板模型与渲染器

## 这个项目适合什么场景

当普通的字符串替换已经不够用，而模板又需要一点点逻辑和结构化渲染能力时，这个项目会比较合适，例如：

- 生成 SQL、DDL、源代码或配置文件
- 递归渲染树形结构
- 用多个 partial 组合较大的模板
- 在模板内部完成少量计算和格式化

## 构建要求

- Java 17
- Maven 3.6.3+

构建命令：

```bash
mvn clean test
```

## 快速开始

基本流程是：

1. 用 `MustacheCompiler` 解析 `.mustache` 模板
2. 用 `MustacheListenerImpl` 构建模板模型
3. 用 `MustacheWriter` 渲染输出

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

如果模板里使用了 partial，需要额外提供 `IPartialFileHandler`：

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

## 扩展语法总览

除了标准 Mustache 的变量、section、反向 section、注释和 partial 之外，这个项目还支持以下扩展：

| 语法 | 含义 |
| --- | --- |
| `{{@name}}...{{/name}}` | 当 `name` 解析为 `Map` 时，按 `Map.values()` 遍历 |
| `{{-index}}` | 当前迭代项下标，从 `0` 开始 |
| `{{#-first}}...{{/-first}}` | 仅首项输出 |
| `{{^-first}}...{{/-first}}` | 非首项输出 |
| `{{#-last}}...{{/-last}}` | 仅尾项输出 |
| `{{^-last}}...{{/-last}}` | 非尾项输出 |
| `{{*children}}` | 在递归 section 中继续渲染同名子结构 |
| `{{#children}}...{{%children}}` | 将某个 section 标记为递归 section |
| `{{% ... }}` | 执行表达式并输出结果 |
| `{{#$var}}...{{/var}}` | 从表达式环境变量中读取 section 数据 |
| `{{>partial(a,b)[c]}}` | 调用带参数和返回值的 partial |

## 标准能力

### 变量

```mustache
{{id}}
{{user.name}}
{{this}}
```

- 支持简单名称和点号限定名
- `{{this}}` 表示当前上下文对象

### 普通 section

```mustache
{{#children}}
  {{id}}
{{/children}}
```

- 如果值是对象，则以该对象为当前上下文渲染一次
- 如果值是 `List` 或数组，则逐项渲染

### 反向 section

```mustache
{{^children}}
  no children
{{/children}}
```

当前实现里，反向 section 主要在值为 `null` 时触发。

### 注释

```mustache
{{! this is a comment }}
```

## 扩展能力详解

### Map 按列表方式遍历

如果某个 section 的值是 `Map`，并且你想遍历它的值，可以使用 `@`：

```mustache
{{@items}}
  {{name}}
{{/items}}
```

运行时效果相当于遍历 `items.values().toArray()`。

### 下标、首项、末项

在列表 section 中，渲染器提供了常见的辅助能力：

```mustache
{{#children}}
  {{id}}({{-index}}){{^-last}},{{/-last}}
{{/children}}
```

这对处理分隔符、逗号和边界元素格式很有帮助。

### 递归 section

递归渲染是这个项目最核心的扩展之一。

示例模板：

```mustache
{{id}}{{#children}}
  {{id}}({{-index}}){{^-last}},{{/-last}} {{*children}}{{%children}}
```

含义：

1. `{{#children}} ... {{%children}}` 定义递归 section
2. `{{*children}}` 会再次进入同名结构的递归渲染

可运行示例：

- [`mustache/src/main/resources/TestTree.mustache`](./mustache/src/main/resources/TestTree.mustache)
- [`mustache/src/test/java/TestRecursion.java`](./mustache/src/test/java/TestRecursion.java)

示例数据：

```java
Component root = new Component(1);
root.add(new Component(2).add(new Component(3).add(new Component(6))).add(new Component(5)));
root.add(new Component(4));
```

递归渲染默认会自动缩进，也可以关闭：

```java
writer.setIndentIfRecursive(false);
```

### 用 partial 实现递归

在实际使用里，`partial` 也可以用来表达递归，而且很多时候比 `{{*children}}` 更容易理解，因为它更接近“再次渲染这个子模板”这种直观模型。

一个常见思路是：

1. 先渲染当前节点
2. 再包含一个用于渲染子节点的 partial
3. 让这个 partial 继续渲染同样的结构

当递归片段比较大时，这种写法通常更容易维护，因为递归逻辑被拆到了独立模板文件中，而不是全部混在一个 section 里。

项目本身已经通过 `IPartialFileHandler` 支持 partial 加载，所以用 partial 组织递归模板和现有渲染模型是天然兼容的。

例如，入口模板可以保持得很简单：

```mustache
Tree root: {{id}}
{{>recursion}}
```

真正的递归逻辑则放在单独的 partial 里：

```mustache
{{id}}{{#children}}
  {{id}}({{-index}}){{^-last}},{{/-last}}
  {{>recursion}}
{{/children}}
```

这种写法通常更容易阅读，因为：

- 入口模板只负责顶层输出
- 递归逻辑集中在一个可复用 partial 中
- 和调用堆栈输出结合时，更容易调试多层嵌套渲染

### 模板内嵌表达式

使用 `{{% ... }}` 可以执行表达式块，并将最终值追加到输出中：

```mustache
{{% val=obj_getVar("id");text="id is " + val;val == 1 ? text : ""}}
```

表达式引擎支持：

- 赋值
- 字符串和数值
- 四则运算：`+ - * /`
- 比较运算：`== != < > <= >=`
- 逻辑运算：`&& || !`
- 三元表达式：`cond ? a : b`
- 函数调用
- 用 `;` 分隔多条表达式语句

语法文件：

- [`multiexpr/src/main/antlr4/com/servpp/multiexpr/antlr/MultiExpr.g4`](./multiexpr/src/main/antlr4/com/servpp/multiexpr/antlr/MultiExpr.g4)

### 用表达式变量驱动 section

如果 section 名称以 `$` 开头，渲染器会从表达式环境中取值，而不是从当前对象取值：

```mustache
{{% TEST=id!=1? "true":null;#""}}
{{#$TEST}}
  Calculated id is : {{%TEST}}
{{/TEST}}
```

这样就可以直接在模板里先计算，再决定是否渲染。

### 带参数和返回值的 partial

partial 支持一种扩展调用方式：

```mustache
{{>partialName(param1,param2)[ret1,ret2]}}
```

语义：

- `(param1,param2)`：在渲染 partial 之前，把这些变量复制到 partial 上下文
- `[ret1,ret2]`：在 partial 渲染结束后，把这些变量复制回调用方上下文

这是项目自定义扩展，能力比标准 Mustache 的 partial 更强。

## 表达式环境

`multiexpr` 模块提供了 `MustacheWriter` 使用的默认表达式环境。

常用内置函数包括：

- `obj_getVar(name)`
- `obj_getCurVar(name)`
- `obj_getCurObj()`
- `obj_getParObj()`
- `str_sub(str, begin, end)`
- `str_indexOf(str, sub)`
- `obj_newList()` / `obj_addToList(...)` / `obj_getFromList(...)`
- `obj_newMap()` / `obj_addToMap(...)` / `obj_getFromMap(...)`
- `println(x)`

Mustache 还额外加入了：

- `must_saveBuff()`
- `must_popBuff()`
- `must_getStackTrace(deep)`
- `must_getShortStackTrace(deep)`

这些调用堆栈函数在调试嵌套 partial 或递归渲染时尤其有用。

你可以直接在表达式块里打印当前模板调用堆栈：

```mustache
{{%
    println(must_getStackTrace());
    ""
}}
```

也可以打印更短的版本：

```mustache
{{%
    println(must_getShortStackTrace());
    ""
}}
```

仓库里的 [`mustache/src/main/resources/recursion.mustache`](./mustache/src/main/resources/recursion.mustache) 已经演示了 `must_getStackTrace()` 的实际用法。

当 partial 嵌套调用很多层，或者递归层级比较深时，这个堆栈输出很适合回答这些问题：

- 当前到底在渲染哪个模板
- 是哪个 partial 调用了当前 partial
- 当前调用链到底有多深
- 是否进入了预期之外的递归路径

扩展表达式环境的方式如下：

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

## partial 加载方式

`MustacheWriter` 不负责决定 partial 从哪里来，这件事由 `IPartialFileHandler` 处理。

因此 partial 可以来自：

- 文件系统
- classpath 资源
- 数据库或缓存
- 任意自定义查找策略

## 核心文件

- [`mustache/src/main/antlr4/com/servpp/mustache/antlr/Mustache.g4`](./mustache/src/main/antlr4/com/servpp/mustache/antlr/Mustache.g4)：扩展版 Mustache 语法
- [`mustache/src/main/java/com/servpp/mustache/handler/MustacheListenerImpl.java`](./mustache/src/main/java/com/servpp/mustache/handler/MustacheListenerImpl.java)：语法树到模板模型
- [`mustache/src/main/java/com/servpp/mustache/handler/MustacheWriter.java`](./mustache/src/main/java/com/servpp/mustache/handler/MustacheWriter.java)：渲染核心
- [`multiexpr/src/main/java/com/servpp/multiexpr/handler/ExprEvaluator.java`](./multiexpr/src/main/java/com/servpp/multiexpr/handler/ExprEvaluator.java)：表达式求值器
- [`multiexpr/src/main/java/com/servpp/multiexpr/IEvaluatorEnvironment.java`](./multiexpr/src/main/java/com/servpp/multiexpr/IEvaluatorEnvironment.java)：环境与内置函数定义

## 与标准 Mustache 的差异

- 它不是严格意义上的“无逻辑模板”
- 反向 section 当前更偏向 `null` 判断
- `@`、`*`、`%`、`$` 都是自定义扩展语法
- partial 参数与返回值也是自定义能力

## 后续还可以补充的内容

- 一个完整的 partial 参数/返回值示例
- 一个 Map 遍历示例
- 一个 SQL 或 DDL 生成示例
- 一个与标准 Mustache 的对照表
- 一个简短的调试和报错定位说明
