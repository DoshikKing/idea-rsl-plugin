package dev.doshikking.rsl;

import dev.doshikking.rsl.RslTokens.Kind;
import dev.doshikking.rsl.RslTokens.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RslAnalysis {
    record Problem(int start, int end, String message, boolean warning) {
        Problem(int start, int end, String message) {
            this(start, end, message, false);
        }
    }
    record Result(List<Problem> problems, Set<Integer> declarations, Map<Integer, Integer> references) {}
    private record Symbol(Token declaration, boolean constant, boolean local) {}
    private record Scope(Scope parent, Map<String, Symbol> symbols) {
        Scope(Scope parent) {
            this(parent, new HashMap<>());
        }
    }
    private record Use(Scope scope, Token name) {}
    private record Target(boolean writable, Token name) {
        static final Target VALUE = new Target(false, null);
        static final Target MEMBER = new Target(true, null);
    }

    private static final Set<String> DECLARATIONS = Set.of("var", "const", "array", "macro", "class", "file", "record");
    private static final Set<String> BLOCK_END = Set.of("end", "else", "elif", "onerror");
    private static final Set<String> FILE_FLAGS =
            Set.of("append", "blob", "btr", "dbf", "dialog", "key", "mem", "normal", "sort", "txt", "write");
    private static final int MAX_DEPTH = 100;

    private final List<Token> tokens = new ArrayList<>();
    private final List<Problem> problems = new ArrayList<>();
    private final Map<Integer, Use> uses = new HashMap<>();
    private final Set<Integer> writes = new HashSet<>();
    private final Set<Integer> declarations = new HashSet<>();
    private final Scope module = new Scope(null);
    private Scope scope = module;
    private int position;
    private int blockDepth;
    private int expressionDepth;
    private boolean checkingCondition;
    private int withDepth;

    private RslAnalysis(CharSequence source) {
        boolean newLine = false;
        int state = 0;
        for (int offset = 0; offset < source.length(); ) {
            var match = RslTokens.scan(source, offset, source.length(), state);
            state = RslTokens.nextState(source, offset, match, state);
            switch (match.kind()) {
                case SPACE, LINE_COMMENT, BLOCK_COMMENT, OPEN_COMMENT -> {
                    if (match.kind() == Kind.OPEN_COMMENT) {
                        problems.add(new Problem(offset, offset + 2, "Незакрытый комментарий"));
                    }
                    for (int i = offset; i < match.end(); i++) {
                        newLine |= RslTokens.newLine(source.charAt(i));
                    }
                }
                default -> {
                    String text = source.subSequence(offset, match.end()).toString();
                    tokens.add(new Token(match.kind(), text, offset, match.end(), newLine));
                    newLine = false;
                }
            }
            offset = match.end();
        }
        tokens.add(new Token(Kind.EOF, "", source.length(), source.length(), newLine));
    }

    static List<Problem> analyze(CharSequence source) {
        return inspect(source).problems();
    }

    static Result inspect(CharSequence source) {
        var analysis = new RslAnalysis(source);
        analysis.body(false);
        analysis.handlers();
        while (analysis.atWord("elif") || analysis.atWord("else") || analysis.atWord("onerror")) {
            Token branch = analysis.advance();
            analysis.report(branch, "«" + branch.text() + "» вне подходящего блока");
            analysis.body(false);
        }
        if (analysis.takeWord("end")) {
            // The interpreter ignores the rest of a module after its optional final END.
            int end = analysis.tokens.get(analysis.position - 1).end();
            analysis.problems.removeIf(problem -> problem.start() >= end);
        }
        Map<Integer, Integer> references = new HashMap<>();
        for (Use use : analysis.uses.values()) {
            Symbol symbol = analysis.resolve(use);
            if (symbol == null) continue;
            references.put(use.name.start(), symbol.declaration.start());
            if (symbol.constant && analysis.writes.contains(use.name.start())) {
                analysis.report(use.name, "Нельзя изменить значение константы «" + use.name.text() + "»");
            }
        }
        return new Result(List.copyOf(analysis.problems), Set.copyOf(analysis.declarations), Map.copyOf(references));
    }

    private void body(boolean ifBranch) {
        while (!at(Kind.EOF) && !blockEnd()) {
            int start = position;
            try {
                if (take(Kind.SEMICOLON)) continue;
                if (unmatchedCloser()) {
                    Token closer = advance();
                    report(closer, "Лишняя закрывающая скобка «" + closer.text() + "»");
                    continue;
                }
                statement();
                if (ifBranch && (atWord("end") || atWord("else") || atWord("elif"))) {
                    warn(tokens.get(position - 1), "Добавьте «;» в конце инструкции в ветке «if»");
                } else if (!at(Kind.EOF) && !at(Kind.SEMICOLON) && !blockEnd() && !unmatchedCloser()) {
                    report(current(), "Ожидается «;» между инструкциями");
                }
            } catch (ParseFailure ignored) {
                if (position == start) advance();
                recoverStatement();
            }
        }
    }

    private void recoverStatement() {
        while (!at(Kind.EOF) && !at(Kind.SEMICOLON) && !blockEnd() && !current().lineBreakBefore()) {
            advance();
        }
    }

    private boolean unmatchedCloser() {
        return at(Kind.RPAREN) || at(Kind.RBRACKET);
    }

    private void statement() {
        Token first = current();
        boolean local = !localCall() && takeWord("local");
        boolean modified = local || takeWord("private");
        String word = current().kind() == Kind.KEYWORD ? current().word() : "";
        if (modified && !DECLARATIONS.contains(word)) {
            throw error("После «" + first.text() + "» ожидается объявление");
        }
        switch (word) {
            case "var", "const", "array" -> {
                advance();
                declarations(word, local);
            }
            case "macro", "class" -> {
                advance();
                definition(first, word, local);
            }
            case "if", "while", "for", "with" -> {
                advance();
                control(first, word);
            }
            case "file", "record" -> {
                advance();
                fileDeclaration(local);
            }
            case "import" -> {
                advance();
                do {
                    fileName();
                } while (take(Kind.COMMA));
            }
            case "break", "continue" -> advance();
            case "return" -> {
                advance();
                if (!at(Kind.EOF) && !at(Kind.SEMICOLON) && !blockEnd() && !current().lineBreakBefore()) {
                    expression(1);
                }
            }
            default -> {
                if (at(Kind.FORM) || at(Kind.OPEN_FORM)) form();
                else expression(1);
            }
        }
    }

    private void declarations(String word, boolean local) {
        do {
            Token name = name();
            declare(name, word.equals("const"), local);
            if (word.equals("array")) continue;
            type();
            if (take(Kind.ASSIGN)) {
                expression(1);
            } else if (word.equals("const")) {
                report(name, "Константе требуется начальное значение");
            }
        } while (take(Kind.COMMA));
    }

    private void definition(Token start, String word, boolean local) {
        Scope parent = scope;
        try {
            try {
                if (word.equals("class") && take(Kind.LPAREN)) {
                    name();
                    require(Kind.RPAREN, "Ожидается «)» после имени базового класса");
                }
                if (!isName(current()) && !at(Kind.OPEN_NAME)) {
                    throw error("Ожидается имя после «" + word + "»");
                }
                declare(name(), false, local);
                scope = new Scope(parent);
                formalParameters();
                if (word.equals("macro")) type();
            } catch (ParseFailure ignored) {
                if (scope == parent) scope = new Scope(parent);
                recoverStatement();
            }
            nestedBody(false);
            handlers();
            closeBlock(start, word);
        } finally {
            scope = parent;
        }
    }

    private void formalParameters() {
        if (!take(Kind.LPAREN) || take(Kind.RPAREN)) return;
        do {
            if (at(Kind.COMMA) || at(Kind.RPAREN)) throw error("Ожидается имя формального параметра");
            declare(name(), false, false);
            type();
        } while (take(Kind.COMMA));
        closeArgumentList("параметров");
    }

    private void control(Token start, String word) {
        try {
            if (word.equals("for")) forHeader();
            else condition(word);
        } catch (ParseFailure ignored) {
            recoverStatement();
        }
        if (word.equals("with")) withDepth++;
        try {
            nestedBody(word.equals("if"));
        } finally {
            if (word.equals("with")) withDepth--;
        }
        if (word.equals("if")) ifBranches();
        closeBlock(start, word);
    }

    private void forHeader() {
        if (!take(Kind.LPAREN) || take(Kind.RPAREN)) return;
        if (takeWord("var")) {
            declare(name(), false, false);
            type();
        } else {
            if (!isName(current()) && !at(Kind.OPEN_NAME)) {
                throw error("Ожидается переменная цикла «for»");
            }
            recordWrite(name());
        }
        for (int count = 0; at(Kind.COMMA); count++) {
            if (count == 3) throw error("В «for» допускается до четырёх параметров");
            advance();
            if (!at(Kind.COMMA) && !at(Kind.RPAREN)) expression(1);
        }
        closeArgumentList("параметров «for»");
    }

    private void ifBranches() {
        boolean elseSeen = false;
        while (atWord("elif") || atWord("else")) {
            Token branch = advance();
            if (branch.word().equals("else")) {
                if (elseSeen) report(branch, "В одном «if» допускается только одна ветвь «else»");
                elseSeen = true;
            } else {
                if (elseSeen) report(branch, "Ветвь «elif» должна располагаться перед «else»");
                try {
                    condition("elif");
                } catch (ParseFailure ignored) {
                    recoverStatement();
                }
            }
            nestedBody(true);
        }
    }

    private void condition(String word) {
        require(Kind.LPAREN, "Ожидается «(» после «" + word + "»");
        if (at(Kind.RPAREN)) {
            report(advance(), "Ожидается выражение в условии «" + word + "»");
            return;
        }
        checkingCondition = !word.equals("with");
        try {
            expression(1);
        } finally {
            checkingCondition = false;
        }
        require(Kind.RPAREN, "Ожидается «)» после условия «" + word + "»");
    }

    private void handlers() {
        boolean seen = false;
        while (atWord("onerror")) {
            Token handler = advance();
            if (seen) report(handler, "В одном блоке допускается только один обработчик «onerror»");
            seen = true;
            try {
                if (take(Kind.LPAREN)) {
                    declare(name(), false, false);
                    require(Kind.RPAREN, "Ожидается «)» после параметра «onerror»");
                }
            } catch (ParseFailure ignored) {
                recoverStatement();
            }
            nestedBody(false);
        }
    }

    private void nestedBody(boolean ifBranch) {
        if (blockDepth >= MAX_DEPTH) throw error("Слишком много вложенных блоков");
        blockDepth++;
        try {
            body(ifBranch);
        } finally {
            blockDepth--;
        }
    }

    private void closeBlock(Token start, String word) {
        if (!takeWord("end")) {
            report(start, "Блок «" + word + "» не закрыт словом «end»");
        }
    }

    private void fileDeclaration(boolean local) {
        declare(name(), false, local);
        require(Kind.LPAREN, "Ожидается «(» перед именем таблицы или файла");
        fileName();
        if (take(Kind.COMMA)) fileName();
        require(Kind.RPAREN, "Ожидается «)» после имени таблицы или файла");
        while (FILE_FLAGS.contains(current().word())) {
            String flag = advance().word();
            if (flag.equals("key") || flag.equals("sort")) {
                require(Kind.NUMBER, "Ожидается номер после «" + flag + "»");
            } else if (flag.equals("txt")) {
                take(Kind.NUMBER);
            }
        }
    }

    private void fileName() {
        if (at(Kind.STRING) || at(Kind.OPEN_STRING)) string();
        else name();
    }

    private void type() {
        if (take(Kind.COLON)) {
            if (current().text().equals("@")) advance();
            name();
        }
    }

    private void form() {
        Token form = advance();
        if (form.kind() == Kind.OPEN_FORM) {
            report(form, "Форма вывода не закрыта символом «]»");
            return;
        }
        int fields = 0;
        for (int i = 1; i < form.text().length(); i++) {
            if (form.text().charAt(i) == '#' && form.text().charAt(i - 1) != '#') fields++;
        }
        int arguments = take(Kind.LPAREN) ? arguments() : 0;
        if (fields != arguments) {
            report(form, "Число полей формы (" + fields + ") не совпадает с числом аргументов (" + arguments + ")");
        }
    }

    private int arguments() {
        int count = 0;
        if (!at(Kind.RPAREN)) {
            do {
                if (at(Kind.COMMA) || at(Kind.RPAREN)) throw error("Ожидается аргумент");
                expression(1);
                count++;
                while (take(Kind.COLON)) {
                    if (!take(Kind.NUMBER)) name();
                }
            } while (take(Kind.COMMA));
        }
        closeArgumentList("аргументов");
        return count;
    }

    private void closeArgumentList(String context) {
        if (take(Kind.RPAREN)) return;
        Kind kind = current().kind();
        if (isName(current()) || kind == Kind.NUMBER || kind == Kind.LITERAL || kind == Kind.STRING) {
            throw error("Ожидается «,» между элементами списка " + context + " или «)» в конце");
        }
        throw error("Ожидается «)» после " + context);
    }

    private Target expression(int minimum) {
        if (expressionDepth >= MAX_DEPTH) throw error("Слишком сложное вложенное выражение");
        expressionDepth++;
        try {
            Target left = operand();
            int precedence = precedence(current());
            while (precedence >= minimum) {
                Token operator = advance();
                if (operator.kind() == Kind.ASSIGN) {
                    if (!left.writable) report(operator, "Слева от «=» ожидается переменная или свойство");
                    else {
                        if (checkingCondition) {
                            warn(operator, "Присваивание «=» в условии. Для сравнения используйте «==»");
                        }
                        if (left.name != null) recordWrite(left.name);
                    }
                }
                expression(precedence == 1 ? precedence : precedence + 1);
                left = Target.VALUE;
                precedence = precedence(current());
            }
            return left;
        } finally {
            expressionDepth--;
        }
    }

    private Target operand() {
        Token token = current();
        if (atWord("not") || at(Kind.OPERATOR) && Set.of("+", "-", "@").contains(token.text())) {
            advance();
            expression(5);
            return Target.VALUE;
        }

        Target result;
        switch (token.kind()) {
            case NUMBER, LITERAL -> {
                advance();
                return Target.VALUE;
            }
            case BAD_NUMBER, BAD -> {
                advance();
                report(token, token.kind() == Kind.BAD_NUMBER ? "Неверная числовая константа" : "Недопустимый символ");
                return Target.VALUE;
            }
            case STRING, OPEN_STRING -> {
                string();
                return Target.VALUE;
            }
            case LPAREN -> {
                advance();
                result = expression(1);
                require(Kind.RPAREN, "Ожидается «)»");
            }
            default -> {
                if (localCall()) {
                    advance();
                    result = Target.VALUE;
                } else if (takeWord("this")) result = Target.VALUE;
                else if (isName(token) || token.kind() == Kind.OPEN_NAME) {
                    Token identifier = name();
                    recordUse(identifier);
                    result = new Target(true, identifier);
                }
                else throw error("Ожидается выражение");
            }
        }
        while (true) {
            if (take(Kind.DOT)) {
                name();
                result = Target.MEMBER;
            } else if (at(Kind.LPAREN)) {
                advance();
                arguments();
                // Calls and indexed properties use the same syntax in RSL.
                result = Target.MEMBER;
            } else if (take(Kind.LBRACKET)) {
                expression(1);
                require(Kind.RBRACKET, "Ожидается «]» после индекса");
                result = Target.MEMBER;
            } else {
                return result;
            }
        }
    }

    private static int precedence(Token token) {
        if (token.kind() == Kind.ASSIGN) return 1;
        if (token.kind() != Kind.OPERATOR && token.kind() != Kind.KEYWORD) return 0;
        return switch (token.word()) {
            case "==", "!=", "<", "<=", ">", ">=" -> 2;
            case "+", "-", "or" -> 3;
            case "*", "/", "and" -> 4;
            default -> 0;
        };
    }

    private void string() {
        Token string = advance();
        if (string.kind() == Kind.OPEN_STRING) {
            report(string, "Незакрытая строка");
            return;
        }
        int length = 0;
        for (int i = 1; i < string.text().length() - 1; i++) {
            length++;
            if (string.text().charAt(i) == '\\') {
                char escaped = string.text().charAt(++i);
                if (escaped == 'x' || escaped == 'X') {
                    if (i + 2 < string.text().length() - 1
                            && RslTokens.hex(string.text().charAt(i + 1))
                            && RslTokens.hex(string.text().charAt(i + 2))) {
                        i += 2;
                    } else {
                        problems.add(new Problem(string.start() + i - 1, string.start() + i + 1,
                                "После «\\x» ожидаются две шестнадцатеричные цифры"));
                    }
                } else if (escaped >= '0' && escaped <= '7') {
                    for (int digits = 1; digits < 3 && i + 1 < string.text().length() - 1; digits++) {
                        char next = string.text().charAt(i + 1);
                        if (next < '0' || next > '7') break;
                        i++;
                    }
                }
            }
        }
        if (length > 2047) report(string, "Строковая константа длиннее 2047 символов");
    }

    private Token name() {
        Token token = current();
        if (!isName(token) && token.kind() != Kind.OPEN_NAME) {
            throw error("Ожидается идентификатор");
        }
        advance();
        if (token.kind() == Kind.OPEN_NAME) report(token, "Специальное имя не закрыто символом «}»");
        if (token.kind() == Kind.NAME && token.text().length() > 80) {
            report(token, "Идентификатор длиннее 80 символов");
        }
        return token;
    }

    private static boolean isName(Token token) {
        // Built-in type words also occur as conversion functions, such as string() and date().
        return token.kind() == Kind.NAME || token.kind() == Kind.SPECIAL_NAME || token.kind() == Kind.TYPE;
    }

    private void declare(Token name, boolean constant, boolean local) {
        Scope owner = name.kind() == Kind.SPECIAL_NAME ? module : scope;
        declarations.add(name.start());
        if (owner.symbols.putIfAbsent(name.word(), new Symbol(name, constant, local)) != null) {
            report(name, "Повторное объявление «" + name.text() + "» в этой области видимости");
        }
    }

    private void recordUse(Token name) {
        if (withDepth == 0 || name.kind() == Kind.SPECIAL_NAME) {
            uses.putIfAbsent(name.start(), new Use(scope, name));
        }
    }

    private void recordWrite(Token name) {
        recordUse(name);
        writes.add(name.start());
    }

    private Symbol resolve(Use use) {
        Scope original = use.name.kind() == Kind.SPECIAL_NAME ? module : use.scope;
        for (Scope candidate = original; candidate != null; candidate = candidate.parent) {
            Symbol symbol = candidate.symbols.get(use.name.word());
            if (symbol != null && (!symbol.local || candidate == original)) return symbol;
        }
        return null;
    }

    private boolean localCall() {
        return atWord("local") && tokens.get(position + 1).kind() == Kind.LPAREN;
    }

    private boolean blockEnd() {
        return current().kind() == Kind.KEYWORD && BLOCK_END.contains(current().word());
    }

    private boolean atWord(String word) {
        return current().kind() == Kind.KEYWORD && current().word().equals(word);
    }

    private boolean takeWord(String word) {
        if (!atWord(word)) return false;
        advance();
        return true;
    }

    private boolean at(Kind kind) {
        return current().kind() == kind;
    }

    private boolean take(Kind kind) {
        if (!at(kind)) return false;
        advance();
        return true;
    }

    private Token require(Kind kind, String message) {
        if (!at(kind)) throw error(message);
        return advance();
    }

    private Token current() {
        return tokens.get(position);
    }

    private Token advance() {
        return tokens.get(position++);
    }

    private ParseFailure error(String message) {
        Token token = current();
        if (token.kind() == Kind.EOF && position > 0) token = tokens.get(position - 1);
        report(token, message);
        return new ParseFailure();
    }

    private void report(Token token, String message) {
        if (token.start() < token.end()) problems.add(new Problem(token.start(), token.end(), message));
    }

    private void warn(Token token, String message) {
        problems.add(new Problem(token.start(), token.end(), message, true));
    }

    private static final class ParseFailure extends RuntimeException {
        ParseFailure() {
            super(null, null, false, false);
        }
    }
}
