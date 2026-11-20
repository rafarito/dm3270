package com.bytezone.dm3270.datasets;

/** Ouvinte de conclusao de operacoes no armazenamento, recebendo o relatorio em texto. */
// -----------------------------------------------------------------------------------//
public interface StoreListener
// -----------------------------------------------------------------------------------//
{
  void storeCompleted (String report);
}
