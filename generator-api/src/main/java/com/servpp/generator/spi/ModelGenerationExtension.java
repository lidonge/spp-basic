package com.servpp.generator.spi;

import java.io.IOException;
import java.util.Map;
import java.util.ServiceLoader;

/** Optional technical model generation. Base business generation has no dependency on providers. */
public interface ModelGenerationExtension {
    String feature();
    String decorateJavaSpringTemplate(String template) throws IOException;
    Map<String,Object> modelExtensions();
    Map<String,Object> propertyExtensions();

    static ModelGenerationExtension find(String feature) {
        ModelGenerationExtension selected = null;
        for (var extension : ServiceLoader.load(ModelGenerationExtension.class)) {
            if (!feature.equals(extension.feature())) continue;
            if (selected != null) throw new IllegalStateException("MODEL_GENERATION_PROVIDER_AMBIGUOUS: " + feature);
            selected = extension;
        }
        return selected;
    }
}
