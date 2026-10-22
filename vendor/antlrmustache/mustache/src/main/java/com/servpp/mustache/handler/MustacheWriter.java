package com.servpp.mustache.handler;

import com.servpp.multiexpr.IEvaluatorEnvironment;
import com.servpp.multiexpr.ReflectTool;
import com.servpp.multiexpr.handler.DefaultEnvironment;
import com.servpp.multiexpr.handler.ExprEvaluator;
import com.servpp.mustache.model.*;

import java.io.File;
import java.net.URI;
import java.util.*;

/**
 * Handles the rendering of Mustache templates.
 * This class processes the template blocks and evaluates expressions to generate the final output.
 *
 * @author lidong
 * @version 1.0
 * @date 2024-07-27
 */
public class MustacheWriter {
    // Indicates if indentations should be added when dealing with recursive blocks
    private boolean indentIfRecursive = true;
    private int indent = 0;

    // Stack to keep track of section indices during rendering
    private Stack<Integer> sectionIndexs = new Stack<>();

    // Handler for processing partial files
    private IPartialFileHandler partialFileHandler;

    // Map to manage recursive executors based on section names
    private Map<String, Stack<IRecursiveExecutor>> recursiveExecutors = new HashMap<>();

    // Evaluator to handle expressions in the template
    private ExprEvaluator exprEvaluator = new ExprEvaluator();
    //The StringBuffer to store the rendered output
    private StringBuffer outText = new StringBuffer();
    //List of parent objects used in template evaluation
    private List<Object> parents = new ArrayList<>();

    //The current object in context
    private Object currentObj;
    private Stack<StringBuffer> outTextStack = new Stack<>();
    private boolean lookUpParents = false;
    private boolean nullVariableAsEmpty = false;
    private URI mustacheFile;

    private Stack<Template> templateStack = new Stack<>();
    public class MustacheEnvironment extends DefaultEnvironment{
        @Override
        public String getShortStackTrace(int deep){
            return MustacheWriter.this.getShortStackTrace(deep);
        }
        @Override
        public String getStackTrace(int deep){
            return MustacheWriter.this.getStackTrace(deep);
        }
        @Override
        public void addDefault() {
            super.addDefault();
            addFunction("must_saveBuff", args -> {
                StringBuffer sb = outText;
                outTextStack.push(outText);
                outText = new StringBuffer();
                return sb;
            });
            addFunction("must_popBuff", args -> {
                StringBuffer sb = outText;
                outText = outTextStack.pop();
                return sb;
            });
            addFunction("must_getStackTrace", args -> {
                return getStackTrace(parseDeep(args));
            });
            addFunction("must_getShortStackTrace", args -> {
                return getShortStackTrace(parseDeep(args));
            });
        }
    }

    static int parseDeep(Object[] args){
        int deep = -1;
        try{
            deep = (int) Double.parseDouble(args[0]+"");
        }catch (Throwable t){

        }
        return deep;
    }
    public MustacheWriter(URI mustacheFile, Object currentObj) {
        this(mustacheFile,currentObj,true);
    }
    public MustacheWriter(URI mustacheFile, Object currentObj, boolean lookUpParents) {
        this.currentObj = currentObj;
        this.lookUpParents = lookUpParents;
        this.mustacheFile = mustacheFile;
        exprEvaluator.setEnvironment(new MustacheEnvironment());
    }

    // Getter for the expression evaluator
    public ExprEvaluator getExprEvaluator() {
        return exprEvaluator;
    }

    // Getter for the partial file handler
    public IPartialFileHandler getPartialFileHandler() {
        return partialFileHandler;
    }

    // Setter for the partial file handler
    public MustacheWriter setPartialFileHandler(IPartialFileHandler partialFileHandler) {
        this.partialFileHandler = partialFileHandler;
        return this;
    }

    // Checks if indentation is enabled for recursive blocks
    public boolean isIndentIfRecursive() {
        return indentIfRecursive;
    }

    // Enables or disables indentation for recursive blocks
    public MustacheWriter setIndentIfRecursive(boolean indentIfRecursive) {
        this.indentIfRecursive = indentIfRecursive;
        return this;
    }

    public boolean isNullVariableAsEmpty() {
        return nullVariableAsEmpty;
    }

