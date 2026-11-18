package dev.doshikking.rsl;

import java.util.ArrayList;
import java.util.List;

import static dev.doshikking.rsl.RslTokens.Kind;

public final class RslAnalysisTest {
    public static void main(String[] args) {
        lexer();
        validModules();
        diagnostics();
        System.out.println("RSL lexer and static analyzer checks passed");
    }

    private static void lexer() {
        String source = """
                VaR _счёт:MoNeY=$12.50, hex=#F2, value=.123e-2;
                /* outer /* inner */ tail */ // comment
                [ # /* raw text */ ] ({Параметр x}, "a\\"b");
                """;
        List<Kind> actual = new ArrayList<>();
        for (int offset = 0; offset < source.length(); ) {
            var match = RslTokens.scan(source, offset, source.length());
            assert match.end() > offset : match;
            if (match.kind() != Kind.SPACE) actual.add(match.kind());
            offset = match.end();
        }
        assert actual.equals(List.of(Kind.KEYWORD, Kind.NAME, Kind.COLON, Kind.TYPE, Kind.ASSIGN, Kind.NUMBER,
                Kind.COMMA, Kind.NAME, Kind.ASSIGN, Kind.NUMBER, Kind.COMMA, Kind.NAME, Kind.ASSIGN, Kind.NUMBER,
                Kind.SEMICOLON, Kind.BLOCK_COMMENT, Kind.LINE_COMMENT, Kind.FORM, Kind.LPAREN, Kind.SPECIAL_NAME,
                Kind.COMMA, Kind.STRING, Kind.RPAREN, Kind.SEMICOLON)) : actual;
    }

    private static void validModules() {
        clean("""
                import rslx, "report.mac";
                file f(client) btr key 0 write;
                record r(client, "other") txt 8;
                array values;
                values(0)(1) = #F2;
                const LIMIT = 10;
                private class (Base) Report(title: string)
                  var label = title;
                  macro printReport(arg:@variant):integer
                    var limit = 4;
                    for (var i:integer, 1, limit)
                      if (i == 1)
                        [ Сумма #### ] (arg:r:10)
                      elif (not false)
                        continue
                      else
                        break
                      end
                    end;
                    while (false) break end;
                    with(this)
                      label = string(arg);
                    end;
                    limit = local(arg);
                    return i
                  onerror (err)
                    println(err.message);
                  end;
                end;
                for (item, values) println(item) end;
                for (counter,,10,) break end;
                for break end;
                break;
                onerror (err)
                  println(err.message);
                end;
                /* trailing data after END is ignored
                """);
        clean("""
                macro calc
                  var n;
                  macro nested()
                    n = 2;
                  end;
                  nested;
                  return n
                end;
                actual = calc
                """);
    }

    private static void diagnostics() {
        problem("const PRICE = 10; price = 20;", "price", "Нельзя изменить");
        problem("const RATE = 10; for (rate, 1, 2) break end;", "rate", "Нельзя изменить");
        problem("var name; macro run(name, NAME) end;", "NAME", "Повторное объявление");
        problem("var a; if(true) var A end;", "A", "Повторное объявление");
        problem("const LIMIT:integer;", "LIMIT", "начальное значение");
        problem("1 = 2;", "=", "Слева");
        problem("if (true)\n println(1);", "if", "не закрыт");
        problem("else\n println(1);", "else", "вне подходящего блока");
        problem("while (true\nend;", "end", "Ожидается «)»");
        problem("var s = \"bad\nvar x;", "\"bad", "Незакрытая строка");
        problem("var n = $;", "$", "Неверная числовая");
        problem("var n = 1e-;", "1e-", "Неверная числовая");
        problem("var n; /* outer /* inner */", "/*", "Незакрытый комментарий");
        problem("[ ## ## ] (1);", "[ ## ## ]", "Число полей");
        problem("var n;\nvar n;", "n", "Повторное объявление");
        problem("var ", "var", "Ожидается идентификатор");
        String longName = "a".repeat(81);
        problem("var " + longName, longName, "длиннее 80");
        String deep = "if(true)\n".repeat(120) + "end;\n".repeat(120);
        assert RslAnalysis.analyze(deep).stream().anyMatch(p -> p.message().contains("вложенных блоков"));
    }

    private static void clean(String source) {
        var problems = RslAnalysis.analyze(source);
        assert problems.isEmpty() : problems;
    }

    private static void problem(String source, String text, String message) {
        var problems = RslAnalysis.analyze(source);
        assert problems.stream().anyMatch(p -> source.substring(p.start(), p.end()).equals(text)
                && p.message().contains(message)) : source + " " + problems;
    }
}
