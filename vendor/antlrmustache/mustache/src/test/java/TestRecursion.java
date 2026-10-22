import com.servpp.multiexpr.handler.DefaultEnvironment;
import com.servpp.mustache.MustacheCompiler;
import com.servpp.mustache.handler.*;
import com.servpp.mustache.model.BaseSection;
import com.servpp.mustache.model.Template;


import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-11-18@version 1.0
 */
public class TestRecursion {
    static URL mustacheFile = TestRecursion.class.getResource("/TestTree.mustache");
    static class Component{
        int id;
        List<Component> children = new ArrayList<>();

        public Component(int id) {
            this.id = id;
        }

        public Component add(Component child){
            children.add(child);
            return this;
        }

        @Override
        public String toString() {
            return "Component{" +
                    "id=" + id +
                    ", children=" + children +
                    '}';
        }
    }
    class TestRecursionWriter extends MustacheWriter{

        public TestRecursionWriter(URI mustacheFile, Object currentObj) {
            super(mustacheFile, currentObj);
            getExprEvaluator().setEnvironment(new MustacheWriter.MustacheEnvironment(){
                @Override
                public void addDefault() {
                    super.addDefault();
                    addFunction("str_replace", args -> ((String) args[0]).replace((String) args[1], (String)args[2]));
                }
            });
            setPartialFileHandler(new IPartialFileHandler() {
                @Override
                public Template compilePartialTemplate(String partialName) {
                    URL url = TestRecursion.class.getResource("/"+partialName +".mustache");
                    try {
                        MustacheCompiler mustacheCompiler = new MustacheCompiler(url);
                        try {
                            mustacheCompiler.compileAntlr4(null);
                            MustacheListenerImpl listener = new MustacheListenerImpl(url.toURI());
                            mustacheCompiler.workListener(listener);

                            return listener.getTemplate();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

        }
    }
    public void exec(String[] args) throws IOException, URISyntaxException {
        MustacheCompiler mustacheCompiler = new MustacheCompiler(mustacheFile);
        mustacheCompiler.compileAntlr4(null);
        MustacheListenerImpl impl = new MustacheListenerImpl(URI.create(mustacheFile.toString()));
        mustacheCompiler.workListener(impl);
        Component root = new Component(1);
        root.add(new Component(2).add(new Component(3).add(new Component(6))).add(new Component(5)));
        root.add(new Component(4));
        System.out.println(root);
        TestRecursionWriter writer = new TestRecursionWriter(URI.create(mustacheFile.toString()),root);
        writer.write(impl.getTemplate(), BaseSection.SectionType.Normal);
        StringBuffer sb = writer.getOutText();
        System.out.println(sb);
    }
    public static void main(String[] args) throws IOException, URISyntaxException {
        new TestRecursion().exec(args);
    }

}
