package com.bytezone.dm3270.screen;

import java.util.Optional;

import com.bytezone.dm3270.commands.WriteControlCharacter;
import com.bytezone.dm3270.filetransfer.TransferManager;

/**
 * Contrato da tela exposto a pilha de protocolo em {@code Buffer.process}.
 * Agrega os papeis por consumidor e o nucleo compartilhado por WriteCommand e AIDCommand.
 */
// -----------------------------------------------------------------------------------//
public interface ScreenTarget extends DisplayScreen, KeyboardState, WriteControlTarget,
    HostReplyTarget, TsoCommandTarget, ApplicationHooks, ConsoleSwitch
// -----------------------------------------------------------------------------------//
{
  void lockKeyboard (String keyName);

  int getFieldCount ();

  Optional<Field> getFieldAt (int position);

  void buildFields (WriteControlCharacter wcc);

  void eraseAllUnprotected ();

  Cursor getScreenCursor ();

  void setCurrentScreen (ScreenOption value);

  void draw ();

  // TODO: remover quando o composition root injetar TransferManager diretamente.
  TransferManager getTransferManager ();
}
