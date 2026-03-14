package com.cqlplatform.service.authoring;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

/**
 * Wraps FreeMarker configuration for rendering CQL templates.
 * Templates are loaded from classpath:templates/cql/.
 */
@Service
@Slf4j
public class CqlTemplateEngine {

    private final Configuration cfg;

    public CqlTemplateEngine() {
        cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates/cql");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
    }

    /**
     * Render the given template with the provided data model.
     *
     * @param templatePath relative path within templates/cql/ (e.g. "modifiers/BooleanNot.ftl")
     * @param dataModel    the data model map
     * @return rendered string
     */
    public String render(String templatePath, Map<String, Object> dataModel) {
        try {
            Template template = cfg.getTemplate(templatePath);
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            return writer.toString();
        } catch (IOException | TemplateException e) {
            log.error("Failed to render template '{}': {}", templatePath, e.getMessage());
            throw new CqlTemplateRenderException("Template rendering failed: " + templatePath, e);
        }
    }

    public static class CqlTemplateRenderException extends RuntimeException {
        public CqlTemplateRenderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
