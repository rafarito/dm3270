package com.bytezone.dm3270.architecture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.Dependency;

// Detecta ciclos mutuos (A <-> B) no primeiro nivel abaixo de dm3270.* e reporter.*.
// -----------------------------------------------------------------------------------//
final class PackageCycles
// -----------------------------------------------------------------------------------//
{
  private static final String DM3270 = "com.bytezone.dm3270.";
  private static final String REPORTER = "com.bytezone.reporter.";

  // ---------------------------------------------------------------------------------//
  private PackageCycles ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // Ordem alfabetica estavel ("a <-> b") para manter mensagens de falha comparaveis.
  // ---------------------------------------------------------------------------------//
  static List<String> mutualCycles (JavaClasses classes)
  // ---------------------------------------------------------------------------------//
  {
    Map<String, Set<String>> graph = buildGraph (classes);
    Set<String> pairs = new TreeSet<> ();

    for (Map.Entry<String, Set<String>> entry : graph.entrySet ())
    {
      String from = entry.getKey ();
      for (String to : entry.getValue ())
      {
        Set<String> reverse = graph.get (to);
        if (reverse != null && reverse.contains (from))
          pairs.add (from.compareTo (to) < 0 ? from + " <-> " + to : to + " <-> " + from);
      }
    }

    return new ArrayList<> (pairs);
  }

  // ---------------------------------------------------------------------------------//
  private static Map<String, Set<String>> buildGraph (JavaClasses classes)
  // ---------------------------------------------------------------------------------//
  {
    Map<String, Set<String>> graph = new HashMap<> ();

    for (JavaClass javaClass : classes)
    {
      String from = sliceOf (javaClass.getPackageName ());
      if (from == null)
        continue;

      for (Dependency dependency : javaClass.getDirectDependenciesFromSelf ())
      {
        String to = sliceOf (dependency.getTargetClass ().getPackageName ());

        if (to == null || to.equals (from))
          continue;

        graph.computeIfAbsent (from, key -> new HashSet<> ()).add (to);
      }
    }

    return graph;
  }

  // ---------------------------------------------------------------------------------//
  private static String sliceOf (String packageName)
  // ---------------------------------------------------------------------------------//
  {
    if (packageName.startsWith (DM3270))
      return "dm3270." + firstSegment (packageName.substring (DM3270.length ()));

    if (packageName.startsWith (REPORTER))
      return "reporter." + firstSegment (packageName.substring (REPORTER.length ()));

    return null;
  }

  // ---------------------------------------------------------------------------------//
  private static String firstSegment (String remainder)
  // ---------------------------------------------------------------------------------//
  {
    int dot = remainder.indexOf ('.');
    return dot < 0 ? remainder : remainder.substring (0, dot);
  }
}
