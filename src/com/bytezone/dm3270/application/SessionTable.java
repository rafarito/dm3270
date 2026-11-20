package com.bytezone.dm3270.application;

import com.bytezone.dm3270.utilities.DefaultTable;

// Tabela de replay; as strings das colunas ligam-se por reflexao aos getters de SessionRow.
// -----------------------------------------------------------------------------------//
class SessionTable extends DefaultTable<SessionRow>
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  SessionTable ()
  // ---------------------------------------------------------------------------------//
  {
    addColumnString ("mm:ss", 45, Justification.LEFT, "time");
    addColumnString ("Source", 55, Justification.LEFT, "sourceName");
    addColumnString ("Type", 70, Justification.LEFT, "commandType");
    addColumnString ("Command", 80, Justification.LEFT, "commandName");
    addColumnNumber ("Size", 55, "bufferSize");

    setPrefWidth (337);
  }
}
