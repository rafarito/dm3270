package com.bytezone.dm3270.watch;

import java.util.List;

import com.bytezone.dm3270.screen.Field;

// Porta de leitura de campos usada por ScreenWatcher para desacoplar de display.
// -----------------------------------------------------------------------------------//
public interface ScreenFields
// -----------------------------------------------------------------------------------//
{
  List<Field> getFields ();

  List<String> getMenus ();

  List<Field> getRowFields (int requestedRow);

  List<Field> getRowFields (int requestedRowFrom, int rows);

  boolean textMatches (int fieldNo, String text);

  boolean textMatches (int fieldNo, String text, int location);

  boolean textMatchesTrim (Field field, String text);
}
