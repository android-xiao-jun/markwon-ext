package io.noties.markwon.sample;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import io.noties.prism4j.GrammarLocator;
import io.noties.prism4j.Prism4j;

/**
 * 手写的代码高亮语法定义。
 *
 * <p>官方做法是用 {@code prism4j-bundler} 注解处理器生成全量语法，但那会引入一套
 * annotationProcessor 流程，案例里没必要。这里只覆盖最常用的几种语言、每种挑几类 token，
 * 足以验证 markwon-syntax-highlight 链路是通的。
 *
 * <p>要点：token 名字必须是 {@code Prism4jThemeBase} 认识的那套（comment / string / keyword
 * / number / function / tag / attr-value / property ...），否则拿不到对应颜色。
 */
public class SampleGrammarLocator implements GrammarLocator {

    private static final Set<String> LANGUAGES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("java", "kotlin", "groovy", "json", "xml")));

    @Nullable
    @Override
    public Prism4j.Grammar grammar(@NonNull Prism4j prism4j, @NonNull String language) {
        switch (language) {
            case "java":
            case "kotlin":
            case "groovy":
                return jvmLike(prism4j, language);
            case "json":
                return json(prism4j);
            case "xml":
                return xml(prism4j);
            default:
                return null;
        }
    }

    @NonNull
    @Override
    public Set<String> languages() {
        return LANGUAGES;
    }

    private static Prism4j.Grammar jvmLike(@NonNull Prism4j prism4j, @NonNull String name) {
        return Prism4j.grammar(name,
                Prism4j.token("comment", Prism4j.pattern(
                        Pattern.compile("/\\*[\\s\\S]*?\\*/|//[^\\n]*"))),
                Prism4j.token("annotation", Prism4j.pattern(
                        Pattern.compile("@[A-Za-z_$][\\w$.]*"))),
                Prism4j.token("string", Prism4j.pattern(
                        Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\r\\n])*\""))),
                Prism4j.token("char", Prism4j.pattern(
                        Pattern.compile("'(?:\\\\.|[^'\\\\\\r\\n])'"))),
                Prism4j.token("keyword", Prism4j.pattern(
                        Pattern.compile("\\b(?:abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while"
                                + "|fun|val|var|object|companion|when|is|in|data|sealed|suspend|lateinit|typealias|init|constructor|override|get|set|def|println)\\b"))),
                Prism4j.token("boolean", Prism4j.pattern(
                        Pattern.compile("\\b(?:true|false|null|nil)\\b"))),
                Prism4j.token("number", Prism4j.pattern(
                        Pattern.compile("\\b0[xX][\\da-fA-F]+\\b|\\b\\d+(?:\\.\\d+)?[fFlLdD]?\\b"))),
                Prism4j.token("function", Prism4j.pattern(
                        Pattern.compile("\\b[A-Za-z_$][\\w$]*(?=\\s*\\()"))),
                Prism4j.token("operator", Prism4j.pattern(
                        Pattern.compile("(?:==|!=|<=|>=|&&|\\|\\||\\+\\+|--|[-+*/%=<>!&|^~?:])")))
        );
    }

    private static Prism4j.Grammar json(@NonNull Prism4j prism4j) {
        return Prism4j.grammar("json",
                Prism4j.token("property", Prism4j.pattern(
                        Pattern.compile("\"(?:\\\\.|[^\"\\\\\\r\\n])*\"(?=\\s*:)"))),
                Prism4j.token("string", Prism4j.pattern(
                        Pattern.compile("\"(?:\\\\.|[^\"\\\\\\r\\n])*\""))),
                Prism4j.token("boolean", Prism4j.pattern(
                        Pattern.compile("\\b(?:true|false|null)\\b"))),
                Prism4j.token("number", Prism4j.pattern(
                        Pattern.compile("-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b"))),
                Prism4j.token("punctuation", Prism4j.pattern(
                        Pattern.compile("[{}\\[\\],:]")))
        );
    }

    private static Prism4j.Grammar xml(@NonNull Prism4j prism4j) {
        return Prism4j.grammar("xml",
                Prism4j.token("comment", Prism4j.pattern(
                        Pattern.compile("<!--[\\s\\S]*?-->"))),
                Prism4j.token("tag", Prism4j.pattern(
                        Pattern.compile("</?[\\w:.-]+(?:\\s+[\\w:.-]+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'))*\\s*/?>"))),
                Prism4j.token("attr-value", Prism4j.pattern(
                        Pattern.compile("(?:\\s[\\w:.-]+\\s*=\\s*)\"[^\"]*\"")))
        );
    }
}
