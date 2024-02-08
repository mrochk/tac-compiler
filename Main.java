import org.antlr.v4.runtime.tree.ParseTree;

import java.io.*;

public class Main {
    static final String extension = ".ccl";
    static final String outputFile = "output.tac";
    static final String green = "\u001B[32m";
    static final String reset = "\u001B[0m";

    public static void failWith(final String errorMsg) {
        System.err.println(errorMsg);
        System.exit(1);
    }

    static boolean validFileName(String filePath) {
        if (filePath.length() > 3) {
            int    len = filePath.length();
            String ext = filePath.substring(len - 4, len);
            return ext.equals(extension);
        }
        return false;
    }

    public static void main(final String[] args) {
        if (args.length < 1) {
            failWith("Please provide file to process as argument.");
        }

        final String filePath = args[0];

        if (!validFileName(filePath)) {
            failWith("Invalid file extension for \"" + filePath + "\".");
        }

        final SyntaxAnalyser syntaxAnalyser = new SyntaxAnalyser(filePath);
        ParseTree tree = syntaxAnalyser.parse();
        syntaxAnalyser.outputResult();
        if (!syntaxAnalyser.parsingSuccessful()) { System.exit(1); }

        CCALSemanticAnalyser semanticAnalyser = new CCALSemanticAnalyser(tree);
        semanticAnalyser.performAnalysis();
        System.out.println();
        semanticAnalyser.outputResult();
        if (!semanticAnalyser.analysisSucceeded()) { System.exit(1); }

        IRCodeGenerator generator = new IRCodeGenerator(tree);
        String result = generator.generate();
        try {
            FileWriter fileWriter = new FileWriter(outputFile);
            fileWriter.write(result);
            fileWriter.close();
        } catch (IOException e) {
            failWith("Error when trying to write the result.");
        }
        
        System.out.println(
            green + 
            "\nIntermediate code generation succeeded." + 
            reset
        );
    }
}