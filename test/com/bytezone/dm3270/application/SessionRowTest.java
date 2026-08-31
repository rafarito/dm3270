package com.bytezone.dm3270.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.commands.WriteCommand;
import com.bytezone.dm3270.extended.CommandHeader;
import com.bytezone.dm3270.runtime.Source;
import com.bytezone.dm3270.session.SessionRecord;
import com.bytezone.dm3270.session.SessionRecord.SessionRecordType;

/*
 * A linha da tabela de replay, depois de separada do SessionRecord.
 *
 * Estes testes existem por um motivo so: provar que a separacao nao mexeu no que a tabela
 * mostra. As cinco Property recebem exatamente os valores que o SessionRecord derivou, e o
 * defeito do getTime () veio junto, sem ser ativado.
 *
 * Nao ha @ExtendWith (JavaFxToolkit.class): SimpleStringProperty nao precisa de toolkit.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("SessionRow - a linha da tabela, separada do registro")
class SessionRowTest
// -----------------------------------------------------------------------------------//
{
  private static final LocalDateTime WHEN = LocalDateTime.of (2024, 1, 15, 10, 30, 42);

  // ---------------------------------------------------------------------------------//
  private static SessionRecord record ()
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = { Command.WRITE_F1, 0x00, (byte) 0xC1, (byte) 0xC2 };
    return new SessionRecord (SessionRecordType.TN3270,
        new WriteCommand (buffer, 0, buffer.length), Source.SERVER, WHEN, true);
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("as cinco colunas recebem o que o registro derivou")
  void copiesTheFiveLabels ()
  // ---------------------------------------------------------------------------------//
  {
    SessionRecord sessionRecord = record ();
    SessionRow row = new SessionRow (sessionRecord);

    assertEquals ("30:42", row.timeProperty ().get ());
    assertEquals ("Server", row.getSourceName ());
    assertEquals ("TN3270", row.getCommandType ());
    assertEquals ("Write", row.getCommandName ());
    assertEquals (4, row.getBufferSize ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("a linha continua sabendo de qual registro veio")
  void keepsTheRecord ()
  // ---------------------------------------------------------------------------------//
  {
    SessionRecord sessionRecord = record ();

    assertSame (sessionRecord, new SessionRow (sessionRecord).getRecord ());
    assertEquals (sessionRecord.toString (), new SessionRow (sessionRecord).toString ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("um registro sem nome de comando deixa a coluna Command vazia")
  void namelessRecordLeavesTheColumnEmpty ()
  // ---------------------------------------------------------------------------------//
  {
    SessionRecord sessionRecord = new SessionRecord (SessionRecordType.TN3270E,
        new CommandHeader (new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00 }), Source.SERVER,
        WHEN, true);

    SessionRow row = new SessionRow (sessionRecord);

    assertNull (row.getCommandName ());
    assertEquals ("30:42", row.timeProperty ().get ());
  }

  /*
   * O defeito que veio do SessionRecord, agora do lado da linha. Continua inalcancavel pela
   * tabela, porque o PropertyValueFactory resolve timeProperty () antes de olhar para o getter.
   * Preservado sob a Regra 1 e registrado no BACKLOG-DEFEITOS.md.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("getTime () devolve o nome do comando, e nao a hora")
  void getTimeStillReturnsTheCommandName ()
  // ---------------------------------------------------------------------------------//
  {
    SessionRow row = new SessionRow (record ());

    assertEquals ("30:42", row.timeProperty ().get ());
    assertEquals ("Write", row.getTime ());
  }
}
