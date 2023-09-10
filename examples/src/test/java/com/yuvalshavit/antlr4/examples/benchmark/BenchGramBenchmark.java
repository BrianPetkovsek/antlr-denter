package com.yuvalshavit.antlr4.examples.benchmark;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Function;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import com.yuvalshavit.antlr4.examples.grammarforker.GrammarForker;
import com.yuvalshavit.antlr4.examples.util.ParserUtils;
import com.yuvalshavit.antlr4.examples.util.QuickRandom;
import com.yuvalshavit.antlr4.examples.util.ResourcesReader;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.fail;

public class BenchGramBenchmark {
  private static final ResourcesReader resources = new ResourcesReader(BenchGramBenchmark.class);
  private static final double globalMultiplier = getGlobalMultiplier();

  private static final String TEST_PROVIDER = "test-provider-0";
  private static final long WARMUP = 3000;
  public static final int WARMUP_REPS = 10;
  private static final long RUNS = 25000;

  private static double getGlobalMultiplier() {
    // Set this to a low number to make the benchmarks all go faster.
    // This is useful when trying to find a good multiplier for a given benchdent program without having to wait
    // for each benchmark to complete.
    String globalMultiplierString = System.getProperty("benchgram.runs.multiplier");
    if (globalMultiplierString == null) {
      return 1;
    } else {
      return Double.parseDouble(globalMultiplierString);
    }
  }

  @DataProvider(name = TEST_PROVIDER)
  public Object[][] readParseFiles() {
    Set<String> files = Sets.newHashSet(resources.readFile("."));
    List<Object[]> parseTests = Lists.newArrayList();
    for (String fileName : files) {
      if (fileName.endsWith(".benchdent")) {
        double multiplier = 1;
        int dashIndex = fileName.indexOf("-");
        if (dashIndex > 0) {
          String num = fileName.substring(0, dashIndex);
          multiplier = Double.parseDouble(num);
        }
        parseTests.add(new Object[] { fileName, multiplier });
      }
    }
    return parseTests.toArray(new Object[parseTests.size()][]);
  }

  @Test(dataProvider = TEST_PROVIDER)
  public void dented(String fileName, double multiplier) {
    String source = resources.readFileToString(fileName);
    warmupAndRun(BenchGramDentingLexer.class, source, standardLexerTokens, multiplier);
  }

  @Test(dataProvider = TEST_PROVIDER)
  public void dentedRaw(String fileName, double multiplier) {
    String source = resources.readFileToString(fileName);
    warmupAndRun(BenchGramDentingLexer.class, source, dentedRawTokens, multiplier);
  }

  @Test(dataProvider = TEST_PROVIDER)
  public void braced(String fileName, double multiplier) {
    String dentedSource = resources.readFileToString(fileName);
    String bracedSource = GrammarForker.dentedToBraced(
      ParserUtils.getLexer(BenchGramDentingLexer.class, dentedSource),
      BenchGramDentingParser.INDENT,
      BenchGramDentingParser.DEDENT,
      BenchGramDentingParser.NL,
      "\n");

    warmupAndRun(BenchGramBracedLexer.class, bracedSource, standardLexerTokens, multiplier);
  }

  private <L extends Lexer> void warmupAndRun(Class<L> lexerClass, String source,
                                              Function<? super L, Tokens> lexerToIter,
                                              double multiplier)
  {
    System.out.printf("[%s]: %d tokens in %d chars%n",
      lexerClass.getSimpleName(),
      countTokens(lexerClass, source),
      source.toCharArray().length);
    for (int i = 0; i < WARMUP_REPS; ++i) {
      timedRuns(lexerClass, source, "warmup " + i, Math.round(WARMUP * multiplier * globalMultiplier), lexerToIter);
    }
    System.out.println();
    System.out.println("Starting main runs...");
    double time = timedRuns(lexerClass, source, "runs", Math.round(RUNS * multiplier * globalMultiplier), lexerToIter);
    System.out.println();
    System.out.println();
    fail(time + " ms per run"); // easy reporting.
  }

  private <L extends Lexer> double timedRuns(Class<L> lexerClass, String braced, String runDesc, long nRuns,
                         Function<? super L, Tokens> lexerToIter) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    int randomresult = runLexer(lexerClass, braced, nRuns, lexerToIter);
    stopwatch.stop();
    System.out.printf("[%s] %s resulted in random int: %d%n", lexerClass.getSimpleName(), runDesc, randomresult);
    long timeMs = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    double timePerRunMs = ((double) timeMs) / nRuns;
    System.out.printf("[%s] time: %f ms each (%d ms total, %d runs)%n",
      lexerClass.getSimpleName(),
      timePerRunMs,
      timeMs,
      nRuns);
    return timePerRunMs;
  }

  private <L extends Lexer> int runLexer(Class<L> lexerClass, String source, long runs,
                                         Function<? super L, Tokens> lexerToIter) {
    QuickRandom r = new QuickRandom();
    for (long i = 0; i < runs; ++i) {
      L lexer = ParserUtils.getLexer(lexerClass, source);
      Tokens iter = lexerToIter.apply(lexer);
      for (Token t = iter.nextToken(); t.getType() != Lexer.EOF; t = iter.nextToken()) {
        r.next();
      }
    }
    return r.next();
  }

  private int countTokens(Class<? extends Lexer> lexerClass, String source) {
    Lexer lexer = ParserUtils.getLexer(lexerClass, source);
    int count = 0;
    for (Token t = lexer.nextToken(); t.getType() != Lexer.EOF; t = lexer.nextToken()) {
      ++count;
    }
    return count;
  }

  private static abstract class Tokens {
    private final Stopwatch stopwatch = Stopwatch.createUnstarted();

    protected abstract Token pullToken();

    Token nextToken() {
      stopwatch.start();
      Token t = pullToken();
      stopwatch.stop();
      return t;
    }
  }

  private static final Function<Lexer, Tokens> standardLexerTokens = new Function<Lexer, Tokens>() {
    @Override
    public Tokens apply(final Lexer input) {
      return new Tokens(){
        @Override
        protected Token pullToken() {
          return input.nextToken();
        }
      };
    }
  };

  private static final Function<BenchGramDentingLexer, Tokens> dentedRawTokens = new Function<BenchGramDentingLexer, Tokens>() {
    @Override
    public Tokens apply(final BenchGramDentingLexer input) {
      return new Tokens() {
        @Override
        protected Token pullToken() {
          return input.rawNextToken();
        }
      };
    }
  };
}
