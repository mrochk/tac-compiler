# TAC Compiler
Compiler taking an hypothetical programming language and converting it to three-address code for a given interpreter using ANLTR4.

The programming language specifications can be found in [lang-specs.pdf](lang-specs.pdf).

The target interpreter specifications can be found in [interpreter-specs.pdf](interpreter-specs.pdf).

To compile a `.ccl` file run:
```
make run TARGET=<input-file>
```

If the compilation was successful, it will output a `.tac` file. \
Else, it will print the errors.

To run a `.tac` file using the given interpreter run:
```
make interpret TARGET=<compiled-file>
```