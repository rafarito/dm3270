package com.bytezone.dm3270.filetransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// -----------------------------------------------------------------------------------//
@DisplayName ("IndFileCommand - parsing do comando IND$FILE")
class IndFileCommandTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("direcao da transferencia")
  class Direction
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("GET e download")
    void getIsDownload ()
    {
      IndFileCommand command = new IndFileCommand ("IND$FILE GET MEUARQ.TXT");

      assertTrue (command.isDownload ());
      assertFalse (command.isUpload ());
    }

    @Test
    @DisplayName ("PUT e upload")
    void putIsUpload ()
    {
      IndFileCommand command = new IndFileCommand ("IND$FILE PUT MEUARQ.TXT");

      assertTrue (command.isUpload ());
      assertFalse (command.isDownload ());
    }

    @Test
    @DisplayName ("aceita o comando em maiusculas ou minusculas")
    void isCaseInsensitive ()
    {
      assertTrue (new IndFileCommand ("ind$file get abc").isDownload ());
      assertTrue (new IndFileCommand ("IND$FILE GET ABC").isDownload ());
      assertTrue (new IndFileCommand ("Ind$File Get Abc").isDownload ());
    }

    @Test
    @DisplayName ("aceita a variante com libra (ind£file) de teclados britanicos")
    void acceptsPoundVariant ()
    {
      assertTrue (new IndFileCommand ("ind£file get abc").isDownload ());
    }

    @Test
    @DisplayName ("remove o prefixo TSO")
    void stripsTsoPrefix ()
    {
      IndFileCommand command = new IndFileCommand ("TSO IND$FILE GET MEUARQ");

      assertTrue (command.isDownload ());
      assertEquals ("meuarq", command.getDatasetName ());
    }

    @Test
    @DisplayName ("ignora espacos em excesso")
    void toleratesExtraWhitespace ()
    {
      IndFileCommand command = new IndFileCommand ("  tso   ind$file   get   abc  ");

      assertTrue (command.isDownload ());
      assertEquals ("abc", command.getDatasetName ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("nome do dataset")
  class DatasetName
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("nome sem aspas nao tem HLQ explicito")
    void unquotedNameHasNoHlq ()
    {
      IndFileCommand command = new IndFileCommand ("ind$file get meuarq.txt");

      assertEquals ("meuarq.txt", command.getDatasetName ());
      assertFalse (command.hasHLQ ());
    }

    @Test
    @DisplayName ("nome entre aspas tem HLQ e as aspas sao removidas")
    void quotedNameHasHlq ()
    {
      IndFileCommand command = new IndFileCommand ("ind$file get 'sys1.proclib(abc)'");

      assertEquals ("sys1.proclib(abc)", command.getDatasetName ());
      assertTrue (command.hasHLQ ());
    }

    @Test
    @DisplayName ("nome entre aspas sem ponto ainda conta como qualificado")
    void quotedNameWithoutDot ()
    {
      IndFileCommand command = new IndFileCommand ("ind$file get 'meuarq'");

      assertEquals ("meuarq", command.getDatasetName ());
      assertTrue (command.hasHLQ ());
    }

    @Test
    @DisplayName ("setDatasetName troca o nome")
    void canReplaceName ()
    {
      IndFileCommand command = new IndFileCommand ("ind$file get abc");

      command.setDatasetName ("xyz");

      assertEquals ("xyz", command.getDatasetName ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("opcoes")
  class Options
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("reconhece ascii e crlf")
    void recognisesAsciiAndCrlf ()
    {
      IndFileCommand command = new IndFileCommand ("ind$file get abc ascii crlf");

      assertTrue (command.getAscii ());
      assertTrue (command.getCrlf ());
    }

    @Test
    @DisplayName ("sem opcoes, ascii e crlf ficam desligados")
    void defaultsAreOff ()
    {
      IndFileCommand command = new IndFileCommand ("ind$file get abc");

      assertFalse (command.getAscii ());
      assertFalse (command.getCrlf ());
    }

    @Test
    @DisplayName ("as opcoes podem ser forcadas depois da construcao")
    void optionsCanBeOverridden ()
    {
      IndFileCommand command = new IndFileCommand ("ind$file get abc");

      command.setAscii (true);
      command.setCrlf (true);

      assertTrue (command.getAscii ());
      assertTrue (command.getCrlf ());
    }

    @Test
    @DisplayName ("le os parametros DCB no formato 'chave valor'")
    void parsesSpacedDcbParameters ()
    {
      IndFileCommand command =
          new IndFileCommand ("ind$file put abc recfm fb lrecl 80 blksize 3120");

      String text = command.toString ();
      assertTrue (text.contains ("RECFM .......... fb"), text);
      assertTrue (text.contains ("LRECL .......... 80"), text);
      assertTrue (text.contains ("BLKSIZE ........ 3120"), text);
    }

    @Test
    @DisplayName ("le os parametros DCB no formato 'chave(valor)'")
    void parsesParenthesisedDcbParameters ()
    {
      IndFileCommand command =
          new IndFileCommand ("ind$file put abc recfm(fb) lrecl(80) blksize(3120)");

      String text = command.toString ();
      assertTrue (text.contains ("RECFM .......... (fb)"), text);
      assertTrue (text.contains ("LRECL .......... (80)"), text);
      assertTrue (text.contains ("BLKSIZE ........ (3120)"), text);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("comandos invalidos")
  class InvalidCommands
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "\"{0}\"")
    @ValueSource (strings = { "", "listcat", "tso listcat", "copy get abc" })
    @DisplayName ("rejeita comando que nao e IND$FILE")
    void rejectsNonIndFile (String command)
    {
      IllegalArgumentException thrown =
          assertThrows (IllegalArgumentException.class, () -> new IndFileCommand (command));

      assertEquals ("No IND$FILE in that command", thrown.getMessage ());
    }

    @ParameterizedTest (name = "\"{0}\"")
    @ValueSource (strings = { "ind$file", "ind$file copy abc", "ind$file getx abc" })
    @DisplayName ("rejeita comando sem PUT nem GET")
    void rejectsMissingDirection (String command)
    {
      IllegalArgumentException thrown =
          assertThrows (IllegalArgumentException.class, () -> new IndFileCommand (command));

      assertEquals ("No PUT or GET in that command", thrown.getMessage ());
    }

    @ParameterizedTest (name = "\"{0}\"")
    @ValueSource (strings = { "ind$file get", "ind$file put", "tso ind$file get" })
    @DisplayName ("rejeita comando sem nome de dataset")
    void rejectsMissingDataset (String command)
    {
      IllegalArgumentException thrown =
          assertThrows (IllegalArgumentException.class, () -> new IndFileCommand (command));

      assertEquals ("No dataset name in that command", thrown.getMessage ());
    }

    @Test
    @DisplayName ("rejeita aspas vazias como nome de dataset")
    void rejectsEmptyQuotedName ()
    {
      IllegalArgumentException thrown = assertThrows (IllegalArgumentException.class,
          () -> new IndFileCommand ("ind$file get ''"));

      assertEquals ("No dataset name in that command", thrown.getMessage ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("construtores programaticos")
  class ProgrammaticConstructors
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("REGRESSAO: exigem setPrefix() antes de construir, senao lancam NPE")
    void requirePrefixBeforeConstruction ()
    {
      // setCommandText() chama prefix.isEmpty() sem checar null, e o campo so pode ser
      // preenchido por setPrefix() DEPOIS que o construtor ja rodou. Ou seja: hoje esses
      // dois construtores sao inutilizaveis. Nenhum ponto do codigo de producao os chama
      // (so o construtor de String e usado), por isso a falha nunca apareceu em runtime.
      assertThrows (NullPointerException.class, () -> new IndFileCommand (
          Transfer.TransferType.DOWNLOAD, "SYS1.PROCLIB", new byte[0]));

      assertThrows (NullPointerException.class, () -> new IndFileCommand (
          Transfer.TransferType.UPLOAD, "SYS1.PROCLIB", (java.io.File) null));
    }
  }
}
