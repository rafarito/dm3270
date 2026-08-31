package com.bytezone.dm3270.application;

import com.bytezone.dm3270.utilities.DefaultTable;

/*
 * A tabela de replay. Veio do pacote session, onde era o unico widget.
 *
 * Ela nao produzia violacao da regra congelada - o ArchUnit atribui as chamadas herdadas ao
 * tipo estatico do receptor, que era SessionTable, e nao a TableView -, entao mover nao muda
 * placar nenhum. O motivo de mover e outro: as cinco strings abaixo se ligam por reflexao a
 * metodos xxxProperty () do tipo da linha, e essa ligacao nao da erro de compilacao quando
 * quebra (§5.14). Tabela e linha tem de morar juntas para que uma renomeacao seja vista.
 *
 * Passou a ser de pacote no caminho: os quatro usuarios - CommandPane, ReplayStage, SpyPane e
 * ela mesma - estao todos em application.
 */
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
