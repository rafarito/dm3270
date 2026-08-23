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

/*
 * Conta os ciclos mutuos entre pacotes: pares em que A depende de B e B depende de A.
 *
 * E a mesma metrica do diagnostico que motivou a refatoracao, que encontrou 25 desses pares
 * e apontou display participando de 9. Manter a contagem aqui permite acompanhar o
 * progresso com um numero unico, em vez de um baseline congelado de milhares de linhas.
 *
 * A granularidade e a do relatorio: o primeiro nivel abaixo de com.bytezone.dm3270 e de
 * com.bytezone.reporter - ou seja, display, commands, orders, reporter.file, e assim por
 * diante.
 */
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

  /*
   * Devolve os pares mutuos em ordem alfabetica estavel, no formato "a <-> b", para que a
   * mensagem de falha seja diffavel entre execucoes.
   */
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

  /*
   * Reduz um pacote ao seu primeiro nivel dentro do projeto. Classes que ficam direto em
   * com.bytezone.dm3270 ou fora do projeto devolvem null e nao entram no grafo.
   */
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
