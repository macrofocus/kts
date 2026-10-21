import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * Runs every *Test class found in a directory and records each failure as
 * {@code FAIL <test> <exception class>}, so that runs against different
 * implementations (jts-core vs kts-core) can be compared.
 * <p>
 * Usage: {@code JtsCompatRunner <classesDir> <resultsFile> <logFile>}
 */
public class JtsCompatRunner {
  public static void main(String[] args) throws IOException {
    Path classesDir = new File(args[0]).toPath();
    List<String> classNames = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(classesDir)) {
      paths.map(p -> classesDir.relativize(p).toString())
          .filter(p -> p.endsWith("Test.class") && !p.contains("$"))
          .map(p -> p.substring(0, p.length() - ".class".length()).replace(File.separatorChar, '.'))
          .sorted()
          .forEach(classNames::add);
    }

    List<String> results = new ArrayList<>();
    PrintStream out = System.out;
    try (PrintStream log = new PrintStream(new FileOutputStream(args[2]), true, "UTF-8")) {
      // The tests print a lot; keep it out of the build output
      System.setOut(log);
      System.setErr(log);
      JUnitCore core = new JUnitCore();
      int total = 0;
      for (String name : classNames) {
        Class<?> c;
        try {
          c = Class.forName(name);
        } catch (Throwable t) {
          results.add("LOADFAIL\t" + name + "\t" + t.getClass().getName());
          continue;
        }
        if (Modifier.isAbstract(c.getModifiers())) continue;
        try {
          Result r = core.run(c);
          total += r.getRunCount();
          for (Failure f : r.getFailures()) {
            results.add("FAIL\t" + f.getDescription().getDisplayName() + "\t" + f.getException().getClass().getName());
          }
        } catch (Throwable t) {
          results.add("RUNFAIL\t" + name + "\t" + t.getClass().getName());
        }
      }
      results.sort(null);
      results.add(0, "TOTAL\t" + total);
    } finally {
      System.setOut(out);
    }
    Files.write(new File(args[1]).toPath(), results);
    // Some tests leave non-daemon threads behind
    System.exit(0);
  }
}
