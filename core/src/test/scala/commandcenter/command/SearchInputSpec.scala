package commandcenter.command

import zio.test.*

object SearchInputSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] =
    suite("SearchInputSpec")(
      suite("tokenizeArgs")(
        test("empty input produces no tokens") {
          assertTrue(SearchInput.tokenizeArgs("") == List.empty)
        },
        test("blank input produces no tokens") {
          assertTrue(SearchInput.tokenizeArgs("   ") == List.empty)
        },
        test("single unquoted token") {
          assertTrue(SearchInput.tokenizeArgs("hello") == List("hello"))
        },
        test("multiple tokens separated by single spaces") {
          assertTrue(SearchInput.tokenizeArgs("-a 1 foo") == List("-a", "1", "foo"))
        },
        test("collapses consecutive spaces instead of producing empty tokens") {
          assertTrue(SearchInput.tokenizeArgs("a    b") == List("a", "b"))
        },
        test("ignores leading and trailing whitespace") {
          assertTrue(SearchInput.tokenizeArgs("  a b  ") == List("a", "b"))
        },
        test("treats tabs and newlines as separators too") {
          assertTrue(SearchInput.tokenizeArgs("a\tb\nc") == List("a", "b", "c"))
        },
        test("a double-quoted span with spaces becomes a single token") {
          assertTrue(SearchInput.tokenizeArgs("""-a 1 "some subcommand"""") == List("-a", "1", "some subcommand"))
        },
        test("a single-quoted span with spaces becomes a single token") {
          assertTrue(SearchInput.tokenizeArgs("""-a 1 'some subcommand'""") == List("-a", "1", "some subcommand"))
        },
        test("empty double quotes produce an empty-string token") {
          assertTrue(SearchInput.tokenizeArgs("""a "" b""") == List("a", "", "b"))
        },
        test("empty single quotes produce an empty-string token") {
          assertTrue(SearchInput.tokenizeArgs("a '' b") == List("a", "", "b"))
        },
        test("a lone pair of quotes with no surrounding text produces a single empty token") {
          assertTrue(SearchInput.tokenizeArgs(""""""") == List(""))
        },
        test("unquoted text immediately followed by a quoted span concatenates into one token") {
          assertTrue(SearchInput.tokenizeArgs("""foo"bar baz"""") == List("foobar baz"))
        },
        test("a quoted span immediately followed by unquoted text concatenates into one token") {
          assertTrue(SearchInput.tokenizeArgs(""""bar baz"qux""") == List("bar bazqux"))
        },
        test("two adjacent quoted spans with no whitespace between them concatenate into one token") {
          assertTrue(SearchInput.tokenizeArgs(""""foo""bar"""") == List("foobar"))
        },
        test("double quotes support \\\" as an escaped quote") {
          assertTrue(SearchInput.tokenizeArgs(""""a\"b"""") == List("""a"b"""))
        },
        test("double quotes support \\\\ as an escaped backslash") {
          assertTrue(SearchInput.tokenizeArgs(""""a\\b"""") == List("""a\b"""))
        },
        test("single quotes are fully literal - a backslash is not an escape character") {
          assertTrue(SearchInput.tokenizeArgs("""'a\b'""") == List("""a\b"""))
        },
        test("single quotes don't treat double quotes as special") {
          assertTrue(SearchInput.tokenizeArgs("""'say "hi"'""") == List("""say "hi""""))
        },
        test("double quotes don't treat single quotes as special") {
          assertTrue(SearchInput.tokenizeArgs(""""it's fine"""") == List("it's fine"))
        },
        test("an unterminated double quote leniently takes the rest of the input") {
          assertTrue(SearchInput.tokenizeArgs("""a "bcd""") == List("a", "bcd"))
        },
        test("an unterminated single quote leniently takes the rest of the input") {
          assertTrue(SearchInput.tokenizeArgs("a 'bcd") == List("a", "bcd"))
        },
        test("realistic mixed example with flags, a quoted value, and trailing plain args") {
          assertTrue(
            SearchInput.tokenizeArgs("""--name "John Doe" --tags 'a b' trailing""") ==
              List("--name", "John Doe", "--tags", "a b", "trailing")
          )
        }
      ),
      suite("splitStatements")(
        test("no semicolon yields a single statement") {
          assertTrue(SearchInput.splitStatements("md5 a") == List("md5 a"))
        },
        test("empty input yields no statements") {
          assertTrue(SearchInput.splitStatements("") == List.empty)
        },
        test("splits on a top-level semicolon and trims each statement") {
          assertTrue(SearchInput.splitStatements("md5 a; md5 b") == List("md5 a", "md5 b"))
        },
        test("splits on multiple top-level semicolons") {
          assertTrue(SearchInput.splitStatements("ss 1; ss 15; ss 30") == List("ss 1", "ss 15", "ss 30"))
        },
        test("drops empty statements from a trailing semicolon") {
          assertTrue(SearchInput.splitStatements("md5 a;") == List("md5 a"))
        },
        test("drops empty statements from a leading semicolon") {
          assertTrue(SearchInput.splitStatements(";md5 a") == List("md5 a"))
        },
        test("drops empty statements from consecutive semicolons") {
          assertTrue(SearchInput.splitStatements("md5 a;;md5 b") == List("md5 a", "md5 b"))
        },
        test("a semicolon inside double quotes doesn't split the statement") {
          assertTrue(SearchInput.splitStatements("""echo "a;b"""") == List("""echo "a;b""""))
        },
        test("a semicolon inside single quotes doesn't split the statement") {
          assertTrue(SearchInput.splitStatements("echo 'a;b'") == List("echo 'a;b'"))
        },
        test("only the semicolon after the closing quote splits") {
          assertTrue(SearchInput.splitStatements("""echo "a;b"; echo c""") == List("""echo "a;b"""", "echo c"))
        },
        test("an escaped quote inside double quotes doesn't end the quoted span early") {
          assertTrue(
            SearchInput.splitStatements("""echo "a\"; echo b"; echo c""") ==
              List("""echo "a\"; echo b"""", "echo c")
          )
        }
      ),
      suite("splitCommandAndRest")(
        test("matches the original regex-based split, including its edge cases") {
          def reference(input: String): (String, String) =
            input.split("[ ]+", 2) match {
              case Array(prefix, rest) => (prefix, s" $rest")
              case Array(prefix)       => (prefix, "")
            }

          val examples = List(
            "",
            "cmd",
            "cmd arg",
            "cmd  arg1  arg2",
            "cmd   ",
            "   cmd arg",
            "cmd\targ", // a tab isn't an ASCII space, so it doesn't split
            "cmd\t arg", // ...but a space right after a tab still does, keeping the tab with the command part
            "a b c d e"
          )

          assertTrue(examples.forall(s => SearchInput.splitCommandAndRest(s) == reference(s)))
        },
        test("matches the original regex-based split for many random inputs") {
          check(Gen.listOfBounded(0, 16)(Gen.elements('a', 'b', ' ', '\t')).map(_.mkString)) { s =>
            def reference(input: String): (String, String) =
              input.split("[ ]+", 2) match {
                case Array(prefix, rest) => (prefix, s" $rest")
                case Array(prefix)       => (prefix, "")
              }

            assertTrue(SearchInput.splitCommandAndRest(s) == reference(s))
          }
        }
      ),
      suite("splitOnWhitespace")(
        test("matches the original regex-based split, including its edge cases") {
          def reference(input: String): (String, String) =
            input.split("\\p{javaWhitespace}+", 2) match {
              case Array(prefix, rest) => (prefix, rest)
              case Array(prefix)       => (prefix, "")
            }

          val examples = List(
            "",
            "cmd",
            "cmd arg",
            "cmd  arg1  arg2",
            "cmd   ",
            "   cmd arg",
            "cmd\targ",
            "cmd\t arg",
            "cmd\narg",
            "a b c d e"
          )

          assertTrue(examples.forall(s => SearchInput.splitOnWhitespace(s) == reference(s)))
        },
        test("matches the original regex-based split for many random inputs") {
          check(Gen.listOfBounded(0, 16)(Gen.elements('a', 'b', ' ', '\t', '\n')).map(_.mkString)) { s =>
            def reference(input: String): (String, String) =
              input.split("\\p{javaWhitespace}+", 2) match {
                case Array(prefix, rest) => (prefix, rest)
                case Array(prefix)       => (prefix, "")
              }

            assertTrue(SearchInput.splitOnWhitespace(s) == reference(s))
          }
        }
      )
    )
}
