package com.bytezone.dm3270.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bytezone.dm3270.commands.AIDCommand;

/*
 * A politica de fim de escrita, isolada: o que o WriteCommand decidia ate o ciclo C2. O
 * HeadlessProcessingTest a exercita pelo comando; aqui cada condicao e cada ponta e vista
 * sozinha, inclusive a releitura do teclado depois da gravacao, que pelo comando nao da para
 * provocar.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("HostWriteCompletion - o que a tela faz quando o host termina de escrever")
class HostWriteCompletionTest
// -----------------------------------------------------------------------------------//
{
  private final List<String> calls = new ArrayList<> ();
  private final List<AIDCommand> replies = new ArrayList<> ();

  private int fieldCount = 1;
  private boolean keyboardLocked;
  private boolean recordingLocksKeyboard;
  private AIDCommand pluginReply = enter ();

  private final HostWriteCompletion completion =
      new HostWriteCompletion (() -> fieldCount, () -> keyboardLocked, this::record,
          this::runPlugins);

  // ---------------------------------------------------------------------------------//
  private void record ()
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("record");
    if (recordingLocksKeyboard)
      keyboardLocked = true;
  }

  // ---------------------------------------------------------------------------------//
  private AIDCommand runPlugins ()
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("plugins");
    return pluginReply;
  }

  // ---------------------------------------------------------------------------------//
  private void complete (boolean freshContent)
  // ---------------------------------------------------------------------------------//
  {
    completion.completed (freshContent, reply ->
    {
      calls.add ("reply");
      replies.add (reply);
    });
  }

  @Test
  @DisplayName ("com campo, teclado livre e conteudo novo: grava, roda os plugins, responde")
  void recordsRunsPluginsAndReplies ()
  {
    complete (true);

    assertEquals (List.of ("record", "plugins", "reply"), calls);
    assertSame (pluginReply, replies.get (0));
  }

  @Test
  @DisplayName ("sem conteudo novo: grava, e nao roda os plugins nem responde")
  void withoutFreshContentOnlyRecords ()
  {
    complete (false);

    assertEquals (List.of ("record"), calls);
  }

  @Test
  @DisplayName ("sem campo nenhum: nada")
  void withoutFieldsDoesNothing ()
  {
    fieldCount = 0;

    complete (true);

    assertEquals (List.of (), calls);
  }

  @Test
  @DisplayName ("com o teclado travado: nada")
  void withLockedKeyboardDoesNothing ()
  {
    keyboardLocked = true;

    complete (true);

    assertEquals (List.of (), calls);
  }

  @Test
  @DisplayName ("sem plugin que responda, a resposta nula ainda e entregue")
  void aNullPluginReplyIsStillDelivered ()
  {
    pluginReply = null;

    complete (true);

    assertEquals (List.of ("record", "plugins", "reply"), calls);
    assertNull (replies.get (0));
  }

  @Test
  @DisplayName ("o teclado e relido depois da gravacao, antes dos plugins")
  void keyboardIsReadAgainAfterRecording ()
  {
    recordingLocksKeyboard = true;

    complete (true);

    assertEquals (List.of ("record"), calls);
  }

  // ---------------------------------------------------------------------------------//
  private static AIDCommand enter ()
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = { AIDCommand.AID_ENTER, 0x40, 0x40 };
    return new AIDCommand (buffer, 0, buffer.length);
  }
}
