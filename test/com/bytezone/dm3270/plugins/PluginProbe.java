package com.bytezone.dm3270.plugins;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Gravador estatico porque PluginsStage instancia plugins por reflexao sem expor referencias
// e URLClassLoaders filhos compartilham esta classe via delegacao ao class loader pai.
// -----------------------------------------------------------------------------------//
public final class PluginProbe
// -----------------------------------------------------------------------------------//
{
  private static final List<String> calls = new ArrayList<> ();
  private static final Map<String, Deque<Boolean>> autoAnswers = new HashMap<> ();
  private static final Map<String, Deque<Boolean>> requestAnswers = new HashMap<> ();

  private PluginProbe ()
  {
  }

  // ---------------------------------------------------------------------------------//
  public static void reset ()
  // ---------------------------------------------------------------------------------//
  {
    calls.clear ();
    autoAnswers.clear ();
    requestAnswers.clear ();
  }

  // Limpa o log mantendo os roteiros configurados (ex.: apos getMenu() na montagem).
  // ---------------------------------------------------------------------------------//
  public static void clearCalls ()
  // ---------------------------------------------------------------------------------//
  {
    calls.clear ();
  }

  // ---------------------------------------------------------------------------------//
  public static void scriptAuto (String plugin, Boolean... answers)
  // ---------------------------------------------------------------------------------//
  {
    autoAnswers.put (plugin, new ArrayDeque<> (List.of (answers)));
  }

  // ---------------------------------------------------------------------------------//
  public static void scriptRequest (String plugin, Boolean... answers)
  // ---------------------------------------------------------------------------------//
  {
    requestAnswers.put (plugin, new ArrayDeque<> (List.of (answers)));
  }

  // ---------------------------------------------------------------------------------//
  public static void record (String call)
  // ---------------------------------------------------------------------------------//
  {
    calls.add (call);
  }

  // ---------------------------------------------------------------------------------//
  public static List<String> calls ()
  // ---------------------------------------------------------------------------------//
  {
    return new ArrayList<> (calls);
  }

  // Registra pergunta e resposta juntas; a ultima resposta da fila e repetida indefinidamente.
  // ---------------------------------------------------------------------------------//
  static boolean nextAuto (String plugin)
  // ---------------------------------------------------------------------------------//
  {
    boolean answer = next (autoAnswers, plugin);
    calls.add (plugin + ".doesAuto->" + answer);
    return answer;
  }

  // ---------------------------------------------------------------------------------//
  static boolean nextRequest (String plugin)
  // ---------------------------------------------------------------------------------//
  {
    boolean answer = next (requestAnswers, plugin);
    calls.add (plugin + ".doesRequest->" + answer);
    return answer;
  }

  // ---------------------------------------------------------------------------------//
  private static boolean next (Map<String, Deque<Boolean>> answers, String plugin)
  // ---------------------------------------------------------------------------------//
  {
    Deque<Boolean> queue = answers.get (plugin);
    if (queue == null || queue.isEmpty ())
      return false;

    return queue.size () == 1 ? queue.peek () : queue.poll ();
  }
}
