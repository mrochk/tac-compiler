all: build

build: 
	@java -Xmx500M -cp "/usr/local/lib/antlr-4.7.1-complete.jar:\$$CLASSPATH" org.antlr.v4.Tool CCAL.g4 -no-listener -visitor
	@javac *.java

clean:
	@rm -f *.class *.interp *.tokens *Visitor.java *Listener.java *Parser.java *Lexer.java 

run: build
	java Main $(TARGET)

interpret: 
	java -jar TACi.jar $(TARGET)

