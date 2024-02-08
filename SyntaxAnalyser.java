import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.runtime.dfa.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.*;

import java.util.*;
import java.io.*;

class SyntaxAnalyser {
    final private String filePath;
    private boolean parsingSuccessful = true;

    public SyntaxAnalyser(final String filePath) { 
        this.filePath = filePath; 
    }

    public static void failWith(final String errorMsg) {
        System.err.println(errorMsg);
        System.exit(1);
    }

    /* This error listener only tells us if the file was 
       successfully parsed or not, nothing else. */
    final private ANTLRErrorListener customErrorListener = new ANTLRErrorListener() {
        @Override
        public void reportAmbiguity(Parser p, org.antlr.v4.runtime.dfa.DFA d, int a, int b, boolean c, BitSet e, ATNConfigSet f) {}
        @Override
        public void reportAttemptingFullContext(Parser p, org.antlr.v4.runtime.dfa.DFA d, int a, int b, BitSet c, ATNConfigSet e) {}
        @Override
        public void reportContextSensitivity(Parser p, DFA d, int a, int b, int c, ATNConfigSet e) {}
        @Override
        public void syntaxError(Recognizer<?, ?> r, Object o, int a, int b, String c, RecognitionException re) {
            parsingSuccessful = false;
        }
    };

    public ParseTree parse() {
        CharStream stream = null;
        try { stream = CharStreams.fromFileName(filePath); } 
        catch (IOException e) {
            failWith("error when trying to open " + filePath);
        }

        final CCALLexer lexer = new CCALLexer(stream);

        lexer.removeErrorListeners();
        lexer.addErrorListener(customErrorListener);

        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final CCALParser        parser = new CCALParser(tokens);

        parser.removeErrorListeners();
        parser.addErrorListener(customErrorListener);

        return parser.program();
    }

    public boolean parsingSuccessful() { return parsingSuccessful; }

    public void outputResult() {
        System.out.println(filePath + (parsingSuccessful ? 
            " parsed successfully" : " has not parsed"));
    }
}