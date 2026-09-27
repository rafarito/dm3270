package com.bytezone.dm3270.screen;

/*
 * Os sinais que um Write Control Character manda para a tela.
 *
 * Consumidor unico: WriteControlCharacter.process, que liga cada um pelo seu bit e chama
 * resetInsertMode () sempre, antes de todos. WriteControlCharacter nao e um Buffer, entao o
 * seu process nao e override de nada e pode pedir so este papel.
 */
// -----------------------------------------------------------------------------------//
public interface WriteControlTarget
// -----------------------------------------------------------------------------------//
{
  void resetInsertMode ();

  void resetPartition ();

  void startPrinter ();

  void soundAlarm ();

  void resetModified ();

  void restoreKeyboard ();
}