    public MustacheWriter setNullVariableAsEmpty(boolean nullVariableAsEmpty) {
        this.nullVariableAsEmpty = nullVariableAsEmpty;
        return this;
    }

    public StringBuffer getOutText() {
        return outText;
    }

    /**
     * Renders the template by processing each block in it.
     *
     * @param template    The Mustache template to be rendered
     * @param sectionType The type of section being processed (Normal, First, Last, etc.)
     */
    public void write( Template template, BaseSection.SectionType sectionType) {
        templateStack.push(template);
        for (IMustacheBlock block : template.getBlocks()) {
            dealBlock(block, sectionType);
        }
        templateStack.pop();
    }

    public String getShortStackTrace(int deep){
        return getStackTrace(deep, true);
    }
    public String getStackTrace(int deep){
        return getStackTrace(deep,false);
    }
    private String getStackTrace(int deep, boolean isShort){
        StringBuffer sb = new StringBuffer();
        sb.append("Mustache Stack Trace:\n");
        if(deep == -1 || deep > templateStack.size())
            deep = templateStack.size();

        for(int n = 0;n<deep;n++){
            Template template = templateStack.get(templateStack.size() - 1 - n);
            for(int i =0;i<n;i++)sb.append("  ");
            URI templateFile = template.getTemplateFile();
            sb.append(isShort ? new File(templateFile).getName():templateFile.toString()).append("\n");
        }
        if(deep != templateStack.size())
            sb.append("......");
        sb.append("End Mustache Stack Trace.\n");
        return sb.toString();
    }

    /**
     * Processes an individual block in the template.
     * The block could be a text, variable, section, partial, etc.
     *
     * @param block              The block to process
     * @param currentSectionType The type of the current section being processed
     */
    private void dealBlock(IMustacheBlock block, BaseSection.SectionType currentSectionType) {
        if (block instanceof Text) {
            writeText((Text) block);
        } else if (block instanceof Variable) {
            writeVariable((Variable) block, currentObj);
        } else if (block instanceof BaseSection) {
            BaseSection.SectionType sectionType = ((BaseSection) block).getSectionType();
            Template sub = ((BaseSection) block).getSubTemplate();
            if (sectionType == BaseSection.SectionType.Normal) {
                writeNormalSection( block, sub);
            } else {
                // Handle first and last sections separately
                writeFirstLast(block, currentSectionType, sectionType, sub);
            }
        } else if (block instanceof Partial block1) {
            List<Parameter> parameterList = block1.getParameterList();
            List<Parameter> returns = block1.getReturnsList();
            if(parameterList != null || returns != null)
                callOrReturnPartial(parameterList,false);
            Template sub = partialFileHandler.compilePartialTemplate(block1.getPartialName());
            write( sub, BaseSection.SectionType.Normal);
            if(parameterList != null || returns != null)
                callOrReturnPartial(returns, true);
        } else if (block instanceof SectionIndex) {
            outText.append(sectionIndexs.peek());
        } else if (block instanceof Recursive) {
            popAndExecute(((Recursive) block).getRecursiveName());
        } else if (block instanceof MultiExpr) {
            // Evaluate and render a multi-expression block
            String formula = ((MultiExpr) block).getMultiExpr();
            exprEvaluator.setVar(IEvaluatorEnvironment.OBJ_CURRENT_OBJ, currentObj);
            exprEvaluator.setVar(IEvaluatorEnvironment.OBJ_PARENTS, parents);
            Template tpl = templateStack.peek();
            Object obj = exprEvaluator.evalFormula(formula,tpl.getTemplateFile(),
                    ((MultiExpr) block).getBeginLineInMainFile());
            outText.append(obj);
        }
    }

    private void callOrReturnPartial(List<Parameter> parameterList, boolean isReturns) {
        String[] params = null;
        if(parameterList != null) {
            params = new String[parameterList.size()];
            for (int i = 0; i < params.length; i++) {
                params[i] = parameterList.get(i).getParamId();
            }
        }
        if(isReturns)
            getExprEvaluator().getEnvironment().endPartialCall(params);
        else
            getExprEvaluator().getEnvironment().partialCall(params);
    }

