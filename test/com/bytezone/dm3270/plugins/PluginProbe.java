package com.bytezone.dm3270.plugins;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * O gravador por onde os dubles de plugin falam com o teste.
 *
 * Ele e estatico, e isso NAO e preguica: o PluginsStage instancia cada plugin por reflexao,
 * dentro de PluginEntry.instantiate (), e nunca devolve a instancia. Um teste nao tem como
 * segurar uma referencia ao objeto que esta observando - so o que esse objeto escrever num
 * lugar combinado de antemao. Guardar o roteiro e o log aqui e o unico caminho.
 *
 * O bloco 3 depende desta mesma propriedade por um segundo motivo: um URLClassLoader filho
 * delega ao pai antes de definir a classe, entao dois plugins carregados de JARs diferentes
 * enxergam ESTE mesmo PluginProbe, vindo do classpath da aplicacao. E assim que um teste de
 * colisao de FQCN consegue ouvir os dois lados.
 *
 * OS TRES DUBLES ESCREVEM NA MESMA LISTA, de proposito: e isso que faz cada caso afirmar
 * tambem a ORDEM entre colaboradores. Uma lista por duble perderia exatamente essa parte -
 * e o precedente e o ConsoleKeyPressTest do passo 8.
 */
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

  /*
   * Apaga o log sem apagar os roteiros. O getMenu () instancia, ativa e ja pergunta
   * doesRequest () duas vezes antes de o teste chegar ao que quer medir; quem esta medindo o
   * despacho automatico limpa o log depois da montagem e deixa o roteiro de pe.
   */
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

  /*
   * Pergunta e resposta ficam na MESMA linha do log, porque as duas importam: um despacho
   * por instanceof deixaria de perguntar, e um teste que so registrasse a resposta nao veria
   * diferenca nenhuma.
   *
   * Fila vazia repete a ultima resposta - um plugin de valor constante e escrito com um
   * elemento so.
   */
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
