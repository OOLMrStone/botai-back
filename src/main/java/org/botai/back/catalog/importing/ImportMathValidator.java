package org.botai.back.catalog.importing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Component
public class ImportMathValidator {
    private final String node;
    private final String script;
    private final String katexPackage;
    public ImportMathValidator(@Value("${app.content-import.node:}") String node,@Value("${app.content-import.math-validator:}") String script,@Value("${app.content-import.katex-package:}") String katexPackage) { this.node=node;this.script=script;this.katexPackage=katexPackage; }
    public void validate(String value) {
        if(node.isBlank()||script.isBlank()||katexPackage.isBlank()||!Path.of(node).isAbsolute()||!Path.of(script).isAbsolute()||!Path.of(katexPackage).isAbsolute())throw new ImportFailure("math_validator_required");
        Process process=null;
        try {
            var builder=new ProcessBuilder(node,script).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.environment().clear();builder.environment().put("BOTAI_KATEX_PACKAGE",katexPackage);process=builder.start();
            try(var input=process.getOutputStream()) { input.write(value.getBytes(StandardCharsets.UTF_8)); }
            if(!process.waitFor(5,TimeUnit.SECONDS)||process.exitValue()!=0)throw new ImportFailure("invalid_math");
        } catch(ImportFailure failure) { throw failure; }
        catch(Exception failure) { throw new ImportFailure("math_validator_unavailable"); }
        finally { if(process!=null&&process.isAlive())process.destroyForcibly(); }
    }
}