    private Object getQualifiedOrSimpleValue(String sectionName){
        Object ret = null;
        if(lookUpParents){
            ret = ReflectTool.getQualifiedOrSimpleValue(parents,currentObj, sectionName);
        }else{
            ret = ReflectTool.getQualifiedOrSimpleValue(null,currentObj, sectionName);
        }
        return ret;
    }

    /**
     * Processes a normal section block in the template.
     *
     * @param block      The block to process
     * @param sub        The sub-template associated with this section
     */
    private void writeNormalSection( IMustacheBlock block, Template sub) {
        String sectionName = ((BaseSection) block).getSectionName();
        boolean exprSection = ((BaseSection) block).isExprSection();
        Object sectionObj = exprSection ? exprEvaluator.getVar(sectionName)
                : getQualifiedOrSimpleValue(sectionName);
        sectionObj = "null".equals(sectionObj) ? null : sectionObj;
        if (block instanceof InvertedSection && sectionObj == null) {
            write(sub, BaseSection.SectionType.Normal);
        } else if (block instanceof Section && sectionObj != null) {
            Object[] array = null;
            array = getArray((Section) block, sectionObj, array);
            //should support length zero array, because dimString need
//            if(array != null && array.length == 0){
//                //skip
//            }else
            {
                parents.add(currentObj);
                if (array != null && array.length != 0) {
                    writeRecordArray(block, array, sectionName, sub);
                } else {
//                    if(exprSection && array == null)
//                        execWithNewCurrentObject(currentObj,()->write( sub, BaseSection.SectionType.Normal));
//                    else
                        execWithNewCurrentObject(sectionObj,()->write( sub, BaseSection.SectionType.Normal));
                }
                parents.remove(parents.size() -1);
            }
        }
    }

    private void execWithNewCurrentObject(Object newCurObj, Runnable runnable){
        Object oldObj = currentObj;
        currentObj = newCurObj;
        runnable.run();
        currentObj = oldObj;
    }
    /**
     * Processes an array of records within a section and renders them.
     *
     * @param block       The block to process
     * @param array       The array of objects to be processed in the section
     * @param sectionName The name of the section being processed
     * @param sub         The sub-template associated with this section
     */
    private void writeRecordArray( IMustacheBlock block, Object[] array, String sectionName, Template sub) {
        int iLast = array.length - 1;
        for (int i = 0; i < array.length; i++) {
            sectionIndexs.push(i);
            Object obj = array[i];

            writeRecordOfSection(block, i, iLast, obj, sectionName, sub);
        }
    }

    /**
     * Helper method to convert a section object into an array for processing.
     *
     * @param block      The section block being processed
     * @param sectionObj The object representing the section
     * @param array      The array to store the section objects
     * @return The array representation of the section object
     */
    private static Object[] getArray(Section block, Object sectionObj, Object[] array) {
        if (sectionObj instanceof List || sectionObj.getClass().isArray()) {
            if (sectionObj instanceof List) {
                array = ((List) sectionObj).toArray();
            } else {
                array = (Object[]) sectionObj;
            }
        } else if (sectionObj instanceof Map && block.isMapAsList()) {
            array = ((Map<?, ?>) sectionObj).values().toArray();
        }
        return array;
    }

    /**
     * Processes and renders an individual record within a section.
     *
     * @param block       The block to process
     * @param i           The index of the current record in the array
     * @param iLast       The index of the last record in the array
     * @param obj         The current object being processed
     * @param sectionName The name of the section being processed
     * @param sub         The sub-template associated with this section
     */
    private void writeRecordOfSection(IMustacheBlock block, int i, int iLast, Object obj, String sectionName, Template sub) {
        BaseSection.SectionType firstLast = i == 0 ? BaseSection.SectionType.First :
                (i == iLast ? BaseSection.SectionType.Last : BaseSection.SectionType.Normal);
        if (firstLast == BaseSection.SectionType.First && i == iLast)
            firstLast = BaseSection.SectionType.FirstAndLast;
        IRecursiveExecutor executor = new IRecursiveExecutor() {
            @Override
            public void execute() {
                Object subSectionObj = ReflectTool.getQualifiedValue(obj, sectionName);
                if (subSectionObj != null) {
                    int children = 0;
                    if(subSectionObj instanceof List){
                        children = ((List<?>) subSectionObj).size();
                    }else if(subSectionObj instanceof Map){
                        children = ((Map<?, ?>) subSectionObj).size();
                    }
                    if(children != 0) {
                        indent++;
                        execWithNewCurrentObject(obj, () -> dealBlock(block, BaseSection.SectionType.Normal));
                        indent--;
                    }
                }
            }
        };
        pushExecutor(sectionName, executor);
        final BaseSection.SectionType theFirstLast = firstLast;
        execWithNewCurrentObject(obj,()->write(sub, theFirstLast));

        if (((Section) block).isRecursive()) {
            popAndExecute(sectionName);
        } else {
            popWithoutExecute(sectionName);
        }
        sectionIndexs.pop();
    }

    /**
     * Processes first and last sections within the template.
     *
     * @param block              The block to process
     * @param currentSectionType The type of the current section being processed
     * @param sectionType        The type of the section being processed (First, Last, etc.)
     * @param sub                The sub-template associated with this section
     */
    private void writeFirstLast(IMustacheBlock block, BaseSection.SectionType currentSectionType, BaseSection.SectionType sectionType, Template sub) {
        boolean bInverted = block instanceof InvertedSection;
        boolean bSecTypeIsLast = sectionType == BaseSection.SectionType.Last;
        boolean bCurTypeIsLast = currentSectionType == BaseSection.SectionType.Last ||
                currentSectionType == BaseSection.SectionType.FirstAndLast;
        boolean bSecTypeIsFirst = sectionType == BaseSection.SectionType.First;
        boolean bCurTypeIsFirst = currentSectionType == BaseSection.SectionType.First ||
                currentSectionType == BaseSection.SectionType.FirstAndLast;
        if (!bInverted) {
            if (bSecTypeIsLast && bCurTypeIsLast) {
                write(sub, BaseSection.SectionType.Normal);
            } else {
                if (bSecTypeIsFirst && bCurTypeIsFirst) {
                    write(sub, BaseSection.SectionType.Normal);
                }
            }
        } else {
            if (bSecTypeIsLast && !bCurTypeIsLast) {
                write(sub, BaseSection.SectionType.Normal);
            } else {
                if (bSecTypeIsFirst && !bCurTypeIsFirst) {
                    write(sub, BaseSection.SectionType.Normal);
                }
            }
        }
    }

    /**
     * Appends text blocks to the output with optional indentation.
     *
     * @param block The text block to process
     */
    private void writeText(Text block) {
        String text = block.getText();
        if (indentIfRecursive && indent > 0) {
            outText.append(text.replace("\n", "\n" + "\t".repeat(indent)));
        } else {
            outText.append(text);
        }
    }

    /**
     * Processes and renders a variable block by evaluating its value.
     *
     * @param block      The variable block to process
     * @param currentObj The current object in context
     */
    private void writeVariable(Variable block, Object currentObj) {
        String varName = block.getVarName();
        Object value = "this".equals(varName) ? currentObj:getQualifiedOrSimpleValue( varName);
        if (value == null) {
            if (nullVariableAsEmpty) {
                return;
            }
            throw new NullPointerException("Variable " + varName + " is null");
        }
        outText.append(value);
    }

    /**
     * Executes the most recent recursive executor for a section.
     *
     * @param sectionName The name of the section to execute
     */
    private void popAndExecute(String sectionName) {
        Stack<IRecursiveExecutor> executors = recursiveExecutors.get(sectionName);
        if (executors != null && !executors.isEmpty()) {
            IRecursiveExecutor executor = executors.pop();
            executor.execute();
        }
    }

    /**
     * Removes the most recent recursive executor without executing it.
     *
     * @param sectionName The name of the section to remove the executor from
     */
    private void popWithoutExecute(String sectionName) {
        Stack<IRecursiveExecutor> executors = recursiveExecutors.get(sectionName);
        if (executors != null && !executors.isEmpty()) {
            executors.pop();
        }
    }

    /**
     * Adds a recursive executor to the stack for a given section name.
     *
     * @param sectionName The name of the section to add the executor to
     * @param executor    The recursive executor to add
     */
    private void pushExecutor(String sectionName, IRecursiveExecutor executor) {
        Stack<IRecursiveExecutor> executors = recursiveExecutors.get(sectionName);
        if (executors == null) {
            executors = new Stack<>();
            recursiveExecutors.put(sectionName, executors);
        }
        executors.push(executor);
    }
}
